package su.spyme.rollcallbot.api;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import su.spyme.rollcallbot.Bot;

import java.util.Collections;
import java.util.List;

public class TelegramBot implements LongPollingSingleThreadUpdateConsumer {

    public Bot bot;
    public TelegramClient telegramClient;

    public void start(){
        telegramClient = new OkHttpTelegramClient(getBotToken());
        bot = new Bot();
        bot.start(this);
    }

    @Override
    public void consume(Update update) {
        try {
            bot.onUpdateReceived(update);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getBotToken(){
        return System.getenv("rollcall_bot_token");
    }

    public Message sendMessage(long chatId, int messageThreadId, String text){
        try{
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            if (messageThreadId > 0){
                sendMessage.setMessageThreadId(messageThreadId);
            }
            return telegramClient.execute(sendMessage);
        }catch(TelegramApiException ex){
            System.out.println("Error while sendMessage(" + chatId + ", " + messageThreadId + ")");
            ex.printStackTrace();
        }
        return null;
    }

    public Message sendMessageInline(long chatId,  int messageThreadId, InlineKeyboardMarkup inline, String text){
        return sendMessageInline(chatId, messageThreadId, inline, text, "Markdown");
    }

    public Message sendMessageInline(long chatId, int messageThreadId, InlineKeyboardMarkup inline, String text, String parseMode){
        try{
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(inline)
                    .parseMode(parseMode)
                    .build();
            if (messageThreadId > 0){
                sendMessage.setMessageThreadId(messageThreadId);
            }
            return telegramClient.execute(sendMessage);
        }catch(TelegramApiException ex){
            System.out.println("Error while sendMessageInline(" + chatId + ", " + messageThreadId + ")");
            ex.printStackTrace();
        }
        return null;
    }

    public void editMessageReplyMarkup(long chatId, int messageId, InlineKeyboardMarkup  inline){
        try{
            EditMessageReplyMarkup editMessage = EditMessageReplyMarkup.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .replyMarkup(inline)
                    .build();
            telegramClient.execute(editMessage);
        }catch(TelegramApiException ex){
            System.out.println("Error while editMessageReplyMarkup(" + chatId + ", " + messageId + ")");
            ex.printStackTrace();
        }
    }

    public void editMessageText(long chatId, int messageId, String text){
        try{
            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            telegramClient.execute(editMessage);
        }catch(TelegramApiException ex){
            System.out.println("Error while editMessageText(" + chatId + ", " + messageId + ")");
            ex.printStackTrace();
        }
    }

    public void deleteMessage(long chatId, int messageId){
        try{
            DeleteMessage deleteMessage = DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build();
            telegramClient.execute(deleteMessage);
        }catch(TelegramApiException ex){
            System.out.println("Error while deleteMessage(" + chatId + ", " + messageId + ")");
            ex.printStackTrace();
        }
    }

    public List<ChatMember> getChatAdministrators(long chatId){
        List<ChatMember> chatAdministrators = Collections.emptyList();
        try {
            chatAdministrators = telegramClient.execute(new GetChatAdministrators(String.valueOf(chatId)));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return chatAdministrators;
    }
}