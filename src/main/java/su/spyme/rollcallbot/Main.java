package su.spyme.rollcallbot;

import org.simpleyaml.configuration.file.YamlFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import su.spyme.rollcallbot.api.TelegramAPI;
import su.spyme.rollcallbot.objects.*;
import su.spyme.rollcallbot.utils.MyUtils;
import su.spyme.rollcallbot.utils.ReminderUtil;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.utils.ConfigUtils.getKeys;
import static su.spyme.rollcallbot.utils.ConfigUtils.loadConfig;
import static su.spyme.rollcallbot.utils.MyUtils.*;
import static su.spyme.rollcallbot.utils.StringUtils.instantToString;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static TelegramClient telegramClient;
    public static TelegramAPI telegramAPI = new TelegramAPI();
    public static YamlFile yamlFile;
    public static List<Chat> chats;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Thread inputThread = new Thread(() -> {
            while (true) if (scanner.nextLine().trim().equalsIgnoreCase("stop")) System.exit(0);
        });
        inputThread.setDaemon(true);
        inputThread.start();

        loadAll();
        checkBirthdays();
        try {
            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(System.getenv("rollcall_bot_token"), new Bot());
            telegramClient = new OkHttpTelegramClient(System.getenv("rollcall_bot_token"));
            telegramAPI.setBotCommands();
        } catch (TelegramApiException exception) {
            logger.error("Error in TelegramAPI: {}", exception.getMessage());
        }
        for (Chat chat : chats) {
            updateChatAdmins(chat);
        }
    }

    public static void loadAll() {
        try {
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
                        Student student = getStudent(chatStudents, Long.parseLong(entryKey));
                        if (student == null) {
                            logger.warn("Пропущена запись переклички {} из-за отсутствующего студента {}", key, entryKey);
                            continue;
                        }
                        entries.add(new RollcallEntry(
                                student,
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
                            chatConfig.getLong("rollcalls." + key + ".startTime"),
                            entries
                    ));
                }
                ChatSettings settings = new ChatSettings(
                        chatConfig.getInt("settings.timer", 60),
                        chatConfig.getString("settings.message", "\uD83D\uDE4B Перекличка на наличие на паре"),
                        chatConfig.getStringList("settings.buttonNames"),
                        chatConfig.getBoolean("settings.birthdays", true)
                );
                if (settings.buttonNames.isEmpty())
                    settings.buttonNames = List.of("✅ Я на паре", "\uD83E\uDD12 Я болею (ув. причина)", "❌ Я не на паре");
                String name = chatConfig.getString("name", "");
                Chat chat = new Chat(Long.parseLong(chatId), name, chatConfig, new ArrayList<>(), settings, chatStudents, chatRollcalls);
                chats.add(chat);
                saveChat(chat);
            }
            logger.info("Загружено {} чатов", chats.size());
            new ReminderUtil().start();
        } catch (Exception exception) {
            logger.error("Error while loading...");
            throw new RuntimeException(exception);
        }
    }

    private static void checkBirthdays() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(7).withMinute(0).withSecond(0).withNano(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(MyUtils::checkBirthdays,
                Duration.between(now, nextRun).toMillis(),
                24 * 60 * 60 * 1000,
                TimeUnit.MILLISECONDS);
    }
}
