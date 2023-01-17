package de.tum.in.www1.artemis.domain.exam;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

public interface ExamExercise {

    Long getId();

    String getTitle();

    Double getMaxPoints();

    Set<StudentParticipation> getStudentParticipations();

    IncludedInOverallScore getIncludedInOverallScore();

    ExerciseGroup getExerciseGroup();

    boolean isExampleSolutionPublished();

    Double getBonusPoints();

    List<GradingCriterion> getGradingCriteria();

    String getGradingInstructions();

    void setCourse(Course course);

    void filterSensitiveInformation();

    void setExampleSolutionPublicationDate(ZonedDateTime exampleSolutionPublicationDate);

    void setExerciseGroup(ExerciseGroup exerciseGroup);

    StudentParticipation findParticipation(List<StudentParticipation> participations);

    void setStudentParticipations(Set<StudentParticipation> participation);

    String getType();
}
