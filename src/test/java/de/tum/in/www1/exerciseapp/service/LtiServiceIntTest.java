package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.PersistentToken;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.PersistentTokenRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import java.time.ZonedDateTime;
import de.tum.in.www1.exerciseapp.service.util.RandomUtil;
import java.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import javax.inject.Inject;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for the LtiService.
 *
 * @see LtiService
 */
@ActiveProfiles(profiles = "dev,jira,bamboo,bitbucket")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@IntegrationTest
@Transactional
public class LtiServiceIntTest {

    @Inject
    private LtiService ltiService;

    @Inject
    private ParticipationRepository participationRepository;

    @Inject
    private ResultRepository resultRepository;


    @Test
    public void assertThatNoResultReturnsZeroScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

        String score = ltiService.getScoreForParticipation(participation);
        assertThat(score).isEqualTo("0.00");

        // cleanup
        participationRepository.delete(participation);
    }


    @Test
    public void assertThatSuccessfulResultReturnsFullScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

        Result result = new Result();
        result.setParticipation(participation);
        result.setBuildSuccessful(true);
        resultRepository.save(result);

        String score = ltiService.getScoreForParticipation(participation);

        assertThat(score).isEqualTo("1.00");

        // cleanup
        resultRepository.delete(result);
        participationRepository.delete(participation);
    }

    @Test
    public void assertThatUnsuccessfulResultWithoutTextReturnsZeroScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

        Result result = new Result();
        result.setParticipation(participation);
        result.setBuildSuccessful(false);
        resultRepository.save(result);

        String score = ltiService.getScoreForParticipation(participation);
        assertThat(score).isEqualTo("0.00");

        // cleanup
        resultRepository.delete(result);
        participationRepository.delete(participation);
    }

    @Test
    public void assertThatUnsuccessfulResultWith2of3FailedTestsReturnsCorrectScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

        Result result = new Result();
        result.setParticipation(participation);
        result.setBuildSuccessful(false);
        result.setResultString("2 of 3 failed");
        resultRepository.save(result);

        String score = ltiService.getScoreForParticipation(participation);
        assertThat(score).isEqualTo("0.33");

        // cleanup
        resultRepository.delete(result);
        participationRepository.delete(participation);
    }


    @Test
    public void assertThatMultipleResultsReturnLatestScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

        Result oldResult = new Result();
        oldResult.setParticipation(participation);
        oldResult.setBuildSuccessful(false);
        oldResult.setBuildCompletionDate(ZonedDateTime.now().minusHours(1));
        oldResult.setResultString("2 of 4 failed");
        resultRepository.save(oldResult);

        Result result = new Result();
        result.setParticipation(participation);
        result.setBuildSuccessful(false);
        result.setBuildCompletionDate(ZonedDateTime.now());
        result.setResultString("1 of 4 failed");
        resultRepository.save(result);

        String score = ltiService.getScoreForParticipation(participation);
        assertThat(score).isEqualTo("0.75");

        // cleanup
        resultRepository.delete(oldResult);
        resultRepository.delete(result);
        participationRepository.delete(participation);
    }


}
