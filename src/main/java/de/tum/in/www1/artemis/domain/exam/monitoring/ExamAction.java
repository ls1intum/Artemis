package de.tum.in.www1.artemis.domain.exam.monitoring;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonBackReference;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;

@Entity
@Table(name = "exam_action")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorOptions(force = true)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExamAction extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "exam_activity_id")
    @JsonBackReference
    protected ExamActivity examActivity;

    @Column(name = "timestamp", nullable = false)
    protected ZonedDateTime timestamp;

    @Column(name = "type", nullable = false, insertable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    protected ExamActionType type;

    public ExamActivity getExamActivity() {
        return examActivity;
    }

    public void setExamActivity(ExamActivity examActivity) {
        this.examActivity = examActivity;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public ExamActionType getType() {
        return type;
    }

    public void setType(ExamActionType type) {
        this.type = type;
    }
}
