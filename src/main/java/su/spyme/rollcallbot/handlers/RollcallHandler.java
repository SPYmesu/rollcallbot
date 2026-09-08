package su.spyme.rollcallbot.handlers;

import org.simpleyaml.configuration.file.YamlFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.objects.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.Main.telegramAPI;
import static su.spyme.rollcallbot.utils.ConfigUtils.setAndSave;
import static su.spyme.rollcallbot.utils.MyUtils.*;
import static su.spyme.rollcallbot.utils.StringUtils.getArguments;
import static su.spyme.rollcallbot.utils.StringUtils.tag;

public class RollcallHandler {
    private static final Logger logger = LoggerFactory.getLogger(RollcallHandler.class);
    private static final long ANSWER_CHANGE_DELAY = TimeUnit.MINUTES.toMillis(1);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void tagAll(Chat chat, int threadId) {
        if (chat.students.isEmpty()) {
            telegramAPI.sendMessage(chat.chatId, threadId, "❌ В этом чате нет студентов, добавьте их командой `.студент`");
            return;
        }
        telegramAPI.sendMessage(chat.chatId, threadId, tag(chat.students));
    }

    public void start(Chat chat, Message message, int threadId, String[] args) {
        long chatId = chat.chatId;
        long userId = message.getFrom().getId();
        List<Student> students = chat.students;
        try {
            if (getRollcallByThread(chat, threadId) != null) {
                telegramAPI.sendMessage(chatId, threadId, "В этом чате уже активна перекличка... \nСначала заверши её (`.пв`)");
                return;
            }
            if (students.isEmpty()) {
                telegramAPI.sendMessage(chatId, threadId, "❌ В этом чате нет студентов, добавьте их командой `.студент`");
                return;
            }
            String text = chat.settings.message;
            if (args.length > 1) {
                text = getArguments(1, args);
            }
            List<RollcallEntry> entries = new ArrayList<>();
            for (Student student : students) {
                entries.add(new RollcallEntry(student, RollcallAnswer.IGNORE, 0, 0));
            }
            Rollcall rollcall = new Rollcall(chatId, threadId, 0, 0, 0L, 0, text, System.currentTimeMillis(), entries);
            rollcall.setResultChatId(userId);
            Message resultMessage = telegramAPI.sendMessage(userId, 0, getRollcallResult(rollcall, students));
            if (resultMessage == null) {
                telegramAPI.sendMessage(chatId, threadId, "❌ Не удалось отправить сообщение с результатом переклички, проверьте, может ли бот вам писать в личные сообщения.");
                return;
            }
            rollcall.setResultMessageId(resultMessage.getMessageId());
            telegramAPI.deleteMessage(chatId, message.getMessageId());
            Message tagAllMessage = telegramAPI.sendMessage(chatId, threadId, tag(students));
            if (tagAllMessage == null) {
                telegramAPI.deleteMessage(userId, rollcall.resultMessageId);
                telegramAPI.sendError(chatId, threadId, "❌ Не удалось отправить сообщение с упоминанием студентов");
                return;
            }
            rollcall.setTagAllMessageId(tagAllMessage.getMessageId());
            String rollcallText = rollcall.text;
            if (chat.settings.timer != -1) {
                rollcallText += "\n\n⏳ Перекличка завершится через " + chat.settings.timer + " мин.";
            }
            Message rollcallMessage = telegramAPI.sendMessageInline(chatId, threadId, getRollcallInline(chat, rollcall), rollcallText);
            if (rollcallMessage == null) {
                telegramAPI.deleteMessage(chatId, rollcall.tagAllMessageId);
                telegramAPI.deleteMessage(userId, rollcall.resultMessageId);
                telegramAPI.sendError(chatId, threadId, "❌ Не удалось отправить сообщение переклички");
                return;
            }
            rollcall.setRollcallMessageId(rollcallMessage.getMessageId());
            telegramAPI.editMessageReplyMarkup(chatId, rollcall.rollcallMessageId, getRollcallInline(chat, rollcall));
            telegramAPI.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, students));
            addRollcall(chat, rollcall);
        } catch (Exception exception) {
            telegramAPI.sendError(chatId, threadId, "❌ При запуске переклички произошла ошибка:\n" + exception.getMessage());
        }
    }

    public void stop(Chat chat, Message message, int threadId) {
        Rollcall rollcall = getRollcallByThread(chat, threadId);
        if (rollcall == null) {
            telegramAPI.sendError(chat.chatId, threadId, "❌ В этом чате нет активной переклички");
            return;
        }
        telegramAPI.deleteMessage(chat.chatId, message.getMessageId());
        finishRollcall(chat, rollcall);
    }

    public void tagIgnored(Chat chat, Message message, int threadId) {
        Rollcall rollcall = getRollcallByThread(chat, threadId);
        if (rollcall == null) return;
        telegramAPI.deleteMessage(chat.chatId, message.getMessageId());
        Message ignoreMessage = telegramAPI.sendMessage(chat.chatId, threadId, tag(rollcall.getStudents(RollcallAnswer.IGNORE)) + "\n\n⚠ Не забудьте сделать выбор выше, иначе Вам проставят отсутствие...");
        if (ignoreMessage != null) {
            scheduler.schedule(() -> telegramAPI.deleteMessage(chat.chatId, ignoreMessage.getMessageId()), 120, TimeUnit.SECONDS);
        }
    }

    public void handleAnswer(Update update, String[] callDataArray) {
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        User user = update.getCallbackQuery().getFrom();
        Rollcall rollcall = getRollcallById(chatId, Integer.parseInt(callDataArray[1]));
        switch (callDataArray[2]) {
            case "here", "notherereason", "nothere" -> {
                if (rollcall == null) {
                    telegramAPI.answerInline(update, "Эта перекличка уже неактивна");
                    return;
                }
                RollcallEntry entry = rollcall.entries.stream().filter(it -> it.student.userId == user.getId()).findAny().orElse(null);
                if (entry == null) {
                    telegramAPI.answerInline(update, "Ты не зарегистрирован, обратись к старосте");
                    return;
                }
                YamlFile config = getChat(chatId).config;
                String path = "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".";
                entry.addTimes();
                setAndSave(config, path + "times", entry.times);
                RollcallAnswer answer = RollcallAnswer.getByName(callDataArray[2]);
                long now = System.currentTimeMillis();
                if (entry.answer == answer) {
                    telegramAPI.answerInline(update, "Ты уже выбрал этот вариант");
                    return;
                }
                if (entry.answer != RollcallAnswer.IGNORE && now - entry.answerTime < ANSWER_CHANGE_DELAY) {
                    long secondsLeft = (ANSWER_CHANGE_DELAY - (now - entry.answerTime)) / 1000 + 1;
                    telegramAPI.answerInline(update, "Сменить ответ можно раз в минуту, подожди ещё " + secondsLeft + " сек.");
                    return;
                }
                boolean changed = entry.answer != RollcallAnswer.IGNORE;
                entry.answer = answer;
                entry.answerTime = now;
                config.set(path + "answer", answer.name());
                setAndSave(config, path + "answerTime", now);
                telegramAPI.answerInline(update, changed ? "Ответ изменён" : "Спасибо за участие, уже передали ответ старосте.");
            }
            default -> {
                telegramAPI.answerInline(update, "Эта перекличка уже неактивна");
                logger.warn("Unhandled callback query {}", String.join(" ", callDataArray));
            }
        }
        telegramAPI.editMessageReplyMarkup(chatId, messageId, getRollcallInline(getChat(chatId), rollcall));
        telegramAPI.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, getChat(chatId).students));
    }
}
