package su.spyme.rollcallbot.handlers;

import su.spyme.rollcallbot.objects.Chat;
import su.spyme.rollcallbot.objects.RollcallAnswer;

public record PendingInput(Setting setting, Chat chat, int menuId, int infoMessageId, long studentId, RollcallAnswer answer) {

    public enum Setting {
        TIMER, MESSAGE, BUTTON, STUDENT, POSITION, NAME, BIRTHDATE
    }

    public PendingInput(Setting setting, Chat chat, int menuId, int infoMessageId) {
        this(setting, chat, menuId, infoMessageId, 0, null);
    }
}
