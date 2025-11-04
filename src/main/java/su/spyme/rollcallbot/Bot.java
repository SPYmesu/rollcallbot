package su.spyme.rollcallbot;

import org.simpleyaml.configuration.file.YamlFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import su.spyme.rollcallbot.objects.*;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static su.spyme.rollcallbot.Main.*;
import static su.spyme.rollcallbot.utils.ConfigUtils.loadConfig;
import static su.spyme.rollcallbot.utils.ConfigUtils.setAndSave;
import static su.spyme.rollcallbot.utils.MyUtils.*;
import static su.spyme.rollcallbot.utils.StringUtils.*;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            String[] callDataArray = callData.split(" ");
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            User user = update.getCallbackQuery().getFrom();
            Rollcall rollcall = getRollcallById(chatId, Integer.parseInt(callDataArray[0]));
            switch (callDataArray[1]) {
                case "here", "notherereason", "nothere" -> {
                    if (rollcall == null) {
                        answerInline(update, "Эта перекличка уже неактивна");
                        return;
                    }
                    RollcallEntry entry = rollcall.entries.stream().filter(it -> it.student.userId == user.getId()).findAny().orElse(null);
                    if (entry == null) {
                        answerInline(update, "Ты не зарегистрирован, обратись к старосте");
                        return;
                    }
                    if (entry.answer != RollcallAnswer.IGNORE) {
                        answerInline(update, "Ты уже сделал свой выбор...");
                        entry.addTimes();
                        setAndSave(getChat(chatId).config, "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".times", entry.times);
                        return;
                    }
                    RollcallAnswer answer = RollcallAnswer.getByName(callDataArray[1]);
                    rollcall.entries.remove(entry);
                    entry.answer = answer;
                    rollcall.entries.add(entry);
                    setAndSave(getChat(chatId).config, "rollcalls." + rollcall.rollcallMessageId + ".entries." + entry.student.userId + ".answer", answer.name());
                    answerInline(update, "Спасибо за участие, уже передали ответ старосте.");
                }
                default -> {
                    answerInline(update, "Эта перекличка уже неактивна");
                    logger.warn("Unhandled callback query {}", callData);
                }
            }
            telegramAPI.editMessageReplyMarkup(chatId, messageId, getRollcallInline(getChat(chatId), rollcall));
            telegramAPI.editMessageText(rollcall.resultChatId, rollcall.resultMessageId, getRollcallResult(rollcall, getChat(chatId).students));
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String body = update.getMessage().getText();
            String[] args = body.split(" ");
            long chatId = update.getMessage().getChatId();
            int threadId = (update.getMessage().getChat().getIsForum() != null && update.getMessage().getChat().getIsForum() && update.getMessage().getMessageThreadId() != null) ? update.getMessage().getMessageThreadId() : 0;
            long userId = update.getMessage().getFrom().getId();
            Chat chat = getChat(chatId);
            List<Student> students = chat.students;
            String command = args[0].toLowerCase().replaceFirst("/", "").replaceFirst(".", "");
            if (command.contains("@")) {
                command = command.substring(0, command.indexOf('@'));
            }
            switch (command) {
                case "all", "позвать", "все" -> {
                    if (!isAdmin(update)) return;
                    telegramAPI.sendMessage(chatId, threadId, tag(students));
                }
                case "rollcall", "перекличка", "п" -> {
                    try {
                        if (!isAdmin(update)) return;
                        if (getRollcallByThread(chat, threadId) != null) {
                            telegramAPI.sendMessage(chatId, threadId, "В этом чате уже активна перекличка... \nСначала заверши её (`.пв`)");
                            return;
                        }
                        String text = chat.settings.message;
                        if (args.length > 1) {
                            text = getArguments(1, args);
                        }
                        List<RollcallEntry> entries = new ArrayList<>();
                        for (Student student : students) {
                            entries.add(new RollcallEntry(student, RollcallAnswer.IGNORE, 0));
                        }
                        Rollcall rollcall = new Rollcall(chatId, threadId, 0, 0, 0L, 0, text, entries);
                        rollcall.setResultChatId(userId);
                        Message resultMessage = telegramAPI.sendMessage(userId, 0, getRollcallResult(rollcall, students));
                        if (resultMessage == null) {
                            telegramAPI.sendMessage(chatId, threadId, "❌ Не удалось отправить сообщение с результатом переклички, провертье, может ли бот вам писать в личные сообщения.");
                            return;
                        }
                        rollcall.setResultMessageId(resultMessage.getMessageId());
                        telegramAPI.deleteMessage(chatId, update.getMessage().getMessageId());
                        Message tagAllMessage = telegramAPI.sendMessage(chatId, threadId, tag(students));
                        if (tagAllMessage == null) {
                            sendError(chatId, threadId, "❌ Не удалось отправить сообщение с упоминанием студентов");
                            return;
                        }
                        rollcall.setTagAllMessageId(tagAllMessage.getMessageId());
                        Message rollcallMessage = telegramAPI.sendMessageInline(chatId, threadId, getRollcallInline(chat, rollcall), rollcall.text);
                        if (rollcallMessage == null) {
                            sendError(chatId, threadId, "❌ Не удалось отправить сообщение переклички");
                            return;
                        }
                        rollcall.setRollcallMessageId(rollcallMessage.getMessageId());
                        telegramAPI.editMessageReplyMarkup(chatId, rollcall.rollcallMessageId, getRollcallInline(chat, rollcall));
                        addRollcall(chat, rollcall);
                    } catch (Exception exception) {
                        sendError(chatId, threadId, "❌ При запуске переклички произошла ошибка:\n" + exception.getMessage());
                    }
                }
                case "rollcallstop", "перекличкавсё", "пв" -> {
                    if (!isAdmin(update)) return;
                    Rollcall rollcall = getRollcallByThread(chat, threadId);
                    if (rollcall == null) {
                        sendError(chatId, threadId, "recall == null;");
                        return;
                    }
                    telegramAPI.deleteMessage(chatId, rollcall.rollcallMessageId);
                    telegramAPI.deleteMessage(chatId, rollcall.tagAllMessageId);
                    telegramAPI.deleteMessage(chatId, update.getMessage().getMessageId());
                    removeRollcall(chat, rollcall);
                    StringBuilder text = new StringBuilder("Перекличка `#" + rollcall.rollcallMessageId + "` завершена");

                    RollcallEntry best = rollcall.entries.getFirst();
                    for (RollcallEntry entry : rollcall.entries) {
                        if (entry.times > best.times) best = entry;
                    }
                    if (best.times > 5)
                        text.append("\n\nИнтересный факт: ").append(best.student.name).append(" кликнул на кнопку ").append(best.times).append(" раз!");
                    telegramAPI.sendMessage(chatId, threadId, text.toString());
                }
                case "student", "студент", "с" -> {
                    if (!isAdmin(update)) return;
                    if (update.getMessage().getReplyToMessage() != null) {
                        long targetId = update.getMessage().getReplyToMessage().getFrom().getId();
                        String targetName = getArguments(2, args);
                        Instant instant = null;
                        try {
                            instant = new SimpleDateFormat("dd.MM.yyyy").parse(args[1]).toInstant();
                        } catch (Exception ignored) {
                            telegramAPI.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
                        }
                        if (targetName.split(" ").length < 2 || instant == null) {
                            telegramAPI.sendMessage(chatId, threadId, "Нужно указать фамилию и имя студента, а так же дату его рождения в формате дд.ММ.гггг");
                            return;
                        }

                        try {
                            Student student = new Student(targetId, targetName, instant);
                            chat.config.set("students." + targetId + ".name", student.name);
                            chat.config.set("students." + targetId + ".birthdate", instantToString(student.birthdate));
                            chat.config.save();
                            students.add(student);
                            telegramAPI.sendMessage(chatId, threadId, "Студент добавлен: " + targetName + " (" + targetId + ").");
                        } catch (Exception exception) {
                            telegramAPI.sendMessage(chatId, threadId, "❌ При выполнении команды произошла ошибка: " + exception.getMessage());
                        }
                    } else {
                        sendError(chatId, threadId, "Message.getForwardFrom() == null;");
                    }
                }
                case "ignore", "игнор" -> {
                    if (!isAdmin(update)) return;
                    Rollcall rollcall = getRollcallByThread(chat, threadId);
                    if (rollcall != null) {
                        telegramAPI.deleteMessage(chatId, update.getMessage().getMessageId());
                        int ignoreMessageId = telegramAPI.sendMessage(chatId, threadId, tag(rollcall.getStudents(RollcallAnswer.IGNORE)) + "\n\nНе забудьте сделать выбор выше, иначе Вам проставят отсутствие...").getMessageId();
                        Executors.newSingleThreadScheduledExecutor().schedule(() -> telegramAPI.deleteMessage(chatId, ignoreMessageId), 120, TimeUnit.SECONDS);
                    }
                }
                case "help", "помощь" -> {
                    if (!update.getMessage().isUserMessage() && !isAdmin(update)) return;
                    telegramAPI.sendMessage(chatId, threadId, """
                            Помощь по командам:
                            
                            .перекличка (.п) `<свой текст сообщения>` - начать перекличку `<если указано, то с этим текстом>`
                            *Так же эта команда автоматически выполняет следующую*
                            
                            .позвать (.все) - упоминает всех добавленных студентов
                            
                            .игнор - упоминает только тех, кто ещё не участвовал в перекличке
                            *Сообщение само удалится через 120 секунд*
                            
                            .перекличкавсё (.пв) - заканчивает перекличку, удаляет сообщение с опросом
                            
                            .студент (.с) `<Дата рожения 11.11.2011>` `<Фамилия Имя>` - добавляет студента с указанными данными
                            
                            Сообщить об ошибке: https://github.com/SPY\\_mesu/rollcallbot/issues
                            Исходный код: https://github.com/SPY\\_mesu/rollcallbot
                            Поддержать разработчика: https://boosty.to/SPY\\_me/about
                            """);
                }
                default -> logger.warn("Unhandled command: {}", command);
            }
        }
    }

    private String getRollcallResult(Rollcall rollcall, List<Student> sortExample) {
        List<Student> here = rollcall.getStudents(RollcallAnswer.HERE);
        here.sort(Comparator.comparingInt(sortExample::indexOf));
        List<Student> notHere = rollcall.getStudents(RollcallAnswer.NOTHERE);
        notHere.sort(Comparator.comparingInt(sortExample::indexOf));
        List<Student> notHereReason = rollcall.getStudents(RollcallAnswer.NOTHEREREASON);
        notHereReason.sort(Comparator.comparingInt(sortExample::indexOf));
        List<Student> ignore = rollcall.getStudents(RollcallAnswer.IGNORE);
        StringBuilder builder = new StringBuilder("Результат переклички. `#" + rollcall.rollcallMessageId + "`");
        builder.append("\n\n");
        builder.append("На паре: (").append(here.size()).append(")");
        for (Student student : here) {
            builder.append("\n").append(student.name);
        }
        int notHereSize = notHere.size() + notHereReason.size();
        if (notHereSize > 0) {
            builder.append("\n");
            builder.append("\nНе на паре: (").append(notHereSize).append(")");
            for (Student student : notHereReason) {
                builder.append("\n").append(student.name).append(" (по ув. причине)");
            }
            for (Student student : notHere) {
                builder.append("\n").append(student.name);
            }
        }
        if (!ignore.isEmpty()) {
            builder.append("\n");
            builder.append("\nПроигнорировали: (").append(ignore.size()).append(")");
            for (Student student : ignore) {
                builder.append("\n").append(student.name);
            }
        }
        return builder.toString();
    }

    private void answerInline(Update update, String text) {
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

    public static void checkBirthdays() {
        LocalDate today = LocalDate.now();
        try {
            for (Chat chat : chats) {
                if (!chat.settings.birthdays) continue;
                YamlFile chatConfig = loadConfig(String.valueOf(chat.chatId));
                for (Student student : chat.students) {
                    String s = chatConfig.getString("birthdays." + student.userId);
                    LocalDate lastBirthday;
                    if (s == null) lastBirthday = null;
                    else lastBirthday = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
                    if (isBirthdayToday(student, today) && lastBirthday != today) {
                        String message = "🎉 С днем рождения, " + format(student) + "! 🎂";
                        telegramAPI.sendMessage(chat.chatId, 0, message);
                        chatConfig.set("birthdays." + student.userId, today);
                        chatConfig.save();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isBirthdayToday(Student student, LocalDate today) {
        LocalDate birthDate = student.birthdate.atZone(ZoneId.systemDefault()).toLocalDate();
        return birthDate.getMonth() == today.getMonth() &&
                birthDate.getDayOfMonth() == today.getDayOfMonth();
    }


    public boolean isAdmin(Update update) {
        if (update.getMessage().isUserMessage()) return false;
        long userId = update.getMessage().getFrom().getId();
        return telegramAPI.getChatAdministrators(update.getMessage().getChatId()).stream().anyMatch(it -> it.getUser().getId() == userId) || userId == 453460175L;
    }

    private void sendError(long chatId, int threadId, String error) {
        telegramAPI.sendMessageInline(chatId, threadId, getDevLink(), error + "\nУверен, что сделал всё правильно? Если да:\n");
    }

    private InlineKeyboardMarkup getDevLink() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(
                        new InlineKeyboardRow(InlineKeyboardButton
                                .builder()
                                .text("Сообщить разработчику - @SPY_mesu")
                                .url("https://t.me/SPY_mesu")
                                .build()
                        )
                )
                .build();
    }

    private InlineKeyboardMarkup getRollcallInline(Chat chat, Rollcall rollcall) {
        List<String> buttons = chat.settings.buttonNames;
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(getInlineButton(buttons.get(0) + " (" + rollcall.getCount(RollcallAnswer.HERE) + ")", rollcall.rollcallMessageId + " here")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(buttons.get(1) + " (" + rollcall.getCount(RollcallAnswer.NOTHEREREASON) + ")", rollcall.rollcallMessageId + " notherereason")))
                .keyboardRow(new InlineKeyboardRow(getInlineButton(buttons.get(2) + " (" + rollcall.getCount(RollcallAnswer.NOTHERE) + ")", rollcall.rollcallMessageId + " nothere")))
                .build();
    }

    private InlineKeyboardButton getInlineButton(String text, String callback) {
        return InlineKeyboardButton
                .builder()
                .text(text)
                .callbackData(callback)
                .switchInlineQueryCurrentChat(callback)
                .build();
    }
}
