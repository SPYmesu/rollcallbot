package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Chat {
    public long chatId;
    public String name;
    public List<Long> admins;
    public ChatSettings settings;
    public List<Student> students;
    public List<Rollcall> rollcalls;
}
