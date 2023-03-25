package de.tum.in.www1.artemis.service;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.util.ModelFactory;

public class RepositoryAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    User user;

    ProgrammingExercise programmingExercise;

    ProgrammingExerciseParticipation participation;

    @BeforeEach
    void setup() {
        user = ModelFactory.generateActivatedUser("test_student_1");
        Course course = ModelFactory.generateCourse(1L, null, null, Collections.emptySet());
        programmingExercise = ModelFactory.generateProgrammingExercise(null, null, course);
        participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, user);
    }

    @Test
    void testShouldEnforceLockRepositoryPolicy() {
        // Prepare a ProgrammingExerciseParticipation
    }
}
