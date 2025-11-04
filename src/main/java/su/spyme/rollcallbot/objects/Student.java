package su.spyme.rollcallbot.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class Student {
    public long userId;
    public String name;
    public Instant birthdate;
}
