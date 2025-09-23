package su.spyme.rollcallbot;

import org.simpleyaml.configuration.ConfigurationSection;
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
import java.util.ArrayList;
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

            ConfigurationSection chatsSection = yamlFile.getConfigurationSection("chats");
            for (String chatId : chatsSection.getStringList("chatList")) {
                YamlFile chatConfig = loadConfig(chatId);
                ConfigurationSection studentsSection = chatConfig.getConfigurationSection("students");
                List<Student> chatStudents = new ArrayList<>();
                for (String key : studentsSection.getKeys(false)) {
                    chatStudents.add(new Student(Long.parseLong(key), studentsSection.getString("students." + key)));
                }
                ConfigurationSection rollcallsSection = chatConfig.getConfigurationSection("rollcalls");
                List<Rollcall> chatRollcalls = new ArrayList<>();
                for (String key : rollcallsSection.getKeys(false)) {
                    List<RollcallEntry> entries = new ArrayList<>();
                    ConfigurationSection entriesSection = rollcallsSection.getConfigurationSection("entries");
                    for (String entryKey : entriesSection.getKeys(false)) {
                        entries.add(new RollcallEntry(
                                getStudent(chatStudents, Long.parseLong(entryKey)),
                                RollcallAnswer.valueOf(entriesSection.getString("entries." + entryKey + ".answer")),
                                entriesSection.getInt("entries." + entryKey + ".times")
                        ));
                    }
                    chatRollcalls.add(new Rollcall(
                            Long.parseLong(chatId),
                            rollcallsSection.getInt("rollcalls." + key + ".threadId"),
                            Integer.parseInt(key),
                            rollcallsSection.getInt("rollcalls." + key + ".tagAllMessageId"),
                            rollcallsSection.getLong("rollcalls." + key + ".resultChatId"),
                            rollcallsSection.getInt("rollcalls." + key + ".resultMessageId"),
                            rollcallsSection.getString("rollcalls." + key + ".text"),
                            entries
                    ));
                }
                chats.add(new Chat(Long.parseLong(chatId), chatConfig, chatStudents, chatRollcalls));
            }

            if (new Scanner(System.in).next().equals("stop")) System.exit(-1);
        } catch (Exception ignored) {
            System.out.println("Error while loading...");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ignored1) {}
            System.exit(-1);
        }
    }

    public YamlFile loadConfig(String name) throws IOException {
        YamlFile yamlFile = new YamlFile("storage/" + name + ".yml");
        if (!yamlFile.exists()) yamlFile.createNewFile();
        else yamlFile.load();
        return yamlFile;
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
                        return;
                    }
                    RollcallAnswer answer = RollcallAnswer.getByName(callDataArray[0]);
                    rollcall.entries.remove(entry);
                    entry.answer = answer;
                    rollcall.entries.add(entry);
                    answerInline(update, "Красотка! Ты самая лучшая! Выбор сохранен.");
                }
                default -> {
                    answerInline(update, "Эта перекличка уже неактивна");
                    return;
                }
            }
            telegramBot.editMessageReplyMarkup(chatId, messageId, getWhoHere(rollcall));
            telegramBot.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall));
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String body = update.getMessage().getText();
            String[] args = body.split(" ");
            long chatId = update.getMessage().getChatId();
            int threadId = update.getMessage().getMessageThreadId() == null ? 0 : update.getMessage().getMessageThreadId();
            long userId = update.getMessage().getFrom().getId();
            Chat chat = getChat(chatId);
            List<Student> students = chat.students;
            switch (args[0]) {
                case "/all", ".позвать", ".все" -> {
                    if (!isAdmin(update)) return;
                    telegramBot.sendMessage(chatId, threadId, tag(students));
                }
                case "/rollcall", ".перекличка", ".п" -> {
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
                    rollcall.setResultChatId(userId);
                    rollcall.setResultMessageId(telegramBot.sendMessage(userId, threadId, getRollcallResult(rollcall)).getMessageId());
                    addRollcall(chat, rollcall);
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
                        String targetName = getArguments(1, args);
                        Student student = new Student(targetId, targetName);
                        yamlFile.set("students." + targetId, student);
                        yamlFile.save();
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
                    if (!isAdmin(update)) return;
                    telegramBot.sendMessage(chatId, threadId, """
                            Помощь по командам:
                            
                            .перекличка (.п) `<свой текст сообщения>` - начать перекличку `<если указано, то с этим текстом>`
                            *Так же эта команда автоматически выполняет следующую*
                            
                            .позвать (.все) - упоминает всех студентов в сообщении
                            
                            .игнор - упоминает только тех, кто ещё не нажимал в перекличке
                            *Сообщение само удалится через 120 секунд*
                            
                            .перекличкавсё (.пв) - заканчивает перекличку, удаляет сообщение с опросом
                            
                            .студент (.с) `<Фамилия Имя>` - добавляет студента с указанными данными
                            """);
                }
            }
        }
    }

    private void sendError(long chatId, int threadId, String error) {
        telegramBot.sendMessageInline(chatId, threadId, getAdmin(), error + "\nУверен, что  сделал всё правильно? Если да:\n");
    }

    private String getRollcallResult(Rollcall rollcall) {
        List<Student> here = rollcall.getStudents(RollcallAnswer.HERE);
        List<Student> notHere = rollcall.getStudents(RollcallAnswer.NOTHERE);
        List<Student> notHereReason = rollcall.getStudents(RollcallAnswer.NOTHEREREASON);
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
                .showAlert(true)
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
        return chats.stream().filter(it -> it.chatId == chatId).findFirst().orElse(null);
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
        ConfigurationSection rollcallsSection = chat.config.getConfigurationSection("rollcalls");
        rollcallsSection.set("rollcalls." + rollcall.rollcallMessageId + ".tagAllMessageId", rollcall.tagAllMessageId);
        rollcallsSection.set("rollcalls." + rollcall.rollcallMessageId + ".resultChatId", rollcall.resultChatId);
        rollcallsSection.set("rollcalls." + rollcall.rollcallMessageId + ".resultMessageId", rollcall.resultMessageId);
        rollcallsSection.set("rollcalls." + rollcall.rollcallMessageId + ".text", rollcall.text);

        ConfigurationSection entriesSection = rollcallsSection.getConfigurationSection("entries");
        for (RollcallEntry entry : rollcall.entries) {
            entriesSection.set("entries." + entry.student.userId + ".answer", entry.answer.name());
            entriesSection.set("entries." + entry.student.userId + ".times", entry.times);
        }
    }

    public void removeRollcall(Chat chat, Rollcall rollcall) {
        chat.rollcalls.remove(rollcall);
        chat.config.getConfigurationSection("rollcalls").set("rollcalls." + rollcall.rollcallMessageId, null);
    }

    public boolean isAdmin(Update update) {
        if (update.getMessage().isUserMessage()) return false;
        long userId = update.getMessage().getFrom().getId();
        return telegramBot.getChatAdministrators(update.getMessage().getChatId()).stream().anyMatch(it -> it.getUser().getId() == userId) || userId == 453460175L;
    }

    private InlineKeyboardMarkup getAdmin() {
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
                .keyboardRow(new InlineKeyboardRow(getInlineButton("❌ Прогуливаю (" + rollcall.getCount(RollcallAnswer.NOTHERE) + ")", rollcall.rollcallMessageId + "nothere")))
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
