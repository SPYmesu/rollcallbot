package su.spyme.rollcallbot.utils;

import org.simpleyaml.configuration.file.YamlFile;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static su.spyme.rollcallbot.Main.chats;
import static su.spyme.rollcallbot.Main.yamlFile;
import static su.spyme.rollcallbot.utils.ConfigUtils.loadConfig;
import static su.spyme.rollcallbot.utils.ConfigUtils.setAndSave;

public class MyUtils {

    public static Chat getChat(long chatId) {
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

    public static void saveChat(Chat chat) throws IOException {
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

    public static void saveChats() throws IOException {
        yamlFile.set("chats", chats.stream().map(it -> it.chatId).toList());
        yamlFile.save();
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

    public static Student getStudent(List<Student> students, long userId) {
        return students.stream().filter(student -> student.userId == userId).findFirst().orElse(null);
    }
}
