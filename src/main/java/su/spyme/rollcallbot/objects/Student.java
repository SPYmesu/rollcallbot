package su.spyme.rollcallbot.objects;

import java.time.Instant;

public class Student {
    public long userId;
    public String name;
    public Instant birthdate;

    public Student(long userId, String name, Instant birthdate) {
        this.userId = userId;
        this.name = name;
        this.birthdate = birthdate;
    }

    public static boolean isValidName(String name) {
        return name.trim().split("\\s+").length >= 2;
    }

    @Override
    public String toString() {
        return name + " (" + userId + ")";
    }
}
