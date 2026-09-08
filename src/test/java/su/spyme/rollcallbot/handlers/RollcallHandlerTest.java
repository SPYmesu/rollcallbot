package su.spyme.rollcallbot.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import su.spyme.rollcallbot.Main;
import su.spyme.rollcallbot.objects.Rollcall;
import su.spyme.rollcallbot.objects.RollcallAnswer;
import su.spyme.rollcallbot.objects.RollcallEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RollcallHandlerTest extends HandlerTest {
    private static final int ROLLCALL_ID = 676;
    private final RollcallHandler handler = new RollcallHandler();
    private Rollcall rollcall;

    @BeforeEach
    void setUpRollcall() {
        List<RollcallEntry> entries = new ArrayList<>(List.of(
                new RollcallEntry(ivanov, RollcallAnswer.IGNORE, 0, 0),
                new RollcallEntry(petrov, RollcallAnswer.IGNORE, 0, 0)
        ));
        rollcall = new Rollcall(CHAT_ID, 0, ROLLCALL_ID, 675, ADMIN_ID, 51, "Текст", System.currentTimeMillis(), entries);
        chat.rollcalls.add(rollcall);
    }

    @Test
    void firstAnswerIsRecordedAndSaved() throws IOException {
        Update update = callback(CHAT_ID, ROLLCALL_ID, ivanov.userId);

        handler.handleAnswer(update, "rollcall 676 here".split(" "));

        RollcallEntry entry = rollcall.entries.get(0);
        assertEquals(RollcallAnswer.HERE, entry.answer);
        assertEquals(1, entry.times);
        verify(telegramAPI).answerInline(update, "Спасибо за участие, уже передали ответ старосте.");
        verify(telegramAPI).editMessageReplyMarkup(eq(CHAT_ID), eq(ROLLCALL_ID), any());
        verify(telegramAPI).editMessageText(eq(ADMIN_ID), eq(51), anyString());
        assertEquals(RollcallAnswer.HERE, Main.storage.load(CHAT_ID).rollcalls.get(0).entries.get(0).answer);
    }

    @Test
    void answerCannotBeChangedWithinAMinute() {
        Update update = callback(CHAT_ID, ROLLCALL_ID, ivanov.userId);
        handler.handleAnswer(update, "rollcall 676 here".split(" "));
        clearInvocations(telegramAPI);

        handler.handleAnswer(update, "rollcall 676 nothere".split(" "));

        RollcallEntry entry = rollcall.entries.get(0);
        assertEquals(RollcallAnswer.HERE, entry.answer);
        assertEquals(2, entry.times);
        verify(telegramAPI).answerInline(eq(update), startsWith("Сменить ответ можно раз в минуту"));
        verify(telegramAPI, never()).editMessageReplyMarkup(anyLong(), anyInt(), any());
    }

    @Test
    void answerCanBeChangedAfterAMinute() {
        Update update = callback(CHAT_ID, ROLLCALL_ID, ivanov.userId);
        handler.handleAnswer(update, "rollcall 676 here".split(" "));
        rollcall.entries.get(0).answerTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(61);

        handler.handleAnswer(update, "rollcall 676 nothere".split(" "));

        assertEquals(RollcallAnswer.NOTHERE, rollcall.entries.get(0).answer);
        assertEquals(2, rollcall.entries.get(0).times);
        verify(telegramAPI).answerInline(update, "Ответ изменён");
    }

    @Test
    void sameAnswerOnlyCountsClicks() {
        Update update = callback(CHAT_ID, ROLLCALL_ID, ivanov.userId);
        handler.handleAnswer(update, "rollcall 676 here".split(" "));
        clearInvocations(telegramAPI);

        handler.handleAnswer(update, "rollcall 676 here".split(" "));

        assertEquals(RollcallAnswer.HERE, rollcall.entries.get(0).answer);
        assertEquals(2, rollcall.entries.get(0).times);
        verify(telegramAPI).answerInline(update, "Ты уже выбрал этот вариант");
        verify(telegramAPI, never()).editMessageText(anyLong(), anyInt(), anyString());
    }

    @Test
    void unknownStudentIsRejected() {
        Update update = callback(CHAT_ID, ROLLCALL_ID, 99L);

        handler.handleAnswer(update, "rollcall 676 here".split(" "));

        assertEquals(RollcallAnswer.IGNORE, rollcall.entries.get(0).answer);
        assertEquals(RollcallAnswer.IGNORE, rollcall.entries.get(1).answer);
        verify(telegramAPI).answerInline(update, "Ты не зарегистрирован, обратись к старосте");
    }

    @Test
    void inactiveRollcallIsRejected() {
        Update update = callback(CHAT_ID, 1, ivanov.userId);

        handler.handleAnswer(update, "rollcall 1 here".split(" "));

        verify(telegramAPI).answerInline(update, "Эта перекличка уже неактивна");
        verify(telegramAPI, never()).editMessageReplyMarkup(anyLong(), anyInt(), any());
    }
}
