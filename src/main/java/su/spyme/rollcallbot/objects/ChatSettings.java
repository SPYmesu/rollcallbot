package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class ChatSettings {
    public static final String DEFAULT_MESSAGE = "🙋 Перекличка на наличие на паре";
    public int timer;
    public String message;
    public Map<RollcallAnswer, String> buttons;
    public boolean birthdays;

    public String getButton(RollcallAnswer answer) {
        String text = buttons.get(answer);
        return text == null || text.isBlank() ? answer.defaultButton : text;
    }

    public void setButton(RollcallAnswer answer, String text) {
        buttons.put(answer, text);
    }
}
