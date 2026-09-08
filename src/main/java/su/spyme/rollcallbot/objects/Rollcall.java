package su.spyme.rollcallbot.objects;

import java.util.ArrayList;
import java.util.List;

public class Rollcall {
    public long chatId;
    public int threadId;
    public int rollcallMessageId;
    public int tagAllMessageId;
    public long resultChatId;
    public int resultMessageId;
    public String text;
    public long startTime;
    public List<RollcallEntry> entries;

    public Rollcall(long chatId, int threadId, int rollcallMessageId, int tagAllMessageId, long resultChatId, int resultMessageId, String text, long startTime, List<RollcallEntry> entries) {
        this.chatId = chatId;
        this.threadId = threadId;
        this.rollcallMessageId = rollcallMessageId;
        this.tagAllMessageId = tagAllMessageId;
        this.resultChatId = resultChatId;
        this.resultMessageId = resultMessageId;
        this.text = text;
        this.startTime = startTime;
        this.entries = entries;
    }

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
