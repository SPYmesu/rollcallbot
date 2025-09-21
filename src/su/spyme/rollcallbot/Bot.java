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
import su.spyme.rollcallbot.objects.Recall;
import su.spyme.rollcallbot.objects.RecallAnswer;
import su.spyme.rollcallbot.objects.RecallEntry;
import su.spyme.rollcallbot.objects.Student;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Bot {

    TelegramBot telegramBot = new TelegramBot();
    YamlFile yamlFile;
    List<Recall> recalls = new ArrayList<>();
    List<Student> students = new ArrayList<>();

    public void start(TelegramBot telegramBot) {
        try {
            this.telegramBot = telegramBot;

            yamlFile = new YamlFile("/config.yml");
            if (!yamlFile.exists()) yamlFile.createNewFile(); else yamlFile.load();

            ConfigurationSection section = yamlFile.getConfigurationSection("students");
            Set<String> keys = section.getKeys(false);
            for (String key : keys) {
                students.add(new Student(Long.parseLong(key), yamlFile.getString("students." + key)));
            }

            if (new Scanner(System.in).next().equals("stop")) System.exit(666);
        } catch (Exception ex) {
            System.out.println("Error while loading...");
            ex.printStackTrace();
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
            System.exit(-1);
        }
    }

    public void onUpdateReceived(Update update) throws TelegramApiException, IOException {
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            String[] callDataArray = callData.split(" ");
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            User user = update.getCallbackQuery().getFrom();
            Recall recall = getRecall(chatId);
            switch (callDataArray[0]) {
                case "here", "notherereason", "nothere" -> {
                    if (recall == null) {
                        answerInline(update, "Эта перекличка уже неактивна");
                        return;
                    }
                    RecallEntry entry = recall.entries.stream().filter(it -> it.student.userId == user.getId()).findAny().orElse(null);
                    if (entry == null) {
                        answerInline(update, "Ты не зарегистрирован, обратись к старосте");
                        return;
                    }
                    if (entry.answer != RecallAnswer.IGNORE) {
                        answerInline(update, "Ты уже сделал свой выбор...");
                        entry.addTimes();
                        return;
                    }
                    RecallAnswer answer = RecallAnswer.getByName(callDataArray[0]);
                    recall.entries.remove(entry);
                    entry.answer = answer;
                    recall.entries.add(entry);
                    answerInline(update, "Красотка! Ты самая лучшая! Выбор сохранен.");
                }
                default -> {
                    return;
                }
            }
            telegramBot.editMessageReplyMarkup(chatId, messageId, getWhoHere(recall));
            telegramBot.editMessageText(recall.resultChatId, recall.resultMessageId, getRecallResult(recall));
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String body = update.getMessage().getText();
            String[] args = body.split(" ");
            long chatId = update.getMessage().getChatId();
            int threadId = update.getMessage().getMessageThreadId() == null ? 0 : update.getMessage().getMessageThreadId();
            long userId = update.getMessage().getFrom().getId();
            switch (args[0]) {
                case ".позвать", ".все" -> {
                    if (!isAdmin(update)) return;
                    telegramBot.sendMessage(chatId, threadId, tag(students));
                }
                case ".перекличка", ".п" -> {
                    if (!isAdmin(update)) return;
                    String text = "Перекличка на наличие на паре!!!";
                    if (args.length > 1) {
                        text = getArguments(1, args);
                    }
                    List<RecallEntry> entries = new ArrayList<>();
                    for (Student student : students) {
                        entries.add(new RecallEntry(student, RecallAnswer.IGNORE, 0));
                    }
                    Recall recall = new Recall(chatId, 0, 0, 0L, 0, text, entries);
                    telegramBot.deleteMessage(chatId, update.getMessage().getMessageId());
                    recall.setTagAllMessageId(telegramBot.sendMessage(chatId, threadId, tag(students)).getMessageId());
                    recall.setRecallMessageId(telegramBot.sendMessageInline(chatId, threadId, getWhoHere(recall), recall.text).getMessageId());
                    recall.setResultChatId(userId);
                    recall.setResultMessageId(telegramBot.sendMessage(userId, threadId, getRecallResult(recall)).getMessageId());
                    recalls.add(recall);
                }
                case ".перекличкавсё", ".пв" -> {
                    if (!isAdmin(update)) return;
                    Recall recall = getRecall(chatId);
                    if (recall == null) {
                        sendError(chatId, threadId, "recall == null;");
                        return;
                    }
                    telegramBot.deleteMessage(chatId, recall.recallMessageId);
                    telegramBot.deleteMessage(chatId, recall.tagAllMessageId);
                    telegramBot.deleteMessage(chatId, update.getMessage().getMessageId());
                    recalls.remove(recall);
                    StringBuilder text = new StringBuilder("Перекличка `#" + recall.recallMessageId + "` завершена");

                    RecallEntry best = recall.entries.getFirst();
                    for (RecallEntry entry : recall.entries) {
                        if (entry.times > best.times) best = entry;
                    }
                    if (best.times > 5) text.append("\n\nИнтересный факт: ").append(best.student.name).append(" кликнул на кнопку ").append(best.times).append(" раз!");
                    telegramBot.sendMessage(chatId, threadId, text.toString());
                }
                case ".студент", ".с" -> {
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
                case ".игнор" -> {
                    if (!isAdmin(update)) return;
                    Recall recall = getRecall(chatId);
                    if (recall != null) {
                        telegramBot.deleteMessage(chatId, update.getMessage().getMessageId());
                        int ignoreMessageId = telegramBot.sendMessage(chatId, threadId, tag(recall.getStudents(RecallAnswer.IGNORE)) +"\n\nНе забудьте сделать выбор выше, иначе Вам проставят отсутствие...").getMessageId();
                        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> telegramBot.deleteMessage(chatId, ignoreMessageId), 0, 120, TimeUnit.SECONDS);
                    }
                }
                case ".помощь" -> {
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

    private String getRecallResult(Recall recall) {
        List<Student> here = recall.getStudents(RecallAnswer.HERE);
        List<Student> notHere = recall.getStudents(RecallAnswer.NOTHERE);
        List<Student> notHereReason = recall.getStudents(RecallAnswer.NOTHEREREASON);
        List<Student> ignore = recall.getStudents(RecallAnswer.IGNORE);
        StringBuilder builder = new StringBuilder("Результат переклички. `#" + recall.recallMessageId + "`");
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

    public Student getStudent(long userId) {
        return students.stream().filter(student -> student.userId == userId).findFirst().orElse(null);
    }

    public Recall getRecall(long chatId) {
        return recalls.stream().filter(it -> it.chatId == chatId).findFirst().orElse(null);
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

    private InlineKeyboardMarkup getWhoHere(Recall recall) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton("✅ На паре (" + recall.getCount(RecallAnswer.HERE) + ")", "here ")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton("\uD83E\uDD12 По ув. причине (" + recall.getCount(RecallAnswer.NOTHEREREASON) + ")", "notherereason ")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton("❌ Прогуливаю (" + recall.getCount(RecallAnswer.NOTHERE) + ")", "nothere ")))
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
