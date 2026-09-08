package su.spyme.rollcallbot.storage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.spyme.rollcallbot.objects.*;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static su.spyme.rollcallbot.utils.StringUtils.instantToString;
import static su.spyme.rollcallbot.utils.StringUtils.parseDate;

public class ChatStorage {
    private static final Logger logger = LoggerFactory.getLogger(ChatStorage.class);
    private static final String NO_BIRTHDATE = "01.01.1970";
    private final Path dir;
    private final ObjectMapper mapper;

    record ConfigFile(List<Long> chats) {
    }

    record ChatFile(String name, SettingsFile settings, Map<Long, StudentFile> students, Map<Integer, RollcallFile> rollcalls) {
    }

    record SettingsFile(Integer timer, String message, Map<RollcallAnswer, String> buttons, Boolean birthdays) {
    }

    record StudentFile(String name, String birthdate) {
    }

    record RollcallFile(int threadId, int tagAllMessageId, long resultChatId, int resultMessageId, String text, long startTime, Map<Long, EntryFile> entries) {
    }

    record EntryFile(RollcallAnswer answer, int times, long answerTime) {
    }

    public ChatStorage(Path dir) {
        this.dir = dir;
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .disable(YAMLGenerator.Feature.SPLIT_LINES)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .build();
        this.mapper = new ObjectMapper(factory).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public List<Long> loadChatIds() throws IOException {
        Path file = dir.resolve("config.yml");
        if (!Files.exists(file)) return new ArrayList<>();
        ConfigFile config = mapper.treeToValue(readTree(file), ConfigFile.class);
        return config.chats() == null ? new ArrayList<>() : new ArrayList<>(config.chats());
    }

    public void saveChatIds(List<Long> chatIds) throws IOException {
        write(dir.resolve("config.yml"), new ConfigFile(chatIds));
    }

    public Chat load(long chatId) throws IOException {
        Path file = dir.resolve(chatId + ".yml");
        ChatFile data = Files.exists(file)
                ? mapper.treeToValue(normalize(readTree(file)), ChatFile.class)
                : new ChatFile(null, null, null, null);
        return toChat(chatId, data);
    }

    public void save(Chat chat) throws IOException {
        Map<RollcallAnswer, String> buttons = new EnumMap<>(RollcallAnswer.class);
        for (RollcallAnswer answer : RollcallAnswer.BUTTONS) {
            buttons.put(answer, chat.settings.getButton(answer));
        }
        SettingsFile settings = new SettingsFile(chat.settings.timer, chat.settings.message, buttons, chat.settings.birthdays);
        Map<Long, StudentFile> students = new LinkedHashMap<>();
        for (Student student : chat.students) {
            students.put(student.userId, new StudentFile(student.name, instantToString(student.birthdate)));
        }
        Map<Integer, RollcallFile> rollcalls = new LinkedHashMap<>();
        for (Rollcall rollcall : chat.rollcalls) {
            Map<Long, EntryFile> entries = new LinkedHashMap<>();
            for (RollcallEntry entry : rollcall.entries) {
                entries.put(entry.student.userId, new EntryFile(entry.answer, entry.times, entry.answerTime));
            }
            rollcalls.put(rollcall.rollcallMessageId, new RollcallFile(
                    rollcall.threadId, rollcall.tagAllMessageId, rollcall.resultChatId, rollcall.resultMessageId,
                    rollcall.text, rollcall.startTime, entries
            ));
        }
        write(dir.resolve(chat.chatId + ".yml"), new ChatFile(chat.name, settings, students, rollcalls));
    }

    private Chat toChat(long chatId, ChatFile data) {
        SettingsFile settingsFile = data.settings() == null ? new SettingsFile(null, null, null, null) : data.settings();
        Map<RollcallAnswer, String> buttons = new EnumMap<>(RollcallAnswer.class);
        if (settingsFile.buttons() != null) buttons.putAll(settingsFile.buttons());
        ChatSettings settings = new ChatSettings(
                settingsFile.timer() == null ? ChatSettings.DEFAULT_TIMER : settingsFile.timer(),
                settingsFile.message() == null ? ChatSettings.DEFAULT_MESSAGE : settingsFile.message(),
                buttons,
                settingsFile.birthdays() == null || settingsFile.birthdays()
        );

        List<Student> students = new ArrayList<>();
        if (data.students() != null) {
            data.students().forEach((userId, studentFile) -> students.add(new Student(
                    userId,
                    studentFile.name() == null ? String.valueOf(userId) : studentFile.name(),
                    parseBirthdate(userId, studentFile.birthdate())
            )));
        }

        List<Rollcall> rollcalls = new CopyOnWriteArrayList<>();
        if (data.rollcalls() != null) {
            data.rollcalls().forEach((messageId, rollcallFile) -> {
                List<RollcallEntry> entries = new ArrayList<>();
                if (rollcallFile.entries() != null) {
                    rollcallFile.entries().forEach((userId, entryFile) -> {
                        Student student = students.stream().filter(it -> it.userId == userId).findFirst().orElse(null);
                        if (student == null) {
                            logger.warn("Пропущена запись переклички {} из-за отсутствующего студента {}", messageId, userId);
                            return;
                        }
                        RollcallAnswer answer = entryFile.answer() == null ? RollcallAnswer.IGNORE : entryFile.answer();
                        entries.add(new RollcallEntry(student, answer, entryFile.times(), entryFile.answerTime()));
                    });
                }
                rollcalls.add(new Rollcall(
                        chatId, rollcallFile.threadId(), messageId, rollcallFile.tagAllMessageId(),
                        rollcallFile.resultChatId(), rollcallFile.resultMessageId(), rollcallFile.text(),
                        rollcallFile.startTime(), entries
                ));
            });
        }

        return new Chat(chatId, data.name() == null ? "" : data.name(), new ArrayList<>(), settings, students, rollcalls);
    }

    private Instant parseBirthdate(long userId, String birthdate) {
        if (birthdate == null) return Instant.EPOCH;
        try {
            return parseDate(birthdate);
        } catch (DateTimeParseException exception) {
            logger.warn("Некорректная дата рождения {} у студента {}", birthdate, userId);
            return Instant.EPOCH;
        }
    }

    private JsonNode readTree(Path file) throws IOException {
        JsonNode node = mapper.readTree(file.toFile());
        return node == null || node.isMissingNode() || node.isNull() ? mapper.createObjectNode() : node;
    }

    /**
     * Старые файлы: студент записан строкой с именем, кнопки списком buttonNames.
     */
    private JsonNode normalize(JsonNode root) {
        if (!root.isObject()) return mapper.createObjectNode();
        JsonNode students = root.get("students");
        if (students != null && students.isObject()) {
            for (Map.Entry<String, JsonNode> entry : students.properties()) {
                if (entry.getValue().isTextual()) {
                    ObjectNode student = mapper.createObjectNode();
                    student.put("name", entry.getValue().asText());
                    student.put("birthdate", NO_BIRTHDATE);
                    ((ObjectNode) students).set(entry.getKey(), student);
                }
            }
        }
        JsonNode settings = root.get("settings");
        if (settings != null && settings.isObject() && !settings.has("buttons")) {
            JsonNode legacyButtons = settings.get("buttonNames");
            if (legacyButtons != null && legacyButtons.isArray()) {
                ObjectNode buttons = mapper.createObjectNode();
                for (int i = 0; i < RollcallAnswer.BUTTONS.size() && i < legacyButtons.size(); i++) {
                    buttons.put(RollcallAnswer.BUTTONS.get(i).name(), legacyButtons.get(i).asText());
                }
                ((ObjectNode) settings).set("buttons", buttons);
            }
        }
        return root;
    }

    private void write(Path file, Object value) throws IOException {
        Files.createDirectories(dir);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), value);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
