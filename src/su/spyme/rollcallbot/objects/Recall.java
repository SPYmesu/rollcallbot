package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Recall {
    public long chatId;
    public int recallMessageId;
    public int tagAllMessageId;
    public long resultChatId;
    public int resultMessageId;
    public String text;
    public List<RecallEntry> entries;

    public int getCount(RecallAnswer answer) {
        int count = 0;
        for (RecallEntry entry : entries) {
            if (entry.answer.equals(answer)) count++;
        }
        return count;
    }

    public List<Student> getStudents(RecallAnswer answer) {
        List<Student> students = new ArrayList<>();
        for (RecallEntry entry : entries) {
            if (entry.answer.equals(answer)) students.add(entry.student);
        }
        return students;
    }
}
