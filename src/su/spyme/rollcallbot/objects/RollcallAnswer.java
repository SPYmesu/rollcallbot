package su.spyme.rollcallbot.objects;

public enum RollcallAnswer {
    HERE,
    NOTHERE,
    NOTHEREREASON,
    IGNORE;

    public static RollcallAnswer getByName(String name) {
        return RollcallAnswer.valueOf(name.toUpperCase());
    }
}
