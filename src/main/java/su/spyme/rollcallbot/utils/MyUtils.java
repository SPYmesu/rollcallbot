package su.spyme.rollcallbot.utils;

import org.simpleyaml.configuration.file.YamlFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static su.spyme.rollcallbot.Main.*;
import static su.spyme.rollcallbot.utils.ConfigUtils.loadConfig;
import static su.spyme.rollcallbot.utils.ConfigUtils.setAndSave;
import static su.spyme.rollcallbot.utils.StringUtils.format;
import static su.spyme.rollcallbot.utils.StringUtils.instantToString;

public class MyUtils {

    public static Chat getChat(long chatId) {
        Chat chat = chats.stream().filter(it -> it.chatId == chatId).findFirst().orElse(null);
        if (chat == null) {
            try {
                YamlFile chatConfig = loadConfig(String.valueOf(chatId));
                List<Long> admins = telegramAPI.getChatAdministrators(chatId).stream().map(it -> it.getUser().getId()).toList();
                String name = telegramAPI.getChat(chatId).getTitle();
                chat = new Chat(chatId, name, chatConfig, admins, new ChatSettings(60, "\uD83D\uDE4B Перекличка на наличие на паре", List.of("✅ Я на паре", "\uD83E\uDD12 Я болею (ув. причина)", "❌ Я не на паре"), false), new ArrayList<>(), new CopyOnWriteArrayList<>());
                saveChat(chat);
            } catch (IOException ignored) {
            }
        }
        return chat;
    }

    public static void saveChat(Chat chat) throws IOException {
        if (!chats.contains(chat)) {
            chats.add(chat);
            saveChats();
        }
        YamlFile config = chat.config;
        config.set("name", chat.name);
        config.set("settings.timer", chat.settings.timer);
        config.set("settings.message", chat.settings.message);
        config.set("settings.buttonNames", chat.settings.buttonNames);
        config.set("settings.birthdays", chat.settings.birthdays);
        config.set("students", null);
        for (Student student : chat.students) {
            config.set("students." + student.userId + ".name", student.name);
            config.set("students." + student.userId + ".birthdate", instantToString(student.birthdate));
        }
        config.save();
    }

    public static void saveChats() throws IOException {
        yamlFile.set("chats", chats.stream().map(it -> it.chatId).toList());
        yamlFile.save();
    }

    public static void updateChatAdmins(Chat chat) {
        chat.setAdmins(telegramAPI.getChatAdministrators(chat.chatId).stream().map(it -> it.getUser().getId()).toList());
    }

    public static void updateChatName(Chat chat) {
        chat.setName(telegramAPI.getChat(chat.chatId).getTitle());
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
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".threadId", rollcall.threadId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".tagAllMessageId", rollcall.tagAllMessageId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".resultChatId", rollcall.resultChatId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".resultMessageId", rollcall.resultMessageId);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".text", rollcall.text);
        chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".startTime", rollcall.startTime);

        for (RollcallEntry entry : rollcall.entries) {
            chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".answer", entry.answer.name());
            chat.config.set("rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".times", entry.times);
        }
        try {
            chat.config.save();
        } catch (IOException ignored) {
        }
    }

    public static void removeRollcall(Chat chat, Rollcall rollcall) {
        chat.rollcalls.remove(rollcall);
        setAndSave(chat.config, "rollcalls." + rollcall.rollcallMessageId, null);
    }

    public static InlineKeyboardMarkup getRollcallInline(Chat chat, Rollcall rollcall) {
        List<String> buttons = chat.settings.buttonNames;
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(buttons.get(0) + " (" + rollcall.getCount(RollcallAnswer.HERE) + ")", "rollcall " + rollcall.rollcallMessageId + " here")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(buttons.get(1) + " (" + rollcall.getCount(RollcallAnswer.NOTHEREREASON) + ")", "rollcall " + rollcall.rollcallMessageId + " notherereason")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(buttons.get(2) + " (" + rollcall.getCount(RollcallAnswer.NOTHERE) + ")", "rollcall " + rollcall.rollcallMessageId + " nothere")))
                .build();
    }

    public static InlineKeyboardMarkup getSettingsInline(Chat chat) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "⏳ Автозавершение: " + (chat.settings.timer == -1 ? "выкл." : chat.settings.timer + " мин."),
                                        "settings " + chat.chatId + " timer"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "\uD83C\uDF89 Дни рождения: " + (chat.settings.birthdays ? "вкл." : "выкл."),
                                        "settings " + chat.chatId + " birthdays"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "\uD83D\uDCAC Управление сообщением переклички",
                                        "settings " + chat.chatId + " message"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "\uD83D\uDC65 Управление студентами",
                                        "settings " + chat.chatId + " students"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "\uD83D\uDD04 Обновить информацию о чате",
                                        "settings " + chat.chatId + " updatechat"
                                )
                        )
                )
                .build();
    }

    public static InlineKeyboardMarkup getMessageSettingsInline(Chat chat) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "ℹ Изменить сообщение",
                                        "settings " + chat.chatId + " message text"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "✏: " + chat.settings.buttonNames.get(0),
                                        "settings " + chat.chatId + " message button0"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "✏: " + chat.settings.buttonNames.get(1),
                                        "settings " + chat.chatId + " message button1"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "✏: " + chat.settings.buttonNames.get(2),
                                        "settings " + chat.chatId + " message button2"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "\uD83D\uDD19 Назад",
                                        "settings " + chat.chatId + " select"
                                )
                        )
                )
                .build();
    }

    public static String getStudentsMenu(Chat chat) {
        StringBuilder sb = new StringBuilder("\uD83D\uDC65 Студенты в этом чате:\n\n");
        int num = 1;
        for (Student student : chat.students) {
            sb.append(num++).append(". ").append(student.getName()).append("\n");
        }
        sb.append("\nℹ Отправь номер студента, которого нужно изменить");
        return sb.toString();
    }

    public static InlineKeyboardMarkup getStudentInline(Chat chat, Student student) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "ℹ Изменить позицию в списке",
                                        "settings " + chat.chatId + " student " + student.userId + " position"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "ℹ Изменить фамилию и имя",
                                        "settings " + chat.chatId + " student " + student.userId + " name"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "ℹ Изменить дату рождения",
                                        "settings " + chat.chatId + " student " + student.userId + " birthdate"
                                )
                        )
                )
                .keyboardRow(
                        new InlineKeyboardRow(
                                getInlineButton(
                                        "\uD83D\uDD19 Назад",
                                        "settings " + chat.chatId + " students"
                                )
                        )
                )
                .build();
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

    public static Student getStudent(List<Student> students, long userId) {
        return students.stream().filter(student -> student.userId == userId).findFirst().orElse(null);
    }

    public static InlineKeyboardButton getInlineButton(String text, String callback) {
        return InlineKeyboardButton
                .builder()
                .text(text)
                .callbackData(callback)
                .switchInlineQueryCurrentChat(callback)
                .build();
    }

    public static void checkBirthdays() {
        try {
            for (Chat chat : chats) {
                if (!chat.settings.birthdays) continue;
                YamlFile chatConfig = loadConfig(String.valueOf(chat.chatId));
                for (Student student : chat.students) {
                    if (isBirthdayToday(student)) {
                        String message = "🎉 С днем рождения, " + format(student) + "! 🎂";
                        telegramAPI.sendMessage(chat.chatId, 0, message);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isBirthdayToday(Student student) {
        LocalDate today = LocalDate.now();
        LocalDate birthDate = student.birthdate.atZone(ZoneId.systemDefault()).toLocalDate();
        return birthDate.getMonth() == today.getMonth() &&
                birthDate.getDayOfMonth() == today.getDayOfMonth();
    }
}
