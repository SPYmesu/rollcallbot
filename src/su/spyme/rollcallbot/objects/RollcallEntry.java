package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RollcallEntry {
    public Student student;
    public RollcallAnswer answer;
    public int times;

    public void addTimes() {
        times++;
    }
}
