package de.tum.in.www1.artemis.domain.exam.event;

import java.time.Instant;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.service.exam.ExamLiveEventsService;
import de.tum.in.www1.artemis.web.rest.dto.examevent.ExamLiveEventDTO;

/**
 * Base class for all exam live events. An exam live event indicates that an event or change has occurred during an exam.
 * See the subclasses for more details.
 *
 * @see WorkingTimeUpdateEvent
 * @see ExamWideAnnouncementEvent
 * @see ExamLiveEventsService
 */
@Entity
@Table(name = "exam_live_event")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@EntityListeners(AuditingEntityListener.class)
public abstract class ExamLiveEvent extends DomainObject {

    @Column(name = "created_by", nullable = false, length = 50, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", updatable = false, nullable = false)
    private Instant createdDate = Instant.now();

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Exam exam;

    @ManyToOne
    private StudentExam studentExam;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public StudentExam getStudentExam() {
        return studentExam;
    }

    public void setStudentExam(StudentExam studentExam) {
        this.studentExam = studentExam;
    }

    protected void populateDTO(ExamLiveEventDTO dto) {
        dto.setId(getId());
        dto.setCreatedBy(getCreatedBy());
        dto.setCreatedDate(getCreatedDate());
    }

    public abstract ExamLiveEventDTO asDTO();
}
