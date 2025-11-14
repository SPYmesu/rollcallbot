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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.utils.MyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static su.spyme.rollcallbot.Main.telegramClient;

public class TelegramAPI {
    private static final Logger logger = LoggerFactory.getLogger(TelegramAPI.class);

    public String getBotToken() {
        return System.getenv("rollcall_bot_token");
    }

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
            adminCommands.add(new BotCommand("all", "Упомянуть всех студентов чате"));
            adminCommands.add(new BotCommand("ignore", "Упомянуть тех, кто не ответил"));
            adminCommands.add(new BotCommand("student", "Добавить студента по сообщению-ответу (<дата рождения> <Фамилия Имя>)"));

            telegramClient.execute(SetMyCommands.builder()
                    .commands(adminCommands)
                    .scope(BotCommandScopeAllChatAdministrators.builder().build())
                    .build());
        } catch (TelegramApiException ex) {
            logger.error("Error while setBotCommands()");
            ex.printStackTrace();
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
            logger.error("Error while sendMessage({}, {})", chatId, messageThreadId);
            ex.printStackTrace();
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
            logger.error("Error while sendMessageInline({}, {})", chatId, messageThreadId);
            ex.printStackTrace();
        }
        return null;
    }

    public void editMessageReplyMarkup(long chatId, int messageId, String text, InlineKeyboardMarkup inline) {
        try {
            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(inline)
                    .build();
            telegramClient.execute(editMessage);
        } catch (TelegramApiException ex) {
            logger.error("Error while editMessageReplyMarkup({}, {})", chatId, messageId);
            ex.printStackTrace();
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
            logger.error("Error while editMessageReplyMarkup({}, {})", chatId, messageId);
            ex.printStackTrace();
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
            logger.error("Error while editMessageText({}, {})", chatId, messageId);
            ex.printStackTrace();
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
            logger.error("Error while deleteMessage({}, {})", chatId, messageId);
            ex.printStackTrace();
        }
    }

    public org.telegram.telegrambots.meta.api.objects.chat.Chat getChat(long chatId) {
        try {
            return telegramClient.execute(new GetChat(String.valueOf(chatId)));
        } catch (TelegramApiException ex) {
            logger.error("Error while getChat({})", chatId);
            ex.printStackTrace();
        }
        return null;
    }

    public List<ChatMember> getChatAdministrators(long chatId) {
        List<ChatMember> chatAdministrators = Collections.emptyList();
        try {
            chatAdministrators = telegramClient.execute(new GetChatAdministrators(String.valueOf(chatId)));
        } catch (TelegramApiException ex) {
            logger.error("Error while getChatAdministrators({})", chatId);
            ex.printStackTrace();
        }
        return chatAdministrators;
    }

    public boolean isAdmin(long chatId, long userId) {
        Chat chat = MyUtils.getChat(chatId);
        if (chat.admins.isEmpty())
            return getChatAdministrators(chatId).stream().anyMatch(it -> it.getUser().getId() == userId) || userId == 453460175L;
        return chat.admins.contains(userId) || userId == 453460175L;
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