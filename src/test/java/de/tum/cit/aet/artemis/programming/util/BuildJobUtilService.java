package de.tum.cit.aet.artemis.programming.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to build jobs for use in integration tests.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
public class BuildJobUtilService {

    @Autowired
    private BuildJobTestRepository buildJobTestRepository;

    public BuildJob createAndSaveBuildJob(String buildJobId, String name, ProgrammingExercise programmingExercise, Course course,
            ProgrammingExerciseStudentParticipation participation, Result result, ProgrammingSubmission submission) {
        BuildJob buildJob = new BuildJob();
        buildJob.setBuildJobId(buildJobId);
        buildJob.setName(name);
        buildJob.setExerciseId(programmingExercise.getId());
        buildJob.setCourseId(course.getId());
        buildJob.setParticipationId(participation.getId());
        buildJob.setResult(result);
        buildJob.setBuildAgentAddress("http://build-agent-address");
        buildJob.setBuildSubmissionDate(submission.getSubmissionDate());
        buildJob.setBuildStartDate(ZonedDateTime.now());
        buildJob.setBuildCompletionDate(ZonedDateTime.now().plusMinutes(5));
        buildJob.setRepositoryType(RepositoryType.USER);
        buildJob.setRepositoryName("student-repo");
        buildJob.setCommitHash(submission.getCommitHash());
        buildJob.setRetryCount(0);
        buildJob.setPriority(1);
        buildJob.setTriggeredByPushTo(RepositoryType.USER);
        buildJob.setBuildStatus(BuildStatus.ERROR);
        buildJob.setDockerImage("default-docker-image");
        return buildJobTestRepository.save(buildJob);
    }

}
