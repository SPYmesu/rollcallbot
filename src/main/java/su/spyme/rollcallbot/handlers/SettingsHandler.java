package su.spyme.rollcallbot.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.objects.Student;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static su.spyme.rollcallbot.Main.*;
import static su.spyme.rollcallbot.utils.MyUtils.*;
import static su.spyme.rollcallbot.utils.StringUtils.*;

public class SettingsHandler {
    private static final Logger logger = LoggerFactory.getLogger(SettingsHandler.class);
    private final Map<Long, String> reading = new HashMap<>();
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
                int id = telegramAPI.sendMessage(chatId, """
                        Отправьте время, через которое вы хотите автоматически завершать перекличку.
                        Это число в минутах от 30 до 90 или -1, если вы хотите отключить эту функцию.
                        """).getMessageId();
                reading.put(user.getId(), "timer☭" + chat.chatId + "☭" + messageId + "☭" + id);
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
                    if (System.currentTimeMillis() - cd >= 60 * 60 * 1000) {
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
                    reading.put(user.getId(), setting + "☭" + chat.chatId + "☭" + messageId + "☭" + id);
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
                reading.put(user.getId(), "student☭" + chat.chatId + "☭" + messageId + "☭" + id);
            }
            case "student" -> {
                long userId = Long.parseLong(callDataArray[3]);
                String setting = callDataArray[4];
                int id = telegramAPI.sendMessage(chatId, """
                        Введите желаемое значение для выбранного элемента пользователя.
                        """).getMessageId();
                reading.put(user.getId(), setting + "☭" + chat.chatId + "☭" + messageId + "☭" + id + "☭" + userId);
            }
        }
        telegramAPI.answerInline(update, "⏳");
    }

    public boolean handleInput(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        if (!reading.containsKey(userId)) return false;
        String toSet = message.getText();
        String metadata = reading.remove(userId);
        String[] split = metadata.split("☭");
        Chat chat = getChat(Long.parseLong(split[1]));
        int menuId = Integer.parseInt(split[2]);
        int infoMessage = Integer.parseInt(split[3]);
        if (chat == null) return true;
        switch (split[0]) {
            case "timer" -> {
                try {
                    int timer = Integer.parseInt(toSet);
                    if (timer != -1 && (timer < 30 || timer > 90)) throw new NumberFormatException();
                    chat.settings.setTimer(timer);
                    saveChat(chat);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.editMessageReplyMarkup(chatId, menuId, getSettingsInline(chat));
                } catch (NumberFormatException ignored) {
                    if (split.length > 4) {
                        reading.put(userId, metadata);
                        return true;
                    } else reading.put(userId, metadata + "☭badint");
                    telegramAPI.editMessageText(chatId, infoMessage, """
                            Отправьте время, через которое вы хотите автоматически завершать перекличку.
                            Это число в минутах от 30 до 90 или -1, если вы хотите отключить эту функцию.

                            ❗️ Проверьте введенное число, с ним что-то не так.
                            """);
                    return true;
                } catch (IOException ignored1) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки чата");
                }
            }
            case "text" -> {
                try {
                    chat.settings.setMessage(toSet);
                    saveChat(chat);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.editMessageText(chatId, menuId, getMessageMenu(chat), getMessageSettingsInline(chat));
                } catch (IOException ignored1) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки чата");
                }
            }
            case "button0", "button1", "button2" -> {
                try {
                    int buttonNum = Integer.parseInt(split[0].substring("button".length()));
                    List<String> buttons = new ArrayList<>(chat.settings.buttonNames);
                    buttons.set(buttonNum, toSet);
                    chat.settings.setButtonNames(buttons);
                    saveChat(chat);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.editMessageReplyMarkup(chatId, menuId, getMessageSettingsInline(chat));
                } catch (IOException ignored1) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки чата");
                }
            }
            case "student" -> {
                try {
                    int num = Integer.parseInt(toSet);
                    if (num < 1 || num > chat.students.size()) throw new NumberFormatException();
                    Student student = chat.students.get(num - 1);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.sendMessageInline(chatId, getStudentInline(chat, student), getStudentMenu(chat, student));
                    return true;
                } catch (NumberFormatException ignored) {
                    if (split.length > 4) {
                        reading.put(userId, metadata);
                        return true;
                    } else reading.put(userId, metadata + "☭badint");
                    telegramAPI.sendMessage(chatId, """
                            ❗️ Проверьте введенное число, с ним что-то не так.
                            """);
                    return true;
                }
            }
            case "position" -> {
                try {
                    long studentId = Long.parseLong(split[4]);
                    int pos = Integer.parseInt(toSet);
                    if (pos < 1 || pos > chat.students.size()) throw new NumberFormatException();
                    List<Student> students = new ArrayList<>(chat.students);
                    Student student = students.stream().filter(it -> it.userId == studentId).findFirst().orElse(null);
                    if (student == null) return true;
                    students.remove(student);
                    students.add(pos - 1, student);
                    chat.setStudents(students);
                    saveChat(chat);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.editMessageText(chatId, menuId, getStudentMenu(chat, student), getStudentInline(chat, student));
                } catch (NumberFormatException ignored) {
                    if (split.length > 5) {
                        reading.put(userId, metadata);
                        return true;
                    } else reading.put(userId, metadata + "☭badint");
                    telegramAPI.editMessageText(chatId, infoMessage, """
                            Отправьте желаемую позицию, на которую вы хотите переместить студента

                            ❗️ Проверьте введенное число, с ним что-то не так.
                            """);
                    return true;
                } catch (IOException ignored1) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки студента");
                }
            }
            case "name" -> {
                try {
                    if (toSet.split(" ").length != 2) {
                        reading.put(userId, metadata);
                        telegramAPI.sendMessage(chatId, "❌ Нужно указать только фамилию и имя студента");
                        return true;
                    }
                    long studentId = Long.parseLong(split[4]);
                    Student student = getStudent(chat.students, studentId);
                    if (student == null) return true;
                    student.setName(toSet);
                    saveChat(chat);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.editMessageText(chatId, menuId, getStudentMenu(chat, student), getStudentInline(chat, student));
                } catch (IOException ignored1) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки студента");
                }
            }
            case "birthdate" -> {
                Instant instant;
                try {
                    instant = parseDate(toSet);
                } catch (Exception ignored) {
                    reading.put(userId, metadata);
                    telegramAPI.sendMessage(chatId, "❌ Нужно указать дату в формате дд.ММ.гггг (01.12.2012)");
                    return true;
                }
                try {
                    long studentId = Long.parseLong(split[4]);
                    Student student = getStudent(chat.students, studentId);
                    if (student == null) return true;
                    student.setBirthdate(instant);
                    saveChat(chat);
                    telegramAPI.deleteMessage(chatId, infoMessage);
                    telegramAPI.editMessageText(chatId, menuId, getStudentMenu(chat, student), getStudentInline(chat, student));
                } catch (IOException ignored1) {
                    telegramAPI.sendError(chatId, 0, "Не удалось сохранить настройки студента");
                }
            }
            default -> logger.warn("Unhandled reading: {}", split[0]);
        }
        telegramAPI.sendMessage(chatId, "Настройка сохранена: " + escapeMarkdown(toSet));
        return true;
    }

    private InlineKeyboardMarkup getSettingsInline(Chat chat) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "⏳ Автозавершение: " + (chat.settings.timer == -1 ? "выкл." : chat.settings.timer + " мин."),
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
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "ℹ Изменить сообщение",
                        "settings " + chat.chatId + " message text"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "✏: " + chat.settings.buttonNames.get(0),
                        "settings " + chat.chatId + " message button0"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "✏: " + chat.settings.buttonNames.get(1),
                        "settings " + chat.chatId + " message button1"
                )))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(
                        "✏: " + chat.settings.buttonNames.get(2),
                        "settings " + chat.chatId + " message button2"
                )))
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
