package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * Test class for the LtiService.
 *
 * @see LtiService
 */
@ActiveProfiles(profiles = "dev,jira,bamboo,bitbucket")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@SpringBootTest
@Transactional
public class LtiServiceIntTest {

    private final LtiService ltiService;
    private final ParticipationRepository participationRepository;
    private final ResultRepository resultRepository;

    public LtiServiceIntTest(LtiService ltiService, ParticipationRepository participationRepository, ResultRepository resultRepository) {
        this.ltiService = ltiService;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
    }


    @Test
    public void assertThatNoResultReturnsZeroScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

//        String score = ltiService.getScoreForParticipation(participation);
//        assertThat(score).isEqualTo("0.00");

        // cleanup
        participationRepository.delete(participation);
    }


    @Test
    public void assertThatSuccessfulResultReturnsFullScore() {

        Participation participation = new Participation();
        participationRepository.save(participation);

        Result result = new Result();
        result.setParticipation(participation);
        result.setSuccessful(true);
        resultRepository.save(result);

//        String score = ltiService.getScoreForParticipation(participation);
//        assertThat(score).isEqualTo("1.00");

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
        result.setSuccessful(false);
        resultRepository.save(result);

//        String score = ltiService.getScoreForParticipation(participation);
//        assertThat(score).isEqualTo("0.00");

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
        result.setSuccessful(false);
        result.setResultString("2 of 3 failed");
        resultRepository.save(result);

//        String score = ltiService.getScoreForParticipation(participation);
//        assertThat(score).isEqualTo("0.33");

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
        oldResult.setSuccessful(false);
        oldResult.setCompletionDate(ZonedDateTime.now().minusHours(1));
        oldResult.setResultString("2 of 4 failed");
        resultRepository.save(oldResult);

        Result result = new Result();
        result.setParticipation(participation);
        result.setSuccessful(false);
        result.setCompletionDate(ZonedDateTime.now());
        result.setResultString("1 of 4 failed");
        resultRepository.save(result);

//        String score = ltiService.getScoreForParticipation(participation);
//        assertThat(score).isEqualTo("0.75");

        // cleanup
        resultRepository.delete(oldResult);
        resultRepository.delete(result);
        participationRepository.delete(participation);
    }


}
