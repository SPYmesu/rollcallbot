package su.spyme.rollcallbot.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.Main;
import su.spyme.rollcallbot.api.TelegramAPI;
import su.spyme.rollcallbot.objects.*;
import su.spyme.rollcallbot.storage.ChatStorage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.Mockito.*;

abstract class HandlerTest {
    static final long CHAT_ID = -100L;
    static final long ADMIN_ID = 1L;

    @TempDir
    Path dir;
    TelegramAPI telegramAPI;
    Chat chat;
    Student ivanov = new Student(10L, "Иванов Иван", Instant.EPOCH);
    Student petrov = new Student(20L, "Петров Пётр", Instant.EPOCH);

    @BeforeEach
    void setUpMain() {
        telegramAPI = mock(TelegramAPI.class);
        Main.telegramAPI = telegramAPI;
        Main.storage = new ChatStorage(dir);
        Main.chats = new CopyOnWriteArrayList<>();
        ChatSettings settings = new ChatSettings(ChatSettings.DEFAULT_TIMER, ChatSettings.DEFAULT_MESSAGE, new EnumMap<>(RollcallAnswer.class), true);
        chat = new Chat(CHAT_ID, "Группа", new ArrayList<>(List.of(ADMIN_ID)), settings, new ArrayList<>(List.of(ivanov, petrov)), new CopyOnWriteArrayList<>());
        Main.chats.add(chat);
    }

    static Message message(long chatId, long fromId, String text) {
        Message message = mock(Message.class, RETURNS_DEEP_STUBS);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom().getId()).thenReturn(fromId);
        when(message.getText()).thenReturn(text);
        return message;
    }

    static Update callback(long chatId, int messageId, long fromId) {
        Update update = mock(Update.class, RETURNS_DEEP_STUBS);
        when(update.getCallbackQuery().getMessage().getMessageId()).thenReturn(messageId);
        when(update.getCallbackQuery().getMessage().getChatId()).thenReturn(chatId);
        when(update.getCallbackQuery().getFrom().getId()).thenReturn(fromId);
        return update;
    }
}
