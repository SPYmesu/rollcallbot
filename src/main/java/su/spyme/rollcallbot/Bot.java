package su.spyme.rollcallbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import su.spyme.rollcallbot.handlers.RollcallHandler;
import su.spyme.rollcallbot.handlers.SettingsHandler;
import su.spyme.rollcallbot.handlers.StudentHandler;
import su.spyme.rollcallbot.objects.Chat;

import static su.spyme.rollcallbot.Main.telegramAPI;
import static su.spyme.rollcallbot.utils.MyUtils.getChat;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);
    private static final String HELP = """
            Помощь по командам:

            .перекличка (.п) `<свой текст сообщения>` - начать перекличку `<если указано, то с этим текстом>`
            *Также эта команда автоматически выполняет следующую*

            .позвать (.все) - упоминает всех добавленных студентов

            .игнор - упоминает только тех, кто ещё не участвовал в перекличке
            *Сообщение само удалится через 120 секунд*

            .перекличкавсё (.пв) - заканчивает перекличку, удаляет сообщение с опросом

            .студент (.с) `<Дата рождения 11.11.2011>` `<Фамилия Имя>` - добавляет студента с указанными данными (ответом на его сообщение)

            .настройки - открывает меню настроек (только в личном чате с ботом)

            Сообщить об ошибке: https://github.com/SPYmesu/rollcallbot/issues
            Исходный код: https://github.com/SPYmesu/rollcallbot
            Поддержать разработчика: https://boosty.to/SPY\\_me/about
            """;

    private final RollcallHandler rollcallHandler = new RollcallHandler();
    private final StudentHandler studentHandler = new StudentHandler();
    private final SettingsHandler settingsHandler = new SettingsHandler();

    @Override
    public void consume(Update update) {
        try {
            handle(update);
        } catch (Exception exception) {
            logger.error("Error while handling update {}", update.getUpdateId(), exception);
        }
    }

    private void handle(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().isUserMessage()) handlePrivateMessage(update.getMessage());
            else handleGroupMessage(update.getMessage());
        }
    }

    private void handleCallback(Update update) {
        String callData = update.getCallbackQuery().getData();
        String[] callDataArray = callData.split(" ");
        switch (callDataArray[0]) {
            case "rollcall" -> rollcallHandler.handleAnswer(update, callDataArray);
            case "settings" -> settingsHandler.handleCallback(update, callDataArray);
            default -> logger.warn("Unhandled callback query {}", callData);
        }
    }

    private void handleGroupMessage(Message message) {
        String command = parseCommand(message.getText());
        if (command == null) return;
        Chat chat = getChat(message.getChatId());
        if (chat == null) return;
        if (!telegramAPI.isAdmin(chat.chatId, message.getFrom().getId())) return;
        int threadId = getThreadId(message);
        String[] args = message.getText().split(" ");
        switch (command) {
            case "all", "позвать", "все" -> rollcallHandler.tagAll(chat, threadId);
            case "rollcall", "перекличка", "п" -> rollcallHandler.start(chat, message, threadId, args);
            case "rollcallstop", "перекличкавсё", "пв" -> rollcallHandler.stop(chat, message, threadId);
            case "student", "студент", "с" -> studentHandler.addOrUpdate(chat, message, threadId, args);
            case "ignore", "игнор" -> rollcallHandler.tagIgnored(chat, message, threadId);
            case "help", "помощь" -> telegramAPI.sendMessage(chat.chatId, threadId, HELP);
            default -> logger.debug("Unhandled command: {}", command);
        }
    }

    private void handlePrivateMessage(Message message) {
        if (settingsHandler.handleInput(message)) return;
        String command = parseCommand(message.getText());
        if (command == null) return;
        switch (command) {
            case "settings", "настройки" -> settingsHandler.sendChatList(message);
            case "help", "помощь" -> telegramAPI.sendMessage(message.getChatId(), HELP);
            default -> logger.debug("Unhandled command: {}", command);
        }
    }

    private static String parseCommand(String text) {
        String command = text.split(" ")[0].toLowerCase().replaceFirst("^\\.", "/");
        if (!command.startsWith("/")) return null;
        command = command.substring(1);
        if (command.contains("@")) {
            command = command.substring(0, command.indexOf('@'));
        }
        return command.isEmpty() ? null : command;
    }

    private static int getThreadId(Message message) {
        boolean forum = message.getChat().getIsForum() != null && message.getChat().getIsForum();
        return forum && message.getMessageThreadId() != null ? message.getMessageThreadId() : 0;
    }
}
