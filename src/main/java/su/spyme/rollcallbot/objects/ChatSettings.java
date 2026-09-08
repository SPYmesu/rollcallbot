package su.spyme.rollcallbot.objects;

import java.util.Map;

public class ChatSettings {
    public static final String DEFAULT_MESSAGE = "🙋 Перекличка на наличие на паре";
    public static final int DEFAULT_TIMER = 60;
    public static final int TIMER_MIN = 30;
    public static final int TIMER_MAX = 90;
    public static final int TIMER_OFF = -1;
    public int timer;
    public String message;
    public Map<RollcallAnswer, String> buttons;
    public boolean birthdays;

    public ChatSettings(int timer, String message, Map<RollcallAnswer, String> buttons, boolean birthdays) {
        this.timer = timer;
        this.message = message;
        this.buttons = buttons;
        this.birthdays = birthdays;
    }

    public String getButton(RollcallAnswer answer) {
        String text = buttons.get(answer);
        return text == null || text.isBlank() ? answer.defaultButton : text;
    }

    public void setButton(RollcallAnswer answer, String text) {
        buttons.put(answer, text);
    }
}
