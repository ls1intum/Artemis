package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class TextExerciseTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @Autowired
    TextSubmissionRepository textSubmissionRepository;

    @Before
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(1, 2);
    }

    @Test
    @WithMockUser(roles = "TA")
    public void submitEnglishTextExercise() throws Exception {
        database.addCourseWithOneTextExercise();
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This Submission is written in English", Language.ENGLISH, false);
        long courseID = courseRepo.findAllActive().get(0).getId();
        TextExercise textExercise = textExerciseRepository.findByCourseId(courseID).get(0);
        request.post("api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);

        Optional<TextSubmission> result = textSubmissionRepository.findById((long) 0);
        assertThat(result.isPresent()).isEqualTo(true);
        assertThat(result.get().getLanguage()).isEqualTo(Language.ENGLISH);

    }
}
