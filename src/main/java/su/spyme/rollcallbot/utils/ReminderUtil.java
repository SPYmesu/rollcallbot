package su.spyme.rollcallbot.utils;

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
import static su.spyme.rollcallbot.utils.MyUtils.removeRollcall;
import static su.spyme.rollcallbot.utils.StringUtils.tag;

public class ReminderUtil {
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
                checkRollcall(chat, rollcall, currentTime);
            }
        }
    }

    private void checkRollcall(Chat chat, Rollcall rollcall, long currentTime) {
        if (chat.settings.timer == -1) return;
        if (rollcall.getStudents(RollcallAnswer.IGNORE).isEmpty()) {
            processFinish(chat, rollcall);
            return;
        }
        long finishTime = rollcall.startTime + TimeUnit.MINUTES.toMillis(chat.settings.timer);
        long timeLeft = finishTime - currentTime;
        String rollcallKey = rollcall.chatId + "_" + rollcall.threadId + "_" + rollcall.startTime;
        if (timeLeft <= 0) {
            String finishKey = rollcallKey + "_finish";
            if (!processedReminders.contains(finishKey)) {
                processFinish(chat, rollcall);
                processedReminders.add(finishKey);
            }
        } else {
            checkReminder(rollcall, timeLeft, rollcallKey, TimeUnit.MINUTES.toMillis(30), "30min");
            checkReminder(rollcall, timeLeft, rollcallKey, TimeUnit.MINUTES.toMillis(15), "15min");
            checkReminder(rollcall, timeLeft, rollcallKey, TimeUnit.MINUTES.toMillis(5), "5min");
        }
    }

    private void checkReminder(Rollcall rollcall, long timeLeft, String rollcallKey, long reminderTime, String reminderType) {
        if (timeLeft <= reminderTime) {
            String reminderKey = rollcallKey + "_" + reminderType;
            if (!processedReminders.contains(reminderKey)) {
                processReminder(rollcall, reminderType);
                processedReminders.add(reminderKey);
            }
        }
    }

    private void processReminder(Rollcall rollcall, String reminderType) {
        long minutesLeft = switch (reminderType) {
            case "30min" -> 30;
            case "15min" -> 15;
            case "5min" -> 5;
            default -> 0;
        };
        List<Student> ignore = rollcall.getStudents(RollcallAnswer.IGNORE);
        if (ignore.isEmpty()) return;
        Message ignoreMessage = telegramAPI.sendMessage(rollcall.chatId, rollcall.threadId, tag(ignore) + "\n\n⚠ Не забудьте сделать выбор выше, иначе Вам проставят отсутствие...\n⌛ Осталось " + minutesLeft + " минут.");
        if (ignoreMessage != null) {
            scheduler.schedule(() -> telegramAPI.deleteMessage(rollcall.chatId, ignoreMessage.getMessageId()), 120, TimeUnit.SECONDS);
        }
    }

    private void processFinish(Chat chat, Rollcall rollcall) {
        telegramAPI.deleteMessage(rollcall.chatId, rollcall.rollcallMessageId);
        telegramAPI.deleteMessage(rollcall.chatId, rollcall.tagAllMessageId);
        removeRollcall(chat, rollcall);
        StringBuilder text = new StringBuilder("\uD83D\uDE4B Перекличка `#" + rollcall.rollcallMessageId + "` завершена");
        RollcallEntry best = rollcall.entries.getFirst();
        for (RollcallEntry entry : rollcall.entries) {
            if (entry.times > best.times) best = entry;
        }
        if (best.times > 5)
            text.append("\n\nИнтересный факт: ").append(best.student.name).append(" кликнул на кнопку ").append(best.times).append(" раз!");
        telegramAPI.sendMessage(rollcall.chatId, rollcall.threadId, text.toString());
    }

    private void cleanupOldReminders() {
        long dayAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
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
