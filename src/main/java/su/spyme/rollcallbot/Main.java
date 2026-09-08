package su.spyme.rollcallbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import su.spyme.rollcallbot.api.TelegramAPI;
import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.storage.ChatStorage;
import su.spyme.rollcallbot.utils.MyUtils;
import su.spyme.rollcallbot.utils.ReminderUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.utils.MyUtils.saveChat;
import static su.spyme.rollcallbot.utils.MyUtils.updateChatAdmins;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static final long OWNER_ID = 453460175L;
    public static final ZoneId ZONE = loadZone();
    private static final int BIRTHDAY_CHECK_HOUR = 7;
    public static TelegramClient telegramClient;
    public static TelegramAPI telegramAPI = new TelegramAPI();
    public static ChatStorage storage = new ChatStorage(Path.of("storage"));
    public static List<Chat> chats;

    public static void main(String[] args) {
        String token = System.getenv("rollcall_bot_token");
        if (token == null || token.isBlank()) {
            logger.error("Не задана переменная окружения rollcall_bot_token");
            return;
        }
        Scanner scanner = new Scanner(System.in);
        Thread inputThread = new Thread(() -> {
            while (scanner.hasNextLine()) if (scanner.nextLine().trim().equalsIgnoreCase("stop")) System.exit(0);
        });
        inputThread.setDaemon(true);
        inputThread.start();

        loadAll();
        scheduleBirthdayCheck();
        try {
            telegramClient = new OkHttpTelegramClient(token);
            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(token, new Bot());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Остановка бота");
                try {
                    botsApplication.close();
                } catch (Exception exception) {
                    logger.error("Error while stopping bot", exception);
                }
            }));
            telegramAPI.setBotCommands();
        } catch (TelegramApiException exception) {
            logger.error("Error in TelegramAPI: {}", exception.getMessage());
        }
        for (Chat chat : chats) {
            updateChatAdmins(chat);
        }
        new ReminderUtil().start();
    }

    public static void loadAll() {
        try {
            chats = new CopyOnWriteArrayList<>();
            for (long chatId : storage.loadChatIds()) {
                Chat chat = storage.load(chatId);
                chats.add(chat);
                saveChat(chat);
            }
            logger.info("Загружено {} чатов", chats.size());
        } catch (Exception exception) {
            logger.error("Error while loading...");
            throw new RuntimeException(exception);
        }
    }

    private static ZoneId loadZone() {
        String zone = System.getenv("rollcall_bot_timezone");
        return zone == null || zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone);
    }

    private static void scheduleBirthdayCheck() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime nextRun = now.withHour(BIRTHDAY_CHECK_HOUR).withMinute(0).withSecond(0).withNano(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(MyUtils::checkBirthdays,
                Duration.between(now, nextRun).toMillis(),
                TimeUnit.DAYS.toMillis(1),
                TimeUnit.MILLISECONDS);
    }
}
