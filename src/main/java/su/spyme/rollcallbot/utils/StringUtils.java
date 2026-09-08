package su.spyme.rollcallbot.utils;

import su.spyme.rollcallbot.objects.Student;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StringUtils {

    public static String instantToString(Instant instant) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(instant);
    }

    public static Instant parseDate(String date) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
        format.setLenient(false);
        return format.parse(date).toInstant();
    }

    public static String tag(List<Student> students) {
        StringBuilder builder = new StringBuilder();
        if (students.isEmpty()) return "";
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
                escapeMarkdown(name), userId
        );
    }

    public static String escapeMarkdown(String text) {
        return text.replaceAll("([_*`\\[])", "\\\\$1");
    }

    public static String formatShort(Student student) {
        String[] split = student.name.split(" ");
        return String.format(
                "[%s](tg://user?id=%d)",
                split.length > 1 ? split[1].toCharArray()[0] : split[0].toCharArray()[0], student.userId
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
