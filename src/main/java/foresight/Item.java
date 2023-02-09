package foresight;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Item
{
    @Id
    String uid;
    String name;
    String type; // “TASK”, “PROJECT”
    LocalDate startDate;
    LocalDate endDate;
    String parentUid;
}