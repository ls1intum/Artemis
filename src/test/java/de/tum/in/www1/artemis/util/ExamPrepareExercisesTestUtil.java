package de.tum.in.www1.artemis.util;

import static org.junit.jupiter.api.Assertions.fail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.service.util.ExamExerciseStartPreparationStatus;

public class ExamPrepareExercisesTestUtil {

    private static final Logger log = LoggerFactory.getLogger(ExamPrepareExercisesTestUtil.class);

    public static int prepareExerciseStart(RequestUtilService requestUtilService, Exam exam, Course course) throws Exception {
        requestUtilService.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises", null, HttpStatus.OK, null);

        // Wait for it to complete
        long start = System.currentTimeMillis();
        do {
            var status = requestUtilService.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises/status", HttpStatus.OK,
                    ExamExerciseStartPreparationStatus.class);
            if (status != null && status.finished() + status.failed() == status.overall()) {
                return status.participationCount();
            }
            else {
                log.warn("Exam exercise preparation not finished: Done = {}, Failed = {}, Overall = {}, Participations = {}", status.finished(), status.failed(), status.overall(),
                        status.participationCount());
            }
            try {
                Thread.sleep(250);
            }
            catch (InterruptedException ignored) {
                break;
            }
        }
        while (System.currentTimeMillis() - start < 60000);
        fail("Exercise preparation did not finish within 1 minute");
        return 0;
    }
}
