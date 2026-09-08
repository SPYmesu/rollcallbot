package su.spyme.rollcallbot.objects;

public class RollcallEntry {
    public Student student;
    public RollcallAnswer answer;
    public int times;
    public long answerTime;

    public RollcallEntry(Student student, RollcallAnswer answer, int times, long answerTime) {
        this.student = student;
        this.answer = answer;
        this.times = times;
        this.answerTime = answerTime;
    }

    public void addTimes() {
        times++;
    }
}
