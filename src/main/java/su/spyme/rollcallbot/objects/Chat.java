package su.spyme.rollcallbot.objects;

import java.util.List;

public class Chat {
    public long chatId;
    public String name;
    public List<Long> admins;
    public ChatSettings settings;
    public List<Student> students;
    public List<Rollcall> rollcalls;

    public Chat(long chatId, String name, List<Long> admins, ChatSettings settings, List<Student> students, List<Rollcall> rollcalls) {
        this.chatId = chatId;
        this.name = name;
        this.admins = admins;
        this.settings = settings;
        this.students = students;
        this.rollcalls = rollcalls;
    }
}
