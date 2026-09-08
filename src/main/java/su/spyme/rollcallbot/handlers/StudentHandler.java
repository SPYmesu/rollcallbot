package su.spyme.rollcallbot.handlers;

import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.objects.Student;

import java.time.Instant;
import java.util.List;

import static su.spyme.rollcallbot.Main.telegramAPI;
import static su.spyme.rollcallbot.utils.MyUtils.getStudent;
import static su.spyme.rollcallbot.utils.MyUtils.saveChat;
import static su.spyme.rollcallbot.utils.StringUtils.*;

public class StudentHandler {

    public void addOrUpdate(Chat chat, Message message, int threadId, String[] args) {
        long chatId = chat.chatId;
        List<Student> students = chat.students;
        if (message.getReplyToMessage() == null) {
            telegramAPI.sendError(chatId, threadId, "❌ Команду нужно отправить ответом на сообщение студента");
            return;
        }
        User target = message.getReplyToMessage().getFrom();
        if (Boolean.TRUE.equals(target.getIsBot())) {
            telegramAPI.sendMessage(chatId, threadId, "❌ Нужно ответить на сообщение студента, а не бота");
            return;
        }
        long targetId = target.getId();
        if (args.length < 3) {
            telegramAPI.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
            return;
        }
        String targetName = getArguments(2, args);
        Instant instant = null;
        try {
            instant = parseDate(args[1]);
        } catch (Exception ignored) {
        }
        if (!Student.isValidName(targetName) || instant == null) {
            telegramAPI.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
            return;
        }

        try {
            Student student = getStudent(students, targetId);
            if (student == null) {
                students.add(new Student(targetId, targetName, instant));
            } else {
                student.name = targetName;
                student.birthdate = instant;
            }
            saveChat(chat);
            telegramAPI.sendMessage(chatId, threadId, (student == null ? "Студент добавлен: " : "Студент обновлён: ") + escapeMarkdown(targetName) + " (" + targetId + ").");
        } catch (Exception exception) {
            telegramAPI.sendMessage(chatId, threadId, "❌ При выполнении команды произошла ошибка: " + exception.getMessage());
        }
    }
}
