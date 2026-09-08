package su.spyme.rollcallbot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static su.spyme.rollcallbot.Main.*;
import static su.spyme.rollcallbot.utils.StringUtils.*;

public class MyUtils {
    private static final Logger logger = LoggerFactory.getLogger(MyUtils.class);
    private static final int CLICKS_FOR_FUN_FACT = 5;

    public static Chat getChat(long chatId) {
        Chat chat = chats.stream().filter(it -> it.chatId == chatId).findFirst().orElse(null);
        if (chat == null) {
            try {
                List<Long> admins = telegramAPI.getChatAdministrators(chatId).stream().map(it -> it.getUser().getId()).toList();
                String name = telegramAPI.getChatTitle(chatId);
                if (name == null) return null;
                chat = storage.load(chatId);
                chat.name = name;
                chat.admins = admins;
                saveChat(chat);
            } catch (IOException exception) {
                logger.error("Error while loading chat {}", chatId, exception);
                return null;
            }
        }
        return chat;
    }

    public static void saveChat(Chat chat) throws IOException {
        if (!chats.contains(chat)) {
            chats.add(chat);
            saveChats();
        }
        storage.save(chat);
    }

    public static void trySaveChat(Chat chat) {
        try {
            saveChat(chat);
        } catch (IOException exception) {
            logger.error("Error while saving chat {}", chat.chatId, exception);
        }
    }

    public static void saveChats() throws IOException {
        storage.saveChatIds(chats.stream().map(it -> it.chatId).toList());
    }

    public static void updateChatAdmins(Chat chat) {
        chat.admins = telegramAPI.getChatAdministrators(chat.chatId).stream().map(it -> it.getUser().getId()).toList();
    }

    public static void updateChatName(Chat chat) {
        String name = telegramAPI.getChatTitle(chat.chatId);
        if (name != null) chat.name = name;
    }

    public static Rollcall getRollcallById(long chatId, int rollcallId) {
        return getRollcallById(getChat(chatId), rollcallId);
    }

    public static Rollcall getRollcallById(Chat chat, int rollcallId) {
        return chat.rollcalls.stream().filter(it -> it.rollcallMessageId == rollcallId).findFirst().orElse(null);
    }

    public static Rollcall getRollcallByThread(Chat chat, int threadId) {
        return chat.rollcalls.stream().filter(it -> it.threadId == threadId).findFirst().orElse(null);
    }

    public static void addRollcall(Chat chat, Rollcall rollcall) {
        chat.rollcalls.add(rollcall);
        trySaveChat(chat);
    }

    public static void removeRollcall(Chat chat, Rollcall rollcall) {
        chat.rollcalls.remove(rollcall);
        trySaveChat(chat);
    }

    public static synchronized void finishRollcall(Chat chat, Rollcall rollcall) {
        if (!chat.rollcalls.contains(rollcall)) return;
        telegramAPI.deleteMessage(rollcall.chatId, rollcall.rollcallMessageId);
        telegramAPI.deleteMessage(rollcall.chatId, rollcall.tagAllMessageId);
        removeRollcall(chat, rollcall);
        StringBuilder text = new StringBuilder("\uD83D\uDE4B Перекличка `#" + rollcall.rollcallMessageId + "` завершена");
        if (!rollcall.entries.isEmpty()) {
            RollcallEntry best = rollcall.entries.getFirst();
            for (RollcallEntry entry : rollcall.entries) {
                if (entry.times > best.times) best = entry;
            }
            if (best.times > CLICKS_FOR_FUN_FACT)
                text.append("\n\nИнтересный факт: ").append(escapeMarkdown(best.student.name)).append(" кликнул на кнопку ").append(best.times).append(" раз!");
        }
        telegramAPI.sendMessage(rollcall.chatId, rollcall.threadId, text.toString());
        telegramAPI.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, chat.students) + "\n\n✅ Перекличка завершена");
    }

    public static InlineKeyboardMarkup getRollcallInline(Chat chat, Rollcall rollcall) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder<?, ?> builder = InlineKeyboardMarkup.builder();
        for (RollcallAnswer answer : RollcallAnswer.BUTTONS) {
            builder.keyboardRow(new InlineKeyboardRow(getInlineButton(
                    chat.settings.getButton(answer) + " (" + rollcall.getCount(answer) + ")",
                    "rollcall " + rollcall.rollcallMessageId + " " + answer.name().toLowerCase()
            )));
        }
        return builder.build();
    }

    public static String getRollcallResult(Rollcall rollcall, List<Student> sortExample) {
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
            builder.append("\n").append(escapeMarkdown(student.name));
        }
        int notHereSize = notHere.size() + notHereReason.size();
        if (notHereSize > 0) {
            builder.append("\n");
            builder.append("\nНе на паре: (").append(notHereSize).append(")");
            for (Student student : notHereReason) {
                builder.append("\n").append(escapeMarkdown(student.name)).append(" (по ув. причине)");
            }
            for (Student student : notHere) {
                builder.append("\n").append(escapeMarkdown(student.name));
            }
        }
        if (!ignore.isEmpty()) {
            builder.append("\n");
            builder.append("\nПроигнорировали: (").append(ignore.size()).append(")");
            for (Student student : ignore) {
                builder.append("\n").append(escapeMarkdown(student.name));
            }
        }
        return builder.toString();
    }

    public static Student getStudent(List<Student> students, long userId) {
        return students.stream().filter(student -> student.userId == userId).findFirst().orElse(null);
    }

    public static InlineKeyboardButton getInlineButton(String text, String callback) {
        return InlineKeyboardButton
                .builder()
                .text(text)
                .callbackData(callback)
                .build();
    }

    public static void checkBirthdays() {
        try {
            for (Chat chat : chats) {
                if (!chat.settings.birthdays) continue;
                for (Student student : chat.students) {
                    if (isBirthdayToday(student)) {
                        String message = "🎉 С днем рождения, " + format(student) + "! 🎂";
                        telegramAPI.sendMessage(chat.chatId, 0, message);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error while checkBirthdays()", e);
        }
    }

    public static boolean hasBirthdate(Student student) {
        return student.birthdate.atZone(ZONE).toLocalDate().isAfter(LocalDate.EPOCH);
    }

    public static boolean isBirthdayToday(Student student) {
        if (!hasBirthdate(student)) return false;
        LocalDate today = LocalDate.now(ZONE);
        LocalDate birthDate = student.birthdate.atZone(ZONE).toLocalDate();
        return birthDate.getMonth() == today.getMonth() &&
                birthDate.getDayOfMonth() == today.getDayOfMonth();
    }
}
