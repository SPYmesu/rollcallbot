package su.spyme.rollcallbot;

import org.simpleyaml.configuration.file.YamlFile;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import su.spyme.rollcallbot.api.TelegramBot;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Bot {

    public TelegramBot telegramBot = new TelegramBot();
    public YamlFile yamlFile;
    public List<Chat> chats;

    public void start(TelegramBot telegramBot) {
        try {
            this.telegramBot = telegramBot;

            yamlFile = loadConfig("config");

            List<String> chatsList = yamlFile.getStringList("chats");
            chats = new ArrayList<>();
            for (String chatId : chatsList) {
                YamlFile chatConfig = loadConfig(chatId);
                List<Student> chatStudents = new ArrayList<>();
                boolean updated = false;
                for (String key : getKeys(chatConfig, "students")) {
                    Student student;
                    if (chatConfig.get("students." + key + ".name") != null) {
                        student = new Student(
                                Long.parseLong(key),
                                chatConfig.getString("students." + key + ".name"),
                                new SimpleDateFormat("dd.MM.yyyy").parse(chatConfig.getString("students." + key + ".birthdate", "01.01.1970")).toInstant()
                        );
                    } else {
                        student = new Student(Long.parseLong(key), chatConfig.getString("students." + key), Instant.EPOCH);
                        chatConfig.set("students." + key + ".name", student.name);
                        chatConfig.set("students." + key + ".birthdate", instantToString(Instant.EPOCH));
                        updated = true;
                    }
                    chatStudents.add(student);
                }
                if (updated) chatConfig.save();
                List<Rollcall> chatRollcalls = new ArrayList<>();
                for (String key : getKeys(chatConfig, "rollcalls")) {
                    List<RollcallEntry> entries = new ArrayList<>();
                    for (String entryKey : getKeys(chatConfig, "rollcalls." + key + ".entries")) {
                        entries.add(new RollcallEntry(
                                getStudent(chatStudents, Long.parseLong(entryKey)),
                                RollcallAnswer.valueOf(chatConfig.getString("rollcalls." + key + ".entries." + entryKey + ".answer")),
                                chatConfig.getInt("rollcalls." + key + ".entries." + entryKey + ".times")
                        ));
                    }
                    chatRollcalls.add(new Rollcall(
                            Long.parseLong(chatId),
                            chatConfig.getInt("rollcalls." + key + ".threadId"),
                            Integer.parseInt(key),
                            chatConfig.getInt("rollcalls." + key + ".tagAllMessageId"),
                            chatConfig.getLong("rollcalls." + key + ".resultChatId"),
                            chatConfig.getInt("rollcalls." + key + ".resultMessageId"),
                            chatConfig.getString("rollcalls." + key + ".text"),
                            entries
                    ));
                }
                ChatSettings settings = new ChatSettings(
                        chatConfig.getInt("settings.timer"),
                        chatConfig.getString("settings.message"),
                        chatConfig.getStringList("settings.buttonNames"),
                        chatConfig.getBoolean("settings.birthdays")
                );
                chats.add(new Chat(Long.parseLong(chatId), chatConfig, settings, chatStudents, chatRollcalls));
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRun = now.withHour(7).withMinute(0).withSecond(0).withNano(0);
            if (now.isAfter(nextRun)) {
                nextRun = nextRun.plusDays(1);
            }
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this::checkBirthdays,
                    Duration.between(now, nextRun).toMillis(),
                    24 * 60 * 60 * 1000,
                    TimeUnit.MILLISECONDS);

            if (new Scanner(System.in).next().equals("stop")) System.exit(-1);
        } catch (Exception exception) {
            System.out.println("Error while loading...");
            throw new RuntimeException(exception);
        }
    }

    public void onUpdateReceived(Update update) throws TelegramApiException, IOException {
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            String[] callDataArray = callData.split(" ");
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            User user = update.getCallbackQuery().getFrom();
            Rollcall rollcall = getRollcallById(chatId, Integer.parseInt(callDataArray[0]));
            switch (callDataArray[1]) {
                case "here", "notherereason", "nothere" -> {
                    if (rollcall == null) {
                        answerInline(update, "Эта перекличка уже неактивна");
                        return;
                    }
                    RollcallEntry entry = rollcall.entries.stream().filter(it -> it.student.userId == user.getId()).findAny().orElse(null);
                    if (entry == null) {
                        answerInline(update, "Ты не зарегистрирован, обратись к старосте");
                        return;
                    }
                    if (entry.answer != RollcallAnswer.IGNORE) {
                        answerInline(update, "Ты уже сделал свой выбор...");
                        entry.addTimes();
                        setAndSave(getChat(chatId).config, "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".times", entry.times);
                        return;
                    }
                    RollcallAnswer answer = RollcallAnswer.getByName(callDataArray[1]);
                    rollcall.entries.remove(entry);
                    entry.answer = answer;
                    rollcall.entries.add(entry);
                    setAndSave(getChat(chatId).config, "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".answer", answer.name());
                    answerInline(update, "Спасибо за участие, уже передали ответ старосте.");
                }
                default -> {
                    answerInline(update, "Эта перекличка уже неактивна");
                    throw new RuntimeException("Unhandled callback query " + callData);
                }
            }
            telegramBot.editMessageReplyMarkup(chatId, messageId, getWhoHere(rollcall));
            telegramBot.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, getChat(chatId).students));
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String body = update.getMessage().getText();
            String[] args = body.split(" ");
            long chatId = update.getMessage().getChatId();
            int threadId = (update.getMessage().getChat().getIsForum() != null && update.getMessage().getChat().getIsForum() && update.getMessage().getMessageThreadId() != null) ? update.getMessage().getMessageThreadId() : 0;
            long userId = update.getMessage().getFrom().getId();
            Chat chat = getChat(chatId);
            List<Student> students = chat.students;
            switch (args[0]) {
                case "/all", ".позвать", ".все" -> {
                    if (!isAdmin(update)) return;
                    telegramBot.sendMessage(chatId, threadId, tag(students));
                }
                case "/rollcall", ".перекличка", ".п" -> {
                    try {
                        if (!isAdmin(update)) return;
                        if (getRollcallByThread(chat, threadId) != null) {
                            telegramBot.sendMessage(chatId, threadId, "В этом чате уже активна перекличка... \nСначала заверши её (`.пв`)");
                            return;
                        }
                        String text = "Перекличка на наличие на паре!!!";
                        if (args.length > 1) {
                            text = getArguments(1, args);
                        }
                        List<RollcallEntry> entries = new ArrayList<>();
                        for (Student student : students) {
                            entries.add(new RollcallEntry(student, RollcallAnswer.IGNORE, 0));
                        }
                        Rollcall rollcall = new Rollcall(chatId, threadId, 0, 0, 0L, 0, text, entries);
                        telegramBot.deleteMessage(chatId, update.getMessage().getMessageId());
                        rollcall.setTagAllMessageId(telegramBot.sendMessage(chatId, threadId, tag(students)).getMessageId());
                        rollcall.setRollcallMessageId(telegramBot.sendMessageInline(chatId, threadId, getWhoHere(rollcall), rollcall.text).getMessageId());
                        telegramBot.editMessageReplyMarkup(chatId, rollcall.rollcallMessageId, getWhoHere(rollcall));
                        rollcall.setResultChatId(userId);
                        rollcall.setResultMessageId(telegramBot.sendMessage(userId, threadId, getRollcallResult(rollcall, students)).getMessageId());
                        addRollcall(chat, rollcall);
                    } catch (Exception exception) {
                        telegramBot.sendMessage(chatId, threadId, "Настя, ну опять ты что-то сделала не так:\n" + exception.getMessage());
                    }
                }
                case "/rollcallstop", ".перекличкавсё", ".пв" -> {
                    if (!isAdmin(update)) return;
                    Rollcall rollcall = getRollcallByThread(chat, threadId);
                    if (rollcall == null) {
                        sendError(chatId, threadId, "recall == null;");
                        return;
                    }
                    telegramBot.deleteMessage(chatId, rollcall.rollcallMessageId);
                    telegramBot.deleteMessage(chatId, rollcall.tagAllMessageId);
                    telegramBot.deleteMessage(chatId, update.getMessage().getMessageId());
                    removeRollcall(chat, rollcall);
                    StringBuilder text = new StringBuilder("Перекличка `#" + rollcall.rollcallMessageId + "` завершена");

                    RollcallEntry best = rollcall.entries.getFirst();
                    for (RollcallEntry entry : rollcall.entries) {
                        if (entry.times > best.times) best = entry;
                    }
                    if (best.times > 5)
                        text.append("\n\nИнтересный факт: ").append(best.student.name).append(" кликнул на кнопку ").append(best.times).append(" раз!");
                    telegramBot.sendMessage(chatId, threadId, text.toString());
                }
                case "/student", ".студент", ".с" -> {
                    if (!isAdmin(update)) return;
                    if (update.getMessage().getReplyToMessage() != null) {
                        long targetId = update.getMessage().getReplyToMessage().getFrom().getId();
                        String targetName = getArguments(2, args);
                        Instant instant = null;
                        try {
                            instant = new SimpleDateFormat("dd.MM.yyyy").parse(args[1]).toInstant();
                        } catch (Exception ignored) {
                            telegramBot.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
                        }
                        if (targetName.split(" ").length < 2 || instant == null) {
                            telegramBot.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
                            return;
                        }

                        Student student = new Student(targetId, targetName, instant);
                        chat.config.set("students." + targetId + ".name", student.name);
                        chat.config.set("students." + targetId + ".birthdate", instantToString(student.birthdate));
                        chat.config.save();
                        students.add(student);
                        telegramBot.sendMessage(chatId, threadId, "Студент добавлен: " + targetName + " (" + targetId + ").");
                    } else {
                        sendError(chatId, threadId, "Message.getForwardFrom() == null;");
                    }
                }
                case "/ignore", ".игнор" -> {
                    if (!isAdmin(update)) return;
                    Rollcall rollcall = getRollcallByThread(chat, threadId);
                    if (rollcall != null) {
                        telegramBot.deleteMessage(chatId, update.getMessage().getMessageId());
                        int ignoreMessageId = telegramBot.sendMessage(chatId, threadId, tag(rollcall.getStudents(RollcallAnswer.IGNORE)) + "\n\nНе забудьте сделать выбор выше, иначе Вам проставят отсутствие...").getMessageId();
                        Executors.newSingleThreadScheduledExecutor().schedule(() -> telegramBot.deleteMessage(chatId, ignoreMessageId), 120, TimeUnit.SECONDS);
                    }
                }
                case "/help", ".помощь" -> {
                    if (!update.getMessage().isUserMessage() && !isAdmin(update)) return;
                    telegramBot.sendMessage(chatId, threadId, """
                            Помощь по командам:
                            
                            .перекличка (.п) `<свой текст сообщения>` - начать перекличку `<если указано, то с этим текстом>`
                            *Так же эта команда автоматически выполняет следующую*
                            
                            .позвать (.все) - упоминает всех добавленных студентов
                            
                            .игнор - упоминает только тех, кто ещё не участвовал в перекличке
                            *Сообщение само удалится через 120 секунд*
                            
                            .перекличкавсё (.пв) - заканчивает перекличку, удаляет сообщение с опросом
                            
                            .студент (.с) `<Дата рожения 11.11.2011>` `<Фамилия Имя>` - добавляет студента с указанными данными
                            
                            Сообщить об ошибке: https://github.com/SPY_mesu/rollcallbot/issues
                            Исходный код: https://github.com/SPY_mesu/rollcallbot
                            Поддержать разработчика: https://boosty.to/SPY_me/about
                            """);
                }
            }
        }
    }

    public YamlFile loadConfig(String name) throws IOException {
        YamlFile yamlFile = new YamlFile("storage/" + name + ".yml");
        if (!yamlFile.exists()) yamlFile.createNewFile();
        else yamlFile.load();
        return yamlFile;
    }

    public List<String> getKeys(YamlFile config, String section) {
        createSectionIfNotExist(config, section);
        return config.getConfigurationSection(section).getKeys(false).stream().toList();
    }

    public void createSectionIfNotExist(YamlFile config, String path) {
        if (!config.contains(path)) {
            setAndSave(config, path + ".temp", 10);
            setAndSave(config, path + ".temp", null);
        }
    }

    public void setAndSave(YamlFile config, String path, Object value) {
        config.set(path, value);
        try {
            config.save();
        } catch (IOException ignored) {
        }
    }

    private String getRollcallResult(Rollcall rollcall, List<Student> sortExample) {
        List<Student> here = rollcall.getStudents(RollcallAnswer.HERE);
        here.sort(Comparator.comparingInt(sortExample::indexOf));
        List<Student> notHere = rollcall.getStudents(RollcallAnswer.NOTHERE);
        notHere.sort(Comparator.comparingInt(sortExample::indexOf));
        List<Student> notHereReason = rollcall.getStudents(RollcallAnswer.NOTHEREREASON);
        notHereReason.sort(Comparator.comparingInt(sortExample::indexOf));
        List<Student> ignore = rollcall.getStudents(RollcallAnswer.IGNORE);
        StringBuilder builder = new StringBuilder("Результат переклички. `#" + rollcall.rollcallMessageId + "`");
        builder.append("\n\n");
        builder.append("На паре: (").append(here.size()).append(")");
        for (Student student : here) {
            builder.append("\n").append(student.name);
        }
        int notHereSize = notHere.size() + notHereReason.size();
        if (notHereSize > 0) {
            builder.append("\n");
            builder.append("\nНе на паре: (").append(notHereSize).append(")");
            for (Student student : notHereReason) {
                builder.append("\n").append(student.name).append(" (по ув. причине)");
            }
            for (Student student : notHere) {
                builder.append("\n").append(student.name);
            }
        }
        if (!ignore.isEmpty()) {
            builder.append("\n");
            builder.append("\nПроигнорировали: (").append(ignore.size()).append(")");
            for (Student student : ignore) {
                builder.append("\n").append(student.name);
            }
        }
        return builder.toString();
    }

    private void answerInline(Update update, String text) throws TelegramApiException {
        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                .callbackQueryId(update.getCallbackQuery().getId())
                .text(text)
                .showAlert(false)
                .build();
        telegramBot.telegramClient.execute(answerCallbackQuery);
    }

    private String tag(List<Student> students) {
        StringBuilder builder = new StringBuilder();
        for (Student student : students) {
            builder.append(formatShort(student)).append(", ");
        }
        return builder.substring(0, builder.length() - 2);
    }

    public String format(Student student) {
        return format(student.name, student.userId);
    }

    public String format(String name, long userId) {
        return String.format(
                "[%s](tg://user?id=%d)",
                name, userId
        );
    }

    public String formatShort(Student student) {
        return String.format(
                "[%s](tg://user?id=%d)",
                student.name.split(" ")[1].toCharArray()[0], student.userId
        );
    }

    public static String getArguments(int start, String[] args) {
        StringBuilder s = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            s.append(i == start ? args[i] : ' ' + args[i]);
        }
        return s.toString();
    }

    public Student getStudent(List<Student> students, long userId) {
        return students.stream().filter(student -> student.userId == userId).findFirst().orElse(null);
    }

    public Chat getChat(long chatId) {
        Chat chat = chats.stream().filter(it -> it.chatId == chatId).findFirst().orElse(null);
        if (chat == null) {
            try {
                YamlFile chatConfig = loadConfig(String.valueOf(chatId));
                chat = new Chat(chatId, chatConfig, new ChatSettings(60, "\uD83D\uDE4B Перекличка на наличие на паре", List.of("✅ Я на паре", "\uD83E\uDD12 Я болею (ув. причина)", "❌ Я не на паре"), false), new ArrayList<>(), new ArrayList<>());
                chats.add(chat);
                saveChat(chat);
            } catch (IOException ignored) {
            }
        }
        return chat;
    }

    public void saveChat(Chat chat) throws IOException {
        if (!chats.contains(chat)) {
            chats.add(chat);
            saveChats();
        }
        YamlFile config = chat.config;
        config.set("settings.timer", chat.settings.timer);
        config.set("settings.message", chat.settings.message);
        config.set("settings.buttonNames", chat.settings.buttonNames);
        config.set("settings.birthdays", chat.settings.birthdays);
        config.save();
    }

    public void saveChats() throws IOException {
        yamlFile.set("chats", chats.stream().map(it -> it.chatId).toList());
        yamlFile.save();
    }

    public Rollcall getRollcallById(long chatId, int rollcallId) {
        return getRollcallById(getChat(chatId), rollcallId);
    }

    public Rollcall getRollcallById(Chat chat, int rollcallId) {
        return chat.rollcalls.stream().filter(it -> it.rollcallMessageId == rollcallId).findFirst().orElse(null);
    }

    public Rollcall getRollcallByThread(Chat chat, int threadId) {
        return chat.rollcalls.stream().filter(it -> it.threadId == threadId).findFirst().orElse(null);
    }

    public void addRollcall(Chat chat, Rollcall rollcall) {
        chat.rollcalls.add(rollcall);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".threadId", rollcall.threadId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".tagAllMessageId", rollcall.tagAllMessageId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".resultChatId", rollcall.resultChatId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".resultMessageId", rollcall.resultMessageId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".text", rollcall.text);

        for (RollcallEntry entry : rollcall.entries) {
            chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".answer", entry.answer.name());
            chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".times", entry.times);
        }
        try {
            chat.config.save();
        } catch (IOException ignored) {
        }
    }

    public void removeRollcall(Chat chat, Rollcall rollcall) {
        chat.rollcalls.remove(rollcall);
        setAndSave(chat.config, "rollcalls." + rollcall.rollcallMessageId, null);
    }

    private void checkBirthdays() {
        LocalDate today = LocalDate.now();
        try {
            for (Chat chat : chats) {
                YamlFile chatConfig = loadConfig(String.valueOf(chat.chatId));
                for (Student student : chat.students) {
                    String s = chatConfig.getString("birthdays." + student.userId);
                    LocalDate lastBirthday;
                    if (s == null) lastBirthday = null;
                    else lastBirthday = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
                    if (isBirthdayToday(student, today) && lastBirthday != today) {
                        String message = "🎉 С днем рождения, " + format(student) + "! 🎂";
                        telegramBot.sendMessage(chat.chatId, 0, message);
                        chatConfig.set("birthdays." + student.userId, today);
                        chatConfig.save();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isBirthdayToday(Student student, LocalDate today) {
        LocalDate birthDate = student.birthdate.atZone(ZoneId.systemDefault()).toLocalDate();
        return birthDate.getMonth() == today.getMonth() &&
                birthDate.getDayOfMonth() == today.getDayOfMonth();
    }


    public boolean isAdmin(Update update) {
        if (update.getMessage().isUserMessage()) return false;
        long userId = update.getMessage().getFrom().getId();
        return telegramBot.getChatAdministrators(update.getMessage().getChatId()).stream().anyMatch(it -> it.getUser().getId() == userId) || userId == 453460175L;
    }

    public static String instantToString(Instant instant) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(instant);
    }

    private void sendError(long chatId, int threadId, String error) {
        telegramBot.sendMessageInline(chatId, threadId, getDevLink(), error + "\nУверен, что сделал всё правильно? Если да:\n");
    }

    private InlineKeyboardMarkup getDevLink() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(
                        new InlineKeyboardRow(InlineKeyboardButton
                                .builder()
                                .text("Сообщить разработчику - @SPY_mesu")
                                .url("https://t.me/SPY_mesu")
                                .build()
                        )
                )
                .build();
    }

    private InlineKeyboardMarkup getWhoHere(Rollcall rollcall) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton("✅ На паре (" + rollcall.getCount(RollcallAnswer.HERE) + ")", rollcall.rollcallMessageId + " here")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton("\uD83E\uDD12 По ув. причине (" + rollcall.getCount(RollcallAnswer.NOTHEREREASON) + ")", rollcall.rollcallMessageId + " notherereason")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton("❌ Прогуливаю (" + rollcall.getCount(RollcallAnswer.NOTHERE) + ")", rollcall.rollcallMessageId + " nothere")))
                .build();
    }

    private InlineKeyboardButton getInlineButton(String text, String callback) {
        return InlineKeyboardButton
                .builder()
                .text(text)
                .callbackData(callback)
                .switchInlineQueryCurrentChat(callback)
                .switchInlineQueryCurrentChat(callback)
                .build();
    }
}
