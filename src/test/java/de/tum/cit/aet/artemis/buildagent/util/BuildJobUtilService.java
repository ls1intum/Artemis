package de.tum.cit.aet.artemis.buildagent.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class BuildJobUtilService {

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    public BuildJob addBuildJobForParticipationId(long participationId, long exerciseId, Result result) {
        BuildJob buildJob = new BuildJob();
        buildJob.setResult(result);
        buildJob.setParticipationId(participationId);
        buildJob.setExerciseId(exerciseId);
        return buildJobRepository.save(buildJob);
    }
}
