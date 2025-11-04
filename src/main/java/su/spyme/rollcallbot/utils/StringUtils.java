package su.spyme.rollcallbot.utils;

import su.spyme.rollcallbot.objects.Student;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StringUtils {

    public static String instantToString(Instant instant) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(instant);
    }

    public static String tag(List<Student> students) {
        StringBuilder builder = new StringBuilder();
        for (Student student : students) {
            builder.append(formatShort(student)).append(", ");
        }
        return builder.substring(0, builder.length() - 2);
    }

    public static String format(Student student) {
        return format(student.name, student.userId);
    }

    public static String format(String name, long userId) {
        return String.format(
                "[%s](tg://user?id=%d)",
                name, userId
        );
    }

    public static String formatShort(Student student) {
        return String.format(
                "[%s](tg://user?id=%d)",
                student.name.split(" ")[1].toCharArray()[0], student.userId
        );
    }

    public static String getArguments(int start, String[] args) {
        StringBuilder s = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            s.append(i == start ? args[i] : ' ' + args[i]);
        }
        return s.toString();
    }
}
