package su.spyme.rollcallbot.handlers;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import su.spyme.rollcallbot.handlers.PendingInput.Setting;
import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.objects.ChatSettings;
import su.spyme.rollcallbot.objects.RollcallAnswer;
import su.spyme.rollcallbot.objects.Student;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.Main.*;
import static su.spyme.rollcallbot.utils.MyUtils.*;
import static su.spyme.rollcallbot.utils.StringUtils.*;

public class SettingsHandler {
    private static final long UPDATE_CHAT_COOLDOWN = TimeUnit.HOURS.toMillis(1);
    private static final String TIMER_PROMPT = """
            Отправьте время, через которое вы хотите автоматически завершать перекличку.
            Это число в минутах от %d до %d или %d, если вы хотите отключить эту функцию.
            """.formatted(ChatSettings.TIMER_MIN, ChatSettings.TIMER_MAX, ChatSettings.TIMER_OFF);
    private final Map<Long, PendingInput> reading = new HashMap<>();
    private final Map<Chat, Long> cooldowns = new HashMap<>();

    public void sendChatList(Message message) {
        long userId = message.getFrom().getId();
        List<Chat> myChats = chats.stream().filter(it -> it.admins.contains(userId) || userId == OWNER_ID).toList();
        if (myChats.isEmpty()) return;
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder<?, ?> builder = InlineKeyboardMarkup.builder();
        for (Chat myChat : myChats) {
            builder.keyboardRow(new InlineKeyboardRow(getInlineButton("Чат " + myChat.name, "settings " + myChat.chatId + " select")));
        }
        telegramAPI.sendMessageInline(message.getChatId(), builder.build(), "✏ Выбери чат для настройки");
    }

    public void handleCallback(Update update, String[] callDataArray) {
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        User user = update.getCallbackQuery().getFrom();
        Chat chat = getChat(Long.parseLong(callDataArray[1]));
        if (chat == null || (!chat.admins.contains(user.getId()) && user.getId() != OWNER_ID)) {
            telegramAPI.answerInline(update, "У вас нет прав на управление этим чатом");
            return;
        }
        switch (callDataArray[2]) {
            case "select" -> {
                reading.remove(user.getId());
                telegramAPI.deleteMessage(chatId, messageId);
                telegramAPI.sendMessageInline(
                        chatId,
                        getSettingsInline(chat),
                        """
                                ⚙ Управление чатом %s

                                ℹ В этом меню Вы можете настроить Ваш чат.
                                    Небольшие подсказки:

                                ✏ Автозавершение: установите время в минутах,
                                    через сколько перекличка будет автоматически завершена.
                                    Укажите -1, чтобы отключить эту функцию.

                                ✏ Дни рождения: настройте, будет ли бот поздравлять
                                    ваших студентов с днем рождения.

                                ✏ Обновить информацию: Если вы изменяли
                                    администраторов или название, обновите чат.
                                """.formatted(escapeMarkdown(chat.name))
                );
            }
            case "timer" -> {
                int id = telegramAPI.sendMessage(chatId, TIMER_PROMPT).getMessageId();
                reading.put(user.getId(), new PendingInput(Setting.TIMER, chat, messageId, id));
            }
            case "birthdays" -> {
                chat.settings.setBirthdays(!chat.settings.birthdays);
                telegramAPI.editMessageReplyMarkup(chatId, messageId, getSettingsInline(chat));
                try {
                    saveChat(chat);
                } catch (IOException ignored) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки чата");
                }
            }
            case "updatechat" -> {
                if (cooldowns.containsKey(chat)) {
                    long cd = cooldowns.get(chat);
                    if (System.currentTimeMillis() - cd >= UPDATE_CHAT_COOLDOWN) {
                        cooldowns.remove(chat);
                    } else {
                        telegramAPI.answerInline(update, "❌ Обновить информацию можно не чаще, чем раз в час");
                        return;
                    }
                }
                try {
                    updateChatName(chat);
                    updateChatAdmins(chat);
                    saveChat(chat);
                    cooldowns.put(chat, System.currentTimeMillis());
                    telegramAPI.answerInline(update, "✅");
                    return;
                } catch (IOException ignored) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки чата");
                }
            }
            case "message" -> {
                if (callDataArray.length > 3) {
                    String setting = callDataArray[3];
                    int id = telegramAPI.sendMessage(chatId, """
                            Введите желаемый текст для выбранного элемента сообщения.
                            """).getMessageId();
                    if (setting.equals("text")) {
                        reading.put(user.getId(), new PendingInput(Setting.MESSAGE, chat, messageId, id));
                    } else {
                        RollcallAnswer answer = RollcallAnswer.valueOf(setting.substring("button".length()));
                        reading.put(user.getId(), new PendingInput(Setting.BUTTON, chat, messageId, id, 0, answer));
                    }
                } else {
                    telegramAPI.deleteMessage(chatId, messageId);
                    telegramAPI.sendMessageInline(chatId, getMessageSettingsInline(chat), getMessageMenu(chat));
                }
            }
            case "students" -> {
                telegramAPI.deleteMessage(chatId, messageId);
                int id = telegramAPI.sendMessageInline(chatId,
                        InlineKeyboardMarkup.builder()
                                .keyboardRow(
                                        new InlineKeyboardRow(
                                                getInlineButton("🔙 Назад", "settings " + chat.chatId + " select")
                                        )
                                )
                                .build(),
                        getStudentsMenu(chat)
                ).getMessageId();
                reading.put(user.getId(), new PendingInput(Setting.STUDENT, chat, messageId, id));
            }
            case "student" -> {
                long studentId = Long.parseLong(callDataArray[3]);
                Setting setting = Setting.valueOf(callDataArray[4].toUpperCase());
                int id = telegramAPI.sendMessage(chatId, """
                        Введите желаемое значение для выбранного элемента пользователя.
                        """).getMessageId();
                reading.put(user.getId(), new PendingInput(setting, chat, messageId, id, studentId, null));
            }
        }
        telegramAPI.answerInline(update);
    }

    public boolean handleInput(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        PendingInput input = reading.get(userId);
        if (input == null) return false;
        String text = message.getText();
        if (text.startsWith("/") || text.startsWith(".")) {
            reading.remove(userId);
            return false;
        }
        String error;
        try {
            error = applyInput(chatId, input, text);
        } catch (IOException exception) {
            reading.remove(userId);
            telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки чата");
            return true;
        }
        if (error != null) {
            telegramAPI.sendMessage(chatId, error);
            return true;
        }
        reading.remove(userId);
        telegramAPI.deleteMessage(chatId, input.infoMessageId());
        if (input.setting() != Setting.STUDENT) {
            telegramAPI.sendMessage(chatId, "Настройка сохранена: " + escapeMarkdown(text));
        }
        return true;
    }

    private String applyInput(long chatId, PendingInput input, String text) throws IOException {
        Chat chat = input.chat();
        switch (input.setting()) {
            case TIMER -> {
                Integer timer = parseInt(text);
                if (timer == null || (timer != ChatSettings.TIMER_OFF && (timer < ChatSettings.TIMER_MIN || timer > ChatSettings.TIMER_MAX))) {
                    return "❗️ Нужно число от %d до %d или %d, чтобы отключить автозавершение.".formatted(ChatSettings.TIMER_MIN, ChatSettings.TIMER_MAX, ChatSettings.TIMER_OFF);
                }
                chat.settings.setTimer(timer);
                saveChat(chat);
                telegramAPI.editMessageReplyMarkup(chatId, input.menuId(), getSettingsInline(chat));
            }
            case MESSAGE -> {
                chat.settings.setMessage(text);
                saveChat(chat);
                telegramAPI.editMessageText(chatId, input.menuId(), getMessageMenu(chat), getMessageSettingsInline(chat));
            }
            case BUTTON -> {
                chat.settings.setButton(input.answer(), text);
                saveChat(chat);
                telegramAPI.editMessageReplyMarkup(chatId, input.menuId(), getMessageSettingsInline(chat));
            }
            case STUDENT -> {
                Integer num = parseInt(text);
                if (num == null || num < 1 || num > chat.students.size()) {
                    return "❗️ Нужно указать номер студента из списка.";
                }
                Student student = chat.students.get(num - 1);
                telegramAPI.sendMessageInline(chatId, getStudentInline(chat, student), getStudentMenu(chat, student));
            }
            case POSITION -> {
                Integer pos = parseInt(text);
                if (pos == null || pos < 1 || pos > chat.students.size()) {
                    return "❗️ Нужно указать позицию от 1 до " + chat.students.size() + ".";
                }
                Student student = getStudent(chat.students, input.studentId());
                if (student == null) return "❌ Студент не найден";
                List<Student> students = new ArrayList<>(chat.students);
                students.remove(student);
                students.add(pos - 1, student);
                chat.setStudents(students);
                saveChat(chat);
                telegramAPI.editMessageText(chatId, input.menuId(), getStudentMenu(chat, student), getStudentInline(chat, student));
            }
            case NAME -> {
                if (!Student.isValidName(text)) return "❌ Нужно указать фамилию и имя студента";
                Student student = getStudent(chat.students, input.studentId());
                if (student == null) return "❌ Студент не найден";
                student.setName(text);
                saveChat(chat);
                telegramAPI.editMessageText(chatId, input.menuId(), getStudentMenu(chat, student), getStudentInline(chat, student));
            }
            case BIRTHDATE -> {
                Instant birthdate;
                try {
                    birthdate = parseDate(text);
                } catch (Exception exception) {
                    return "❌ Нужно указать дату в формате дд.ММ.гггг (01.12.2012)";
                }
                Student student = getStudent(chat.students, input.studentId());
                if (student == null) return "❌ Студент не найден";
                student.setBirthdate(birthdate);
                saveChat(chat);
                telegramAPI.editMessageText(chatId, input.menuId(), getStudentMenu(chat, student), getStudentInline(chat, student));
            }
        }
        return null;
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private InlineKeyboardMarkup getSettingsInline(Chat chat) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "⏳ Автозавершение: " + (chat.settings.timer == ChatSettings.TIMER_OFF ? "выкл." : chat.settings.timer + " мин."),
                        "settings " + chat.chatId + " timer"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "🎉 Дни рождения: " + (chat.settings.birthdays ? "вкл." : "выкл."),
                        "settings " + chat.chatId + " birthdays"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "💬 Управление сообщением переклички",
                        "settings " + chat.chatId + " message"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "👥 Управление студентами",
                        "settings " + chat.chatId + " students"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "🔄 Обновить информацию о чате",
                        "settings " + chat.chatId + " updatechat"
                )))
                .build();
    }

    private String getMessageMenu(Chat chat) {
        return """
                ⚙ Управление сообщением переклички

                ✏ Редактируйте, нажимая кнопки ниже.

                ℹ Текущее сообщение:
                %s
                """.formatted(chat.settings.message);
    }

    private InlineKeyboardMarkup getMessageSettingsInline(Chat chat) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder<?, ?> builder = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "ℹ Изменить сообщение",
                        "settings " + chat.chatId + " message text"
                )));
        for (RollcallAnswer answer : RollcallAnswer.BUTTONS) {
            builder.keyboardRow(new InlineKeyboardRow(getInlineButton(
                    "✏: " + chat.settings.getButton(answer),
                    "settings " + chat.chatId + " message button" + answer.name()
            )));
        }
        return builder
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "🔙 Назад",
                        "settings " + chat.chatId + " select"
                )))
                .build();
    }

    private String getStudentsMenu(Chat chat) {
        StringBuilder sb = new StringBuilder("👥 Студенты в этом чате:\n\n");
        int num = 1;
        for (Student student : chat.students) {
            sb.append(num++).append(". ").append(escapeMarkdown(student.name)).append("\n");
        }
        sb.append("\nℹ Отправь номер студента, которого нужно изменить");
        return sb.toString();
    }

    private String getStudentMenu(Chat chat, Student student) {
        return """
                👤 Управление студентом

                Позиция: %s
                Имя: %s
                Дата рождения: %s
                """.formatted(
                chat.students.indexOf(student) + 1,
                escapeMarkdown(student.name),
                hasBirthdate(student) ? instantToString(student.birthdate) : "не указана"
        );
    }

    private InlineKeyboardMarkup getStudentInline(Chat chat, Student student) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "ℹ Изменить позицию в списке",
                        "settings " + chat.chatId + " student " + student.userId + " position"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "ℹ Изменить фамилию и имя",
                        "settings " + chat.chatId + " student " + student.userId + " name"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "ℹ Изменить дату рождения",
                        "settings " + chat.chatId + " student " + student.userId + " birthdate"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "🔙 Назад",
                        "settings " + chat.chatId + " students"
                )))
                .build();
    }
}
