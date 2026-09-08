package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ChatSettings {
    public static final String DEFAULT_MESSAGE = "\uD83D\uDE4B Перекличка на наличие на паре";
    public static final List<String> DEFAULT_BUTTONS = List.of("✅ Я на паре", "\uD83E\uDD12 Я болею (ув. причина)", "❌ Я не на паре");
    public int timer;
    public String message;
    public List<String> buttonNames;
    public boolean birthdays;
}
