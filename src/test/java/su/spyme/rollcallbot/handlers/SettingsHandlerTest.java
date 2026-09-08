package su.spyme.rollcallbot.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.Main;
import su.spyme.rollcallbot.objects.ChatSettings;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingsHandlerTest extends HandlerTest {
    private static final int MENU_ID = 400;
    private static final int PROMPT_ID = 500;
    private final SettingsHandler handler = new SettingsHandler();

    @BeforeEach
    void stubPrompt() {
        Message prompt = mock(Message.class);
        when(prompt.getMessageId()).thenReturn(PROMPT_ID);
        when(telegramAPI.sendMessage(anyLong(), anyString())).thenReturn(prompt);
    }

    @Test
    void timerInputIsValidatedAndSaved() throws IOException {
        handler.handleCallback(callback(ADMIN_ID, MENU_ID, ADMIN_ID), "settings -100 timer".split(" "));

        assertTrue(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "5")));
        assertEquals(ChatSettings.DEFAULT_TIMER, chat.settings.timer);
        verify(telegramAPI).sendMessage(eq(ADMIN_ID), startsWith("❗️ Нужно число"));

        assertTrue(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "45")));
        assertEquals(45, chat.settings.timer);
        assertEquals(45, Main.storage.load(CHAT_ID).settings.timer);
        verify(telegramAPI).deleteMessage(ADMIN_ID, PROMPT_ID);
        verify(telegramAPI).sendMessage(ADMIN_ID, "Настройка сохранена: 45");

        assertFalse(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "60")));
    }

    @Test
    void commandCancelsPendingInput() {
        handler.handleCallback(callback(ADMIN_ID, MENU_ID, ADMIN_ID), "settings -100 timer".split(" "));

        assertFalse(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "/help")));
        assertFalse(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "45")));
        assertEquals(ChatSettings.DEFAULT_TIMER, chat.settings.timer);
    }

    @Test
    void nonAdminCannotOpenSettings() {
        Update update = callback(99L, MENU_ID, 99L);

        handler.handleCallback(update, "settings -100 timer".split(" "));

        verify(telegramAPI).answerInline(update, "У вас нет прав на управление этим чатом");
        assertFalse(handler.handleInput(message(99L, 99L, "45")));
    }

    @Test
    void studentNameIsValidatedAndSaved() throws IOException {
        handler.handleCallback(callback(ADMIN_ID, MENU_ID, ADMIN_ID), "settings -100 student 10 name".split(" "));

        assertTrue(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "Иванов")));
        assertEquals("Иванов Иван", ivanov.name);

        assertTrue(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "Сидоров Иван")));
        assertEquals("Сидоров Иван", ivanov.name);
        assertEquals("Сидоров Иван", Main.storage.load(CHAT_ID).students.get(0).name);
        verify(telegramAPI).editMessageText(eq(ADMIN_ID), eq(MENU_ID), anyString(), any());
    }

    @Test
    void studentPositionIsMoved() throws IOException {
        handler.handleCallback(callback(ADMIN_ID, MENU_ID, ADMIN_ID), "settings -100 student 20 position".split(" "));

        assertTrue(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "3")));
        assertEquals(petrov, chat.students.get(1));

        assertTrue(handler.handleInput(message(ADMIN_ID, ADMIN_ID, "1")));
        assertEquals(petrov, chat.students.get(0));
        assertEquals(petrov.userId, Main.storage.load(CHAT_ID).students.get(0).userId);
    }
}
