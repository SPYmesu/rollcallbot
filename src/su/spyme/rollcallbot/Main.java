package su.spyme.rollcallbot;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import su.spyme.rollcallbot.api.TelegramBot;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            TelegramBot telegramBot = new TelegramBot();
            botsApplication.registerBot(telegramBot.getBotToken(), telegramBot);
            telegramBot.start();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
