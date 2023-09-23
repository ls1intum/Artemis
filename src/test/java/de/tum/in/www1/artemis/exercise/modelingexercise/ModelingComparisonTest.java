package de.tum.in.www1.artemis.exercise.modelingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.exam.StudentExamService;

class ModelingComparisonTest {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    private static final String MODEL = "{\"version\":\"2.0.0\",\"type\":\"ClassDiagram\",\"size\":{\"width\":660,\"height\":620},\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"9556bd7b-4665-47e9-8221-6dde41d6352c\",\"name\":\"Class\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":0,\"y\":0,\"width\":160,\"height\":90},\"attributes\":[\"a49e575e-fdc2-4e8a-8176-b1e1b13984d6\"],\"methods\":[\"1595879c-7979-446d-84da-b2f028064bb4\"]},{\"id\":\"a49e575e-fdc2-4e8a-8176-b1e1b13984d6\",\"name\":\"+ attribute: Type\",\"type\":\"ClassAttribute\",\"owner\":\"9556bd7b-4665-47e9-8221-6dde41d6352c\",\"bounds\":{\"x\":0.5,\"y\":30.5,\"width\":159,\"height\":30}},{\"id\":\"1595879c-7979-446d-84da-b2f028064bb4\",\"name\":\"+ method()\",\"type\":\"ClassMethod\",\"owner\":\"9556bd7b-4665-47e9-8221-6dde41d6352c\",\"bounds\":{\"x\":0.5,\"y\":60.5,\"width\":159,\"height\":30}},{\"id\":\"c497eb07-22be-44b1-8299-f842f9dd0c39\",\"name\":\"Class\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":460,\"y\":20,\"width\":160,\"height\":90},\"attributes\":[\"c0e48c54-6f8e-4c3d-a2bb-0c75c2a457bf\"],\"methods\":[\"ace9c7d8-541e-4e56-bf53-c26fa380cec3\"]},{\"id\":\"c0e48c54-6f8e-4c3d-a2bb-0c75c2a457bf\",\"name\":\"+ attribute: Type\",\"type\":\"ClassAttribute\",\"owner\":\"c497eb07-22be-44b1-8299-f842f9dd0c39\",\"bounds\":{\"x\":460.5,\"y\":50.5,\"width\":159,\"height\":30}},{\"id\":\"ace9c7d8-541e-4e56-bf53-c26fa380cec3\",\"name\":\"+ method()\",\"type\":\"ClassMethod\",\"owner\":\"c497eb07-22be-44b1-8299-f842f9dd0c39\",\"bounds\":{\"x\":460.5,\"y\":80.5,\"width\":159,\"height\":30}},{\"id\":\"0c5619c7-0a1a-4078-9258-0c20c04418da\",\"name\":\"Class\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":230,\"y\":190,\"width\":160,\"height\":90},\"attributes\":[\"82ed73ff-32ca-4962-bf6d-9cf1016aaa5b\"],\"methods\":[\"26d99794-d6cc-4193-af0a-9883d049a9ff\"]},{\"id\":\"82ed73ff-32ca-4962-bf6d-9cf1016aaa5b\",\"name\":\"+ attribute: Type\",\"type\":\"ClassAttribute\",\"owner\":\"0c5619c7-0a1a-4078-9258-0c20c04418da\",\"bounds\":{\"x\":230.5,\"y\":220.5,\"width\":159,\"height\":30}},{\"id\":\"26d99794-d6cc-4193-af0a-9883d049a9ff\",\"name\":\"+ method()\",\"type\":\"ClassMethod\",\"owner\":\"0c5619c7-0a1a-4078-9258-0c20c04418da\",\"bounds\":{\"x\":230.5,\"y\":250.5,\"width\":159,\"height\":30}}],\"relationships\":[{\"id\":\"b0661cbc-4380-4228-89b0-ea8160811ddb\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":160,\"y\":45,\"width\":300,\"height\":31},\"path\":[{\"x\":0,\"y\":10},{\"x\":300,\"y\":10}],\"source\":{\"direction\":\"Right\",\"element\":\"9556bd7b-4665-47e9-8221-6dde41d6352c\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"c497eb07-22be-44b1-8299-f842f9dd0c39\",\"multiplicity\":\"\",\"role\":\"\"},\"isManuallyLayouted\":false}],\"assessments\":[]}";

    private static final String EMPTY_MODEL = "{\"version\":\"2.0.0\",\"type\":\"ClassDiagram\",\"size\":{\"width\":40,\"height\":40},\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[],\"relationships\":[],\"assessments\":[]}";

    @Test
    void compareSubmittedAnswers() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>());
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, DiagramType.ClassDiagram,
                course);

        var submission1 = ModelingExerciseFactory.generateModelingExerciseSubmission(modelingExercise, MODEL, "explanation");
        var submission2 = ModelingExerciseFactory.generateModelingExerciseSubmission(modelingExercise, MODEL, "explanation");  // same as submission1
        var submission3 = ModelingExerciseFactory.generateModelingExerciseSubmission(modelingExercise, EMPTY_MODEL, "explanation");
        var submission4 = ModelingExerciseFactory.generateModelingExerciseSubmission(modelingExercise, EMPTY_MODEL, "different explanation"); // same model as submission3, but
                                                                                                                                              // different explanation
        var submission5 = ModelingExerciseFactory.generateModelingExerciseSubmission(modelingExercise, null, null);
        var submission6 = ModelingExerciseFactory.generateModelingExerciseSubmission(modelingExercise, null, null);

        assertThat(StudentExamService.isContentEqualTo(submission1, submission2)).isTrue();  // submission with same model and explanation
        assertThat(StudentExamService.isContentEqualTo(submission1, submission3)).isFalse(); // submission with different model, but the same explanation
        assertThat(StudentExamService.isContentEqualTo(submission3, submission4)).isFalse(); // submission with same model, but different explanation
        assertThat(StudentExamService.isContentEqualTo(submission3, submission5)).isFalse();
        assertThat(StudentExamService.isContentEqualTo(submission5, submission6)).isTrue();  // both submission with null model

        assertThat(StudentExamService.isContentEqualTo(submission2, null)).isFalse(); // one submission null
        assertThat(StudentExamService.isContentEqualTo(null, submission5)).isFalse();  // one submission null, other null model
        assertThat(StudentExamService.isContentEqualTo((ModelingSubmission) null, null)).isTrue(); // both submissions null
    }
}
