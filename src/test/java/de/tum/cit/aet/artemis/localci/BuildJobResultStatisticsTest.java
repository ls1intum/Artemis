package de.tum.cit.aet.artemis.localci;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the build job result counts behind the admin and the course build overview.
 * <p>
 * Both were served by a single query with a {@code (:courseId IS NULL OR b.courseId = :courseId)} guard. MySQL cannot
 * fold that branch away, so it could estimate neither predicate and scanned the whole table for both callers - the
 * defect documented at length on {@code findFinishedIdsByFilterCriteria}. The query is therefore split in two, and
 * each variant has to count exactly what its caller asks for.
 */
class BuildJobResultStatisticsTest extends AbstractSpringIntegrationIndependentTest {

    private static final long COURSE_ID = 4711L;

    private static final long OTHER_COURSE_ID = 4712L;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    private ZonedDateTime now;

    @BeforeEach
    void setup() {
        buildJobRepository.deleteAll();
        now = ZonedDateTime.now();
    }

    @Test
    void shouldCountBuildJobResultsOfAllCoursesSinceTheGivenDate() {
        saveBuildJob(COURSE_ID, BuildStatus.SUCCESSFUL, now.minusHours(1));
        saveBuildJob(OTHER_COURSE_ID, BuildStatus.FAILED, now.minusHours(2));
        saveBuildJob(COURSE_ID, BuildStatus.ERROR, now.minusDays(2));

        var statistics = buildJobRepository.getBuildJobsResultsStatistics(now.minusDays(1));

        assertThat(statistics).containsExactlyInAnyOrder(new BuildJobResultCountDTO(BuildStatus.SUCCESSFUL, 1), new BuildJobResultCountDTO(BuildStatus.FAILED, 1));
    }

    @Test
    void shouldCountBuildJobResultsOfOneCourseSinceTheGivenDate() {
        saveBuildJob(COURSE_ID, BuildStatus.SUCCESSFUL, now.minusHours(1));
        saveBuildJob(COURSE_ID, BuildStatus.SUCCESSFUL, now.minusHours(2));
        saveBuildJob(OTHER_COURSE_ID, BuildStatus.FAILED, now.minusHours(2));
        saveBuildJob(COURSE_ID, BuildStatus.ERROR, now.minusDays(2));

        var statistics = buildJobRepository.getBuildJobsResultsStatisticsForCourse(now.minusDays(1), COURSE_ID);

        assertThat(statistics).containsExactly(new BuildJobResultCountDTO(BuildStatus.SUCCESSFUL, 2));
    }

    private void saveBuildJob(long courseId, BuildStatus buildStatus, ZonedDateTime buildSubmissionDate) {
        BuildJob buildJob = new BuildJob();
        buildJob.setCourseId(courseId);
        buildJob.setBuildStatus(buildStatus);
        buildJob.setBuildSubmissionDate(buildSubmissionDate);
        buildJobRepository.save(buildJob);
    }
}
