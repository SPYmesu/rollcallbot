package su.spyme.rollcallbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.Main.*;
import static su.spyme.rollcallbot.utils.ConfigUtils.setAndSave;
import static su.spyme.rollcallbot.utils.MyUtils.*;
import static su.spyme.rollcallbot.utils.StringUtils.*;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);
    public static Map<Long, String> reading = new HashMap<>();
    public static Map<Chat, Long> cooldowns = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final String HELP = """
            Помощь по командам:

            .перекличка (.п) `<свой текст сообщения>` - начать перекличку `<если указано, то с этим текстом>`
            *Также эта команда автоматически выполняет следующую*

            .позвать (.все) - упоминает всех добавленных студентов

            .игнор - упоминает только тех, кто ещё не участвовал в перекличке
            *Сообщение само удалится через 120 секунд*

            .перекличкавсё (.пв) - заканчивает перекличку, удаляет сообщение с опросом

            .студент (.с) `<Дата рождения 11.11.2011>` `<Фамилия Имя>` - добавляет студента с указанными данными (ответом на его сообщение)

            .настройки - открывает меню настроек (только в личном чате с ботом)

            Сообщить об ошибке: https://github.com/SPYmesu/rollcallbot/issues
            Исходный код: https://github.com/SPYmesu/rollcallbot
            Поддержать разработчика: https://boosty.to/SPY\\_me/about
            """;

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            String[] callDataArray = callData.split(" ");
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            User user = update.getCallbackQuery().getFrom();
            switch (callDataArray[0]) {
                case "rollcall" -> {
                    Rollcall rollcall = getRollcallById(chatId, Integer.parseInt(callDataArray[1]));
                    switch (callDataArray[2]) {
                        case "here", "notherereason", "nothere" -> {
                            if (rollcall == null) {
                                telegramAPI.answerInline(update, "Эта перекличка уже неактивна");
                                return;
                            }
                            RollcallEntry entry = rollcall.entries.stream().filter(it -> it.student.userId == user.getId()).findAny().orElse(null);
                            if (entry == null) {
                                telegramAPI.answerInline(update, "Ты не зарегистрирован, обратись к старосте");
                                return;
                            }
                            if (entry.answer != RollcallAnswer.IGNORE) {
                                telegramAPI.answerInline(update, "Ты уже сделал свой выбор...");
                                entry.addTimes();
                                setAndSave(getChat(chatId).config, "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".times", entry.times);
                                return;
                            }
                            RollcallAnswer answer = RollcallAnswer.getByName(callDataArray[2]);
                            entry.answer = answer;
                            setAndSave(getChat(chatId).config, "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".answer", answer.name());
                            telegramAPI.answerInline(update, "Спасибо за участие, уже передали ответ старосте.");
                        }
                        default -> {
                            telegramAPI.answerInline(update, "Эта перекличка уже неактивна");
                            logger.warn("Unhandled callback query {}", callData);
                        }
                    }
                    telegramAPI.editMessageReplyMarkup(chatId, messageId, getRollcallInline(getChat(chatId), rollcall));
                    telegramAPI.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, getChat(chatId).students));
                }
                case "settings" -> {
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
                                sendError(chatId, 0, "Не удалось сохранить настройки чата");
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
                                sendError(chatId, 0, "Не удалось сохранить настройки чата");
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
                                telegramAPI.sendMessageInline(
                                        chatId,
                                        getMessageSettingsInline(chat),
                                        """
                                                ⚙ Управление сообщением переклички
                                                
                                                ✏ Редактируйте, нажимая кнопки ниже.
                                                
                                                ℹ Текущее сообщение:
                                                %s
                                                """.formatted(chat.settings.message)
                                );
                            }
                        }
                        case "students" -> {
                            telegramAPI.deleteMessage(chatId, messageId);
                            int id = telegramAPI.sendMessageInline(chatId,
                                    InlineKeyboardMarkup.builder()
                                            .keyboardRow(
                                                    new InlineKeyboardRow(
                                                            getInlineButton("\uD83D\uDD19 Назад", "settings " + chat.chatId + " select")
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
            }
        } else if (update.hasMessage() && update.getMessage().hasText() && !update.getMessage().isUserMessage()) {
            String[] args = update.getMessage().getText().split(" ");
            long chatId = update.getMessage().getChatId();
            int threadId = (update.getMessage().getChat().getIsForum() != null && update.getMessage().getChat().getIsForum() && update.getMessage().getMessageThreadId() != null) ? update.getMessage().getMessageThreadId() : 0;
            long userId = update.getMessage().getFrom().getId();
            String command = args[0].toLowerCase().replaceFirst("^\\.", "/");
            if (!command.startsWith("/")) return;
            command = command.substring(1);
            if (command.contains("@")) {
                command = command.substring(0, command.indexOf('@'));
            }
            if (command.isEmpty()) return;
            Chat chat = getChat(chatId);
            if (chat == null) return;
            List<Student> students = chat.students;
            switch (command) {
                case "all", "позвать", "все" -> {
                    if (!telegramAPI.isAdmin(chatId, userId)) return;
                    if (students.isEmpty()) {
                        telegramAPI.sendMessage(chatId, threadId, "❌ В этом чате нет студентов, добавьте их командой `.студент`");
                        return;
                    }
                    telegramAPI.sendMessage(chatId, threadId, tag(students));
                }
                case "rollcall", "перекличка", "п" -> {
                    try {
                        if (!telegramAPI.isAdmin(chatId, userId)) return;
                        if (getRollcallByThread(chat, threadId) != null) {
                            telegramAPI.sendMessage(chatId, threadId, "В этом чате уже активна перекличка... \nСначала заверши её (`.пв`)");
                            return;
                        }
                        if (students.isEmpty()) {
                            telegramAPI.sendMessage(chatId, threadId, "❌ В этом чате нет студентов, добавьте их командой `.студент`");
                            return;
                        }
                        String text = chat.settings.message;
                        if (args.length > 1) {
                            text = getArguments(1, args);
                        }
                        List<RollcallEntry> entries = new ArrayList<>();
                        for (Student student : students) {
                            entries.add(new RollcallEntry(student, RollcallAnswer.IGNORE, 0));
                        }
                        Rollcall rollcall = new Rollcall(chatId, threadId, 0, 0, 0L, 0, text, System.currentTimeMillis(), entries);
                        rollcall.setResultChatId(userId);
                        Message resultMessage = telegramAPI.sendMessage(userId, 0, getRollcallResult(rollcall, students));
                        if (resultMessage == null) {
                            telegramAPI.sendMessage(chatId, threadId, "❌ Не удалось отправить сообщение с результатом переклички, проверьте, может ли бот вам писать в личные сообщения.");
                            return;
                        }
                        rollcall.setResultMessageId(resultMessage.getMessageId());
                        telegramAPI.deleteMessage(chatId, update.getMessage().getMessageId());
                        Message tagAllMessage = telegramAPI.sendMessage(chatId, threadId, tag(students));
                        if (tagAllMessage == null) {
                            sendError(chatId, threadId, "❌ Не удалось отправить сообщение с упоминанием студентов");
                            return;
                        }
                        rollcall.setTagAllMessageId(tagAllMessage.getMessageId());
                        Message rollcallMessage = telegramAPI.sendMessageInline(chatId, threadId, getRollcallInline(chat, rollcall), rollcall.text);
                        if (rollcallMessage == null) {
                            sendError(chatId, threadId, "❌ Не удалось отправить сообщение переклички");
                            return;
                        }
                        rollcall.setRollcallMessageId(rollcallMessage.getMessageId());
                        telegramAPI.editMessageReplyMarkup(chatId, rollcall.rollcallMessageId, getRollcallInline(chat, rollcall));
                        telegramAPI.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, students));
                        addRollcall(chat, rollcall);
                    } catch (Exception exception) {
                        sendError(chatId, threadId, "❌ При запуске переклички произошла ошибка:\n" + exception.getMessage());
                    }
                }
                case "rollcallstop", "перекличкавсё", "пв" -> {
                    if (!telegramAPI.isAdmin(chatId, userId)) return;
                    Rollcall rollcall = getRollcallByThread(chat, threadId);
                    if (rollcall == null) {
                        sendError(chatId, threadId, "❌ В этом чате нет активной переклички");
                        return;
                    }
                    telegramAPI.deleteMessage(chatId, update.getMessage().getMessageId());
                    finishRollcall(chat, rollcall);
                }
                case "student", "студент", "с" -> {
                    if (!telegramAPI.isAdmin(chatId, userId)) return;
                    if (update.getMessage().getReplyToMessage() != null) {
                        long targetId = update.getMessage().getReplyToMessage().getFrom().getId();
                        if (args.length < 3) {
                            telegramAPI.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
                            return;
                        }
                        String targetName = getArguments(2, args);
                        Instant instant = null;
                        try {
                            instant = parseDate(args[1]);
                        } catch (Exception ignored) {
                        }
                        if (targetName.split(" ").length < 2 || instant == null) {
                            telegramAPI.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
                            return;
                        }

                        try {
                            Student student = getStudent(students, targetId);
                            if (student == null) {
                                students.add(new Student(targetId, targetName, instant));
                            } else {
                                student.setName(targetName);
                                student.setBirthdate(instant);
                            }
                            saveChat(chat);
                            telegramAPI.sendMessage(chatId, threadId, (student == null ? "Студент добавлен: " : "Студент обновлён: ") + escapeMarkdown(targetName) + " (" + targetId + ").");
                        } catch (Exception exception) {
                            telegramAPI.sendMessage(chatId, threadId, "❌ При выполнении команды произошла ошибка: " + exception.getMessage());
                        }
                    } else {
                        sendError(chatId, threadId, "❌ Команду нужно отправить ответом на сообщение студента");
                    }
                }
                case "ignore", "игнор" -> {
                    if (!telegramAPI.isAdmin(chatId, userId)) return;
                    Rollcall rollcall = getRollcallByThread(chat, threadId);
                    if (rollcall != null) {
                        telegramAPI.deleteMessage(chatId, update.getMessage().getMessageId());
                        Message ignoreMessage = telegramAPI.sendMessage(chatId, threadId, tag(rollcall.getStudents(RollcallAnswer.IGNORE)) + "\n\n⚠ Не забудьте сделать выбор выше, иначе Вам проставят отсутствие...");
                        if (ignoreMessage != null) {
                            scheduler.schedule(() -> telegramAPI.deleteMessage(chatId, ignoreMessage.getMessageId()), 120, TimeUnit.SECONDS);
                        }
                    }
                }
                case "help", "помощь" -> {
                    if (!telegramAPI.isAdmin(chatId, userId)) return;
                    telegramAPI.sendMessage(chatId, threadId, HELP);
                }
                default -> logger.debug("Unhandled command: {}", command);
            }
        } else if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().isUserMessage()) {
            String[] args = update.getMessage().getText().split(" ");
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();
            if (reading.containsKey(userId)) {
                String toSet = update.getMessage().getText();
                String metadata = reading.remove(userId);
                String[] split = metadata.split("☭");
                Chat chat = getChat(Long.parseLong(split[1]));
                int menuId = Integer.parseInt(split[2]);
                int infoMessage = Integer.parseInt(split[3]);
                if (chat == null) return;
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
                                return;
                            } else reading.put(userId, metadata + "☭badint");
                            telegramAPI.editMessageText(chatId, infoMessage, """
                                    Отправьте время, через которое вы хотите автоматически завершать перекличку.
                                    Это число в минутах от 30 до 90 или -1, если вы хотите отключить эту функцию.
                                    
                                    ❗️ Проверьте введенное число, с ним что-то не так.
                                    """);
                            return;
                        } catch (IOException ignored1) {
                            sendError(chatId, 0, "Не удалось сохранить настройки чата");
                        }
                    }
                    case "text" -> {
                        try {
                            chat.settings.setMessage(toSet);
                            saveChat(chat);
                            telegramAPI.deleteMessage(chatId, infoMessage);
                            telegramAPI.editMessageText(
                                    chatId,
                                    menuId,
                                    """
                                            ⚙ Управление сообщением переклички
                                            
                                            ✏ Редактируйте, нажимая кнопки ниже.
                                            
                                            ℹ Текущее сообщение:
                                            %s
                                            """.formatted(chat.settings.message),
                                    getMessageSettingsInline(chat)
                            );
                        } catch (IOException ignored1) {
                            sendError(chatId, 0, "Не удалось сохранить настройки чата");
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
                            sendError(chatId, 0, "Не удалось сохранить настройки чата");
                        }
                    }
                    case "student" -> {
                        try {
                            int num = Integer.parseInt(toSet);
                            if (num < 1 || num > chat.students.size()) throw new NumberFormatException();
                            Student student = chat.students.get(num - 1);
                            telegramAPI.deleteMessage(chatId, infoMessage);
                            telegramAPI.sendMessageInline(chatId, getStudentInline(chat, student), getStudentMenu(chat, student));
                            return;
                        } catch (NumberFormatException ignored) {
                            if (split.length > 4) {
                                reading.put(userId, metadata);
                                return;
                            } else reading.put(userId, metadata + "☭badint");
                            telegramAPI.sendMessage(chatId, """
                                    ❗️ Проверьте введенное число, с ним что-то не так.
                                    """);
                            return;
                        }
                    }
                    case "position" -> {
                        try {
                            long studentId = Long.parseLong(split[4]);
                            int pos = Integer.parseInt(toSet);
                            if (pos < 1 || pos > chat.students.size()) throw new NumberFormatException();
                            List<Student> students = new ArrayList<>(chat.students);
                            Student student = students.stream().filter(it -> it.userId == studentId).findFirst().orElse(null);
                            if (student == null) return;
                            students.remove(student);
                            students.add(pos - 1, student);
                            chat.setStudents(students);
                            saveChat(chat);
                            telegramAPI.deleteMessage(chatId, infoMessage);
                            telegramAPI.editMessageText(chatId, menuId, getStudentMenu(chat, student), getStudentInline(chat, student));
                        } catch (NumberFormatException ignored) {
                            if (split.length > 5) {
                                reading.put(userId, metadata);
                                return;
                            } else reading.put(userId, metadata + "☭badint");
                            telegramAPI.editMessageText(chatId, infoMessage, """
                                    Отправьте желаемую позицию, на которую вы хотите переместить студента
                                    
                                    ❗️ Проверьте введенное число, с ним что-то не так.
                                    """);
                            return;
                        } catch (IOException ignored1) {
                            sendError(chatId, 0, "Не удалось сохранить настройки студента");
                        }
                    }
                    case "name" -> {
                        try {
                            if (toSet.split(" ").length != 2) {
                                reading.put(userId, metadata);
                                telegramAPI.sendMessage(chatId, "❌ Нужно указать только фамилию и имя студента");
                                return;
                            }
                            long studentId = Long.parseLong(split[4]);
                            Student student = null;
                            for (Student st : chat.students) {
                                if (st.userId == studentId) student = st;
                            }
                            if (student == null) return;
                            student.setName(toSet);
                            saveChat(chat);
                            telegramAPI.deleteMessage(chatId, infoMessage);
                            telegramAPI.editMessageText(chatId, menuId, getStudentMenu(chat, student), getStudentInline(chat, student));
                        } catch (IOException ignored1) {
                            sendError(chatId, 0, "Не удалось сохранить настройки студента");
                        }
                    }
                    case "birthdate" -> {
                        Instant instant;
                        try {
                            instant = parseDate(toSet);
                        } catch (Exception ignored) {
                            reading.put(userId, metadata);
                            telegramAPI.sendMessage(chatId, "❌ Нужно указать дату в формате дд.ММ.гггг (01.12.2012)");
                            return;
                        }
                        try {
                            long studentId = Long.parseLong(split[4]);
                            Student student = null;
                            for (Student st : chat.students) {
                                if (st.userId == studentId) student = st;
                            }
                            if (student == null) return;
                            student.setBirthdate(instant);
                            saveChat(chat);
                            telegramAPI.deleteMessage(chatId, infoMessage);
                            telegramAPI.editMessageText(chatId, menuId, getStudentMenu(chat, student), getStudentInline(chat, student));
                        } catch (IOException ignored1) {
                            sendError(chatId, 0, "Не удалось сохранить настройки студента");
                        }
                    }
                    default -> logger.warn("Unhandled reading: {}", split[0]);
                }
                telegramAPI.sendMessage(chatId, "Настройка сохранена: " + escapeMarkdown(toSet));
                return;
            }
            String command = args[0].toLowerCase().replaceFirst("^\\.", "/");
            if (!command.startsWith("/")) return;
            command = command.substring(1);
            if (command.contains("@")) {
                command = command.substring(0, command.indexOf('@'));
            }
            if (command.isEmpty()) return;
            switch (command) {
                case "settings", "настройки" -> {
                    if (!update.getMessage().isUserMessage()) return;
                    List<Chat> myChats = chats.stream().filter(it -> it.admins.contains(userId) || userId == OWNER_ID).toList();
                    if (myChats.isEmpty()) return;
                    InlineKeyboardMarkup.InlineKeyboardMarkupBuilder<?, ?> builder = InlineKeyboardMarkup.builder();
                    for (Chat myChat : myChats) {
                        builder.keyboardRow(new InlineKeyboardRow(getInlineButton("Чат " + myChat.name, "settings " + myChat.chatId + " select")));
                    }
                    telegramAPI.sendMessageInline(chatId, builder.build(), "✏ Выбери чат для настройки");
                }
                case "help", "помощь" -> {
                    if (!update.getMessage().isUserMessage()) return;
                    telegramAPI.sendMessage(chatId, HELP);
                }
                default -> logger.debug("Unhandled command: {}", command);
            }
        }
    }

    private void sendError(long chatId, int threadId, String error) {
        telegramAPI.sendMessageInline(
                chatId,
                threadId,
                InlineKeyboardMarkup.builder()
                        .keyboardRow(
                                new InlineKeyboardRow(InlineKeyboardButton
                                        .builder()
                                        .text("Сообщить разработчику - @SPY_mesu")
                                        .url("https://t.me/SPY_mesu")
                                        .build()
                                )
                        )
                        .build(),
                error + "\nУверен, что сделал всё правильно? Если да:\n"
        );
    }
}
