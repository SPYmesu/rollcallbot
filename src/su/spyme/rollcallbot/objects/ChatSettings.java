package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ChatSettings {
    public int timer;
    public String message;
    public List<String> buttonNames;
    public boolean birthdays;
}
