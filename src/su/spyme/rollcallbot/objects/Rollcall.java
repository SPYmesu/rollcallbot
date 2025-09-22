package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Rollcall {
    public long chatId;
    public int threadId;
    public int rollcallMessageId;
    public int tagAllMessageId;
    public long resultChatId;
    public int resultMessageId;
    public String text;
    public List<RollcallEntry> entries;

    public int getCount(RollcallAnswer answer) {
        int count = 0;
        for (RollcallEntry entry : entries) {
            if (entry.answer.equals(answer)) count++;
        }
        return count;
    }

    public List<Student> getStudents(RollcallAnswer answer) {
        List<Student> students = new ArrayList<>();
        for (RollcallEntry entry : entries) {
            if (entry.answer.equals(answer)) students.add(entry.student);
        }
        return students;
    }
}
