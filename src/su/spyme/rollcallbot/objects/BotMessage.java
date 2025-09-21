package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class BotMessage{
    private long messageId;
    private long userId;
    private long peerId;
    private boolean isFromChat;
    private String body;
    private String payload;
}
