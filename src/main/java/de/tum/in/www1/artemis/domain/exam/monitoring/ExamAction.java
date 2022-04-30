package de.tum.in.www1.artemis.domain.exam.monitoring;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "exam_action")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "exam_action_type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorOptions(force = true)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

// TODO
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
// @JsonSubTypes()
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamAction extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "exam_activity_id")
    protected ExamActivity examActivity;

    @Column(name = "timestamp", nullable = false)
    protected ZonedDateTime timestamp;

    public ExamActivity getExamActivity() {
        return examActivity;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }
}
