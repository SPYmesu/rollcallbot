package su.spyme.rollcallbot.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static su.spyme.rollcallbot.utils.MyUtils.hasBirthdate;
import static su.spyme.rollcallbot.utils.StringUtils.instantToString;
import static su.spyme.rollcallbot.utils.StringUtils.parseDate;

class ChatStorageTest {

    @TempDir
    Path dir;

    @Test
    void savesAndLoadsChat() throws IOException {
        ChatStorage storage = new ChatStorage(dir);
        Student ivanov = new Student(1, "Иванов Иван", parseDate("15.05.2004"));
        Student petrov = new Student(2, "Петров_Пётр", Instant.EPOCH);
        Map<RollcallAnswer, String> buttons = new EnumMap<>(RollcallAnswer.class);
        buttons.put(RollcallAnswer.HERE, "Тут");
        ChatSettings settings = new ChatSettings(45, "Перекличка\nвторая строка", buttons, false);
        List<RollcallEntry> entries = new ArrayList<>(List.of(
                new RollcallEntry(ivanov, RollcallAnswer.HERE, 3, 1762800400000L),
                new RollcallEntry(petrov, RollcallAnswer.IGNORE, 0, 0)
        ));
        Rollcall rollcall = new Rollcall(-100L, 7, 676, 675, 1L, 51, "Текст", 1762800331176L, entries);
        Chat chat = new Chat(-100L, "Группа 🙋", new ArrayList<>(), settings, new ArrayList<>(List.of(ivanov, petrov)), new CopyOnWriteArrayList<>(List.of(rollcall)));

        storage.save(chat);
        Chat loaded = storage.load(-100L);

        assertEquals("Группа 🙋", loaded.name);
        assertEquals(45, loaded.settings.timer);
        assertEquals("Перекличка\nвторая строка", loaded.settings.message);
        assertFalse(loaded.settings.birthdays);
        assertEquals("Тут", loaded.settings.getButton(RollcallAnswer.HERE));
        assertEquals(RollcallAnswer.NOTHERE.defaultButton, loaded.settings.getButton(RollcallAnswer.NOTHERE));

        assertEquals(List.of(1L, 2L), loaded.students.stream().map(it -> it.userId).toList());
        assertEquals("Иванов Иван", loaded.students.get(0).name);
        assertEquals("15.05.2004", instantToString(loaded.students.get(0).birthdate));
        assertFalse(hasBirthdate(loaded.students.get(1)));

        assertEquals(1, loaded.rollcalls.size());
        Rollcall loadedRollcall = loaded.rollcalls.get(0);
        assertEquals(676, loadedRollcall.rollcallMessageId);
        assertEquals(7, loadedRollcall.threadId);
        assertEquals(675, loadedRollcall.tagAllMessageId);
        assertEquals(1L, loadedRollcall.resultChatId);
        assertEquals(51, loadedRollcall.resultMessageId);
        assertEquals("Текст", loadedRollcall.text);
        assertEquals(1762800331176L, loadedRollcall.startTime);
        assertEquals(2, loadedRollcall.entries.size());
        assertSame(loaded.students.get(0), loadedRollcall.entries.get(0).student);
        assertEquals(RollcallAnswer.HERE, loadedRollcall.entries.get(0).answer);
        assertEquals(3, loadedRollcall.entries.get(0).times);
        assertEquals(1762800400000L, loadedRollcall.entries.get(0).answerTime);

        String yaml = Files.readString(dir.resolve("-100.yml"));
        assertTrue(yaml.contains("Иванов Иван"));
        assertFalse(yaml.startsWith("---"));
        assertFalse(Files.exists(dir.resolve("-100.yml.tmp")));
    }

    @Test
    void loadsLegacyFormat() throws IOException {
        Files.writeString(dir.resolve("config.yml"), """
                chats:
                  - '-1001750454869'
                  - '-1002582842996'
                """);
        Files.writeString(dir.resolve("-1001750454869.yml"), """
                students:
                  '453460175': Краюшкин Антон
                  '1217941962':
                    name: Козлов Кирилл
                    birthdate: 01.01.1970
                rollcalls:
                  '676':
                    threadId: 0
                    tagAllMessageId: 675
                    resultChatId: 453460175
                    resultMessageId: 51
                    text: Перекличка
                    startTime: 1762800331176
                    entries:
                      '453460175':
                        answer: HERE
                        times: 0
                      '999':
                        answer: NOTHERE
                        times: 1
                settings:
                  timer: 60
                  message: Перекличка
                  buttonNames:
                    - Тут
                    - Болею
                    - Нет
                  birthdays: true
                name: test
                """);
        ChatStorage storage = new ChatStorage(dir);

        assertEquals(List.of(-1001750454869L, -1002582842996L), storage.loadChatIds());
        Chat chat = storage.load(-1001750454869L);

        assertEquals("test", chat.name);
        assertEquals(2, chat.students.size());
        assertEquals("Краюшкин Антон", chat.students.get(0).name);
        assertFalse(hasBirthdate(chat.students.get(0)));
        assertEquals("Тут", chat.settings.getButton(RollcallAnswer.HERE));
        assertEquals("Болею", chat.settings.getButton(RollcallAnswer.NOTHEREREASON));
        assertEquals("Нет", chat.settings.getButton(RollcallAnswer.NOTHERE));
        assertEquals(1, chat.rollcalls.size());
        assertEquals(1, chat.rollcalls.get(0).entries.size());
        assertEquals(0, chat.rollcalls.get(0).entries.get(0).answerTime);
    }

    @Test
    void missingFilesGiveDefaults() throws IOException {
        ChatStorage storage = new ChatStorage(dir);

        assertTrue(storage.loadChatIds().isEmpty());
        Chat chat = storage.load(42);
        assertEquals("", chat.name);
        assertEquals(ChatSettings.DEFAULT_TIMER, chat.settings.timer);
        assertEquals(ChatSettings.DEFAULT_MESSAGE, chat.settings.message);
        assertTrue(chat.settings.birthdays);
        assertTrue(chat.students.isEmpty());
        assertTrue(chat.rollcalls.isEmpty());

        storage.saveChatIds(List.of(42L));
        assertEquals(List.of(42L), storage.loadChatIds());
    }
}
