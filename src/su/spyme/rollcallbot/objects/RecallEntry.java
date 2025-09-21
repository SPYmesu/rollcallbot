package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RecallEntry {
    public Student student;
    public RecallAnswer answer;
    public int times;

    public void addTimes() {
        times++;
    }
}
