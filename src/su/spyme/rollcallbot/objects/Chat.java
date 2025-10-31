package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.simpleyaml.configuration.file.YamlFile;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Chat {
    public long chatId;
    public YamlFile config;
    public ChatSettings settings;
    public List<Student> students;
    public List<Rollcall> rollcalls;
}
