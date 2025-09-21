package su.spyme.rollcallbot.objects;

public enum RecallAnswer {
    HERE,
    NOTHERE,
    NOTHEREREASON,
    IGNORE;

    public static RecallAnswer getByName(String name) {
        return RecallAnswer.valueOf(name.toUpperCase());
    }
}
