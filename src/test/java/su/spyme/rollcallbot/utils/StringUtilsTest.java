package su.spyme.rollcallbot.utils;

import org.junit.jupiter.api.Test;

import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;
import static su.spyme.rollcallbot.utils.StringUtils.*;

class StringUtilsTest {

    @Test
    void escapeMarkdownEscapesSpecialCharacters() {
        assertEquals("a\\_b\\*c\\`d\\[e", escapeMarkdown("a_b*c`d[e"));
    }

    @Test
    void escapeMarkdownKeepsPlainText() {
        assertEquals("Иванов Иван", escapeMarkdown("Иванов Иван"));
    }

    @Test
    void parseDateRoundTrip() {
        assertEquals("15.05.2004", instantToString(parseDate("15.05.2004")));
        assertEquals("29.02.2024", instantToString(parseDate(" 29.02.2024 ")));
    }

    @Test
    void parseDateRejectsInvalidDates() {
        assertThrows(DateTimeParseException.class, () -> parseDate("31.02.2005"));
        assertThrows(DateTimeParseException.class, () -> parseDate("29.02.2023"));
        assertThrows(DateTimeParseException.class, () -> parseDate("2005-02-01"));
        assertThrows(DateTimeParseException.class, () -> parseDate("abc"));
    }

    @Test
    void getArgumentsJoinsFromStart() {
        assertEquals("Иванов Иван", getArguments(2, new String[]{".с", "01.01.2005", "Иванов", "Иван"}));
        assertEquals("", getArguments(3, new String[]{"a", "b", "c"}));
    }
}
