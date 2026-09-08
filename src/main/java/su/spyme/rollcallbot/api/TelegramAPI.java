package su.spyme.rollcallbot.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllChatAdministrators;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.utils.MyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.Main.OWNER_ID;
import static su.spyme.rollcallbot.Main.telegramClient;

public class TelegramAPI {
    private static final Logger logger = LoggerFactory.getLogger(TelegramAPI.class);
    private static final long ADMINS_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(5);
    private final Map<Long, Long> adminsRefreshed = new HashMap<>();

    public void setBotCommands() {
        try {
            List<BotCommand> privateCommands = new ArrayList<>();
            privateCommands.add(new BotCommand("settings", "Показать меню настроек"));
            privateCommands.add(new BotCommand("help", "Показать меню помощи"));
            telegramClient.execute(SetMyCommands.builder()
                    .commands(privateCommands)
                    .scope(BotCommandScopeAllPrivateChats.builder().build())
                    .build());

            List<BotCommand> adminCommands = new ArrayList<>();
            adminCommands.add(new BotCommand("help", "Показать меню помощи"));
            adminCommands.add(new BotCommand("rollcall", "Начать перекличку"));
            adminCommands.add(new BotCommand("rollcallstop", "Завершить перекличку принудительно"));
            adminCommands.add(new BotCommand("all", "Упомянуть всех студентов в чате"));
            adminCommands.add(new BotCommand("ignore", "Упомянуть тех, кто не ответил"));
            adminCommands.add(new BotCommand("student", "Добавить студента по сообщению-ответу (<дата рождения> <Фамилия Имя>)"));

            telegramClient.execute(SetMyCommands.builder()
                    .commands(adminCommands)
                    .scope(BotCommandScopeAllChatAdministrators.builder().build())
                    .build());
        } catch (TelegramApiException ex) {
            logger.error("Error while setBotCommands()", ex);
        }
    }

    public Message sendMessage(long chatId, String text) {
        return sendMessage(chatId, 0, text);
    }

    public Message sendMessage(long chatId, int messageThreadId, String text) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            if (messageThreadId > 0) {
                sendMessage.setMessageThreadId(messageThreadId);
            }
            return telegramClient.execute(sendMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while sendMessage({}, {})", chatId, messageThreadId, ex);
        }
        return null;
    }

    public Message sendMessageInline(long chatId, InlineKeyboardMarkup inline, String text) {
        return sendMessageInline(chatId, 0, inline, text, "Markdown");
    }

    public Message sendMessageInline(long chatId, int messageThreadId, InlineKeyboardMarkup inline, String text) {
        return sendMessageInline(chatId, messageThreadId, inline, text, "Markdown");
    }

    public Message sendMessageInline(long chatId, int messageThreadId, InlineKeyboardMarkup inline, String text, String parseMode) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(inline)
                    .parseMode(parseMode)
                    .build();
            if (messageThreadId > 0) {
                sendMessage.setMessageThreadId(messageThreadId);
            }
            return telegramClient.execute(sendMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while sendMessageInline({}, {})", chatId, messageThreadId, ex);
        }
        return null;
    }

    public void sendError(long chatId, int threadId, String error) {
        sendMessageInline(
                chatId,
                threadId,
                InlineKeyboardMarkup.builder()
                        .keyboardRow(
                                new InlineKeyboardRow(InlineKeyboardButton
                                        .builder()
                                        .text("Сообщить разработчику - @SPY_mesu")
                                        .url("https://t.me/SPY_mesu")
                                        .build()
                                )
                        )
                        .build(),
                error + "\nУверен, что сделал всё правильно? Если да:\n"
        );
    }

    public void editMessageText(long chatId, int messageId, String text, InlineKeyboardMarkup inline) {
        try {
            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(inline)
                    .parseMode("Markdown")
                    .build();
            telegramClient.execute(editMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while editMessageText({}, {})", chatId, messageId, ex);
        }
    }

    public void editMessageReplyMarkup(long chatId, int messageId, InlineKeyboardMarkup inline) {
        try {
            EditMessageReplyMarkup editMessage = EditMessageReplyMarkup.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .replyMarkup(inline)
                    .build();
            telegramClient.execute(editMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while editMessageReplyMarkup({}, {})", chatId, messageId, ex);
        }
    }

    public void editMessageText(long chatId, int messageId, String text) {
        try {
            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            telegramClient.execute(editMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while editMessageText({}, {})", chatId, messageId, ex);
        }
    }

    public void deleteMessage(long chatId, int messageId) {
        try {
            DeleteMessage deleteMessage = DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build();
            telegramClient.execute(deleteMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while deleteMessage({}, {})", chatId, messageId, ex);
        }
    }

    public org.telegram.telegrambots.meta.api.objects.chat.Chat getChat(long chatId) {
        try {
            return telegramClient.execute(new GetChat(String.valueOf(chatId)));
        } catch (TelegramApiException ex) {
            logger.error("Error while getChat({})", chatId, ex);
        }
        return null;
    }

    public String getChatTitle(long chatId) {
        org.telegram.telegrambots.meta.api.objects.chat.Chat chat = getChat(chatId);
        return chat == null ? null : chat.getTitle();
    }

    public List<ChatMember> getChatAdministrators(long chatId) {
        List<ChatMember> chatAdministrators = Collections.emptyList();
        try {
            chatAdministrators = telegramClient.execute(new GetChatAdministrators(String.valueOf(chatId)));
        } catch (TelegramApiException ex) {
            logger.error("Error while getChatAdministrators({})", chatId, ex);
        }
        return chatAdministrators;
    }

    public boolean isAdmin(long chatId, long userId) {
        if (userId == OWNER_ID) return true;
        Chat chat = MyUtils.getChat(chatId);
        if (chat == null) return false;
        if (chat.admins.contains(userId)) return true;
        long now = System.currentTimeMillis();
        if (now - adminsRefreshed.getOrDefault(chatId, 0L) < ADMINS_REFRESH_INTERVAL) return false;
        adminsRefreshed.put(chatId, now);
        MyUtils.updateChatAdmins(chat);
        return chat.admins.contains(userId);
    }

    public void answerInline(Update update, String text) {
        try {
            AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                    .callbackQueryId(update.getCallbackQuery().getId())
                    .text(text)
                    .showAlert(false)
                    .build();
            telegramClient.execute(answerCallbackQuery);
        } catch (TelegramApiException ignored) {
        }
    }
}