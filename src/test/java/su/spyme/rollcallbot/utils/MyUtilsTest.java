package su.spyme.rollcallbot.utils;

import org.junit.jupiter.api.Test;
import su.spyme.rollcallbot.objects.Student;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static su.spyme.rollcallbot.Main.ZONE;
import static su.spyme.rollcallbot.utils.MyUtils.hasBirthdate;
import static su.spyme.rollcallbot.utils.MyUtils.isBirthdayToday;
import static su.spyme.rollcallbot.utils.StringUtils.parseDate;

class MyUtilsTest {

    @Test
    void epochMeansNoBirthdate() {
        assertFalse(hasBirthdate(new Student(1, "Иванов Иван", Instant.EPOCH)));
        assertFalse(hasBirthdate(new Student(1, "Иванов Иван", parseDate("01.01.1970"))));
        assertTrue(hasBirthdate(new Student(1, "Иванов Иван", parseDate("15.05.2004"))));
    }

    @Test
    void studentWithoutBirthdateIsNeverCongratulated() {
        assertFalse(isBirthdayToday(new Student(1, "Иванов Иван", Instant.EPOCH)));
    }

    @Test
    void birthdayIsDetectedByDayAndMonth() {
        LocalDate today = LocalDate.now(ZONE);
        String todayInPast = today.minusYears(20).format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
        String tomorrowInPast = today.plusDays(1).minusYears(20).format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
        assertTrue(isBirthdayToday(new Student(1, "Иванов Иван", parseDate(todayInPast))));
        assertFalse(isBirthdayToday(new Student(1, "Иванов Иван", parseDate(tomorrowInPast))));
    }

    @Test
    void nameMustContainSurnameAndName() {
        assertTrue(Student.isValidName("Иванов Иван"));
        assertTrue(Student.isValidName("  Иванов   Иван Иванович "));
        assertFalse(Student.isValidName("Иванов"));
        assertFalse(Student.isValidName("   "));
    }
}
