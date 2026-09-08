package su.spyme.rollcallbot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.objects.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.Main.chats;
import static su.spyme.rollcallbot.Main.telegramAPI;
import static su.spyme.rollcallbot.utils.MyUtils.finishRollcall;
import static su.spyme.rollcallbot.utils.StringUtils.tag;

public class ReminderUtil {
    private static final Logger logger = LoggerFactory.getLogger(ReminderUtil.class);
    private static final int[] REMINDERS = {5, 15, 30};
    private static final int REMINDER_LIFETIME_SECONDS = 120;
    private static final long PROCESSED_KEEP_TIME = TimeUnit.HOURS.toMillis(24);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Set<String> processedReminders = new HashSet<>();

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAllRollcalls, 0, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::cleanupOldReminders, 1, 1, TimeUnit.HOURS);
    }

    private void checkAllRollcalls() {
        long currentTime = System.currentTimeMillis();
        for (Chat chat : chats) {
            if (chat.rollcalls == null) continue;
            for (Rollcall rollcall : chat.rollcalls) {
                try {
                    checkRollcall(chat, rollcall, currentTime);
                } catch (Exception exception) {
                    logger.error("Error while checkRollcall({}, {})", chat.chatId, rollcall.rollcallMessageId, exception);
                }
            }
        }
    }

    private void checkRollcall(Chat chat, Rollcall rollcall, long currentTime) {
        if (rollcall.getStudents(RollcallAnswer.IGNORE).isEmpty()) {
            finishRollcall(chat, rollcall);
            return;
        }
        if (chat.settings.timer == ChatSettings.TIMER_OFF) return;
        long finishTime = rollcall.startTime + TimeUnit.MINUTES.toMillis(chat.settings.timer);
        long timeLeft = finishTime - currentTime;
        String rollcallKey = rollcall.chatId + "_" + rollcall.threadId + "_" + rollcall.startTime;
        if (timeLeft <= 0) {
            if (processedReminders.add(rollcallKey + "_finish")) {
                finishRollcall(chat, rollcall);
            }
        } else {
            checkReminders(rollcall, timeLeft, chat.settings.timer, rollcallKey);
        }
    }

    private void checkReminders(Rollcall rollcall, long timeLeft, int timer, String rollcallKey) {
        for (int i = 0; i < REMINDERS.length; i++) {
            int minutes = REMINDERS[i];
            if (minutes >= timer || timeLeft > TimeUnit.MINUTES.toMillis(minutes)) continue;
            if (processedReminders.add(rollcallKey + "_" + minutes + "min")) {
                sendReminder(rollcall, minutes);
            }
            for (int j = i + 1; j < REMINDERS.length; j++) {
                processedReminders.add(rollcallKey + "_" + REMINDERS[j] + "min");
            }
            return;
        }
    }

    private void sendReminder(Rollcall rollcall, int minutesLeft) {
        List<Student> ignore = rollcall.getStudents(RollcallAnswer.IGNORE);
        if (ignore.isEmpty()) return;
        Message ignoreMessage = telegramAPI.sendMessage(rollcall.chatId, rollcall.threadId, tag(ignore) + "\n\n⚠ Не забудьте сделать выбор выше, иначе Вам проставят отсутствие...\n⌛ Осталось " + minutesLeft + " минут.");
        if (ignoreMessage != null) {
            scheduler.schedule(() -> telegramAPI.deleteMessage(rollcall.chatId, ignoreMessage.getMessageId()), REMINDER_LIFETIME_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void cleanupOldReminders() {
        long dayAgo = System.currentTimeMillis() - PROCESSED_KEEP_TIME;
        processedReminders.removeIf(key -> {
            try {
                String[] parts = key.split("_");
                long rollcallTime = Long.parseLong(parts[2]);
                return rollcallTime < dayAgo;
            } catch (Exception e) {
                return true;
            }
        });
    }
}
