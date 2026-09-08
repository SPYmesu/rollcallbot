package su.spyme.rollcallbot.objects;

import java.util.List;

public enum RollcallAnswer {
    HERE("✅ Я на паре"),
    NOTHEREREASON("🤒 Я болею (ув. причина)"),
    NOTHERE("❌ Я не на паре"),
    IGNORE(null);

    public static final List<RollcallAnswer> BUTTONS = List.of(HERE, NOTHEREREASON, NOTHERE);
    public final String defaultButton;

    RollcallAnswer(String defaultButton) {
        this.defaultButton = defaultButton;
    }

    public static RollcallAnswer getByName(String name) {
        return RollcallAnswer.valueOf(name.toUpperCase());
    }
}
