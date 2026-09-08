package su.spyme.rollcallbot.handlers;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.Main;
import su.spyme.rollcallbot.objects.Student;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static su.spyme.rollcallbot.utils.StringUtils.instantToString;

class StudentHandlerTest extends HandlerTest {
    private static final String INVALID_FORMAT = "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг";
    private final StudentHandler handler = new StudentHandler();

    private Message command(String text, long replyFromId, boolean bot) {
        Message message = message(CHAT_ID, ADMIN_ID, text);
        when(message.getReplyToMessage().getFrom().getId()).thenReturn(replyFromId);
        when(message.getReplyToMessage().getFrom().getIsBot()).thenReturn(bot);
        return message;
    }

    private void handle(Message message) {
        handler.addOrUpdate(chat, message, 0, message.getText().split(" "));
    }

    @Test
    void addsStudentFromReply() throws IOException {
        handle(command(".с 15.05.2004 Сидоров Иван", 30L, false));

        assertEquals(3, chat.students.size());
        Student added = chat.students.get(2);
        assertEquals(30L, added.userId);
        assertEquals("Сидоров Иван", added.name);
        assertEquals("15.05.2004", instantToString(added.birthdate));
        assertEquals(3, Main.storage.load(CHAT_ID).students.size());
        verify(telegramAPI).sendMessage(CHAT_ID, 0, "Студент добавлен: Сидоров Иван (30).");
    }

    @Test
    void updatesExistingStudent() throws IOException {
        handle(command(".с 15.05.2004 Иванов Пётр", ivanov.userId, false));

        assertEquals(2, chat.students.size());
        assertEquals("Иванов Пётр", ivanov.name);
        assertEquals("15.05.2004", instantToString(ivanov.birthdate));
        assertEquals("Иванов Пётр", Main.storage.load(CHAT_ID).students.get(0).name);
        verify(telegramAPI).sendMessage(CHAT_ID, 0, "Студент обновлён: Иванов Пётр (10).");
    }

    @Test
    void rejectsBotAndMissingReply() {
        handle(command(".с 15.05.2004 Сидоров Иван", 30L, true));
        verify(telegramAPI).sendMessage(CHAT_ID, 0, "❌ Нужно ответить на сообщение студента, а не бота");

        Message noReply = message(CHAT_ID, ADMIN_ID, ".с 15.05.2004 Сидоров Иван");
        when(noReply.getReplyToMessage()).thenReturn(null);
        handle(noReply);
        verify(telegramAPI).sendError(CHAT_ID, 0, "❌ Команду нужно отправить ответом на сообщение студента");

        assertEquals(2, chat.students.size());
    }

    @Test
    void rejectsInvalidDateOrName() {
        handle(command(".с 31.02.2004 Сидоров Иван", 30L, false));
        handle(command(".с 15.05.2004 Сидоров", 30L, false));
        handle(command(".с Сидоров", 30L, false));

        assertEquals(2, chat.students.size());
        verify(telegramAPI, org.mockito.Mockito.times(3)).sendMessage(CHAT_ID, 0, INVALID_FORMAT);
    }
}
