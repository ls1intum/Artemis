package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;

/**
 * The participant of a user is created lazily when a student first opens a course-wide channel, by several requests at once. The creation must produce exactly one
 * participant and no error however the requests interleave.
 */
class ConversationParticipantCreationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "participantcreation";

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ConversationService conversationService;

    private Channel channel;

    private User student;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        channel = conversationUtilService.createCourseWideChannel(exampleCourse, "participantcreation");
    }

    @AfterEach
    void tearDown() {
        conversationRepository.deleteAllByCourseId(exampleCourseId);
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    void createsTheParticipantOnceWithTheGivenValues() {
        var lastRead = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS);

        assertThat(conversationParticipantRepository.createIfAbsent(channel.getId(), student.getId(), true, lastRead, 3L)).isTrue();
        // a second call, even with other values, leaves the existing participant untouched
        assertThat(conversationParticipantRepository.createIfAbsent(channel.getId(), student.getId(), false, ZonedDateTime.now(), 0L)).isFalse();

        var participants = getParticipants(channel.getId());
        assertThat(participants).hasSize(1);
        var participant = participants.iterator().next();
        assertThat(participant.getUser().getId()).isEqualTo(student.getId());
        assertThat(participant.getIsModerator()).isTrue();
        assertThat(participant.getIsFavorite()).isFalse();
        assertThat(participant.getIsHidden()).isFalse();
        assertThat(participant.getIsMuted()).isFalse();
        assertThat(participant.getUnreadMessagesCount()).isEqualTo(3L);
        assertThat(participant.getLastRead()).isCloseTo(lastRead, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void createsNothingForAConversationOrUserThatDoesNotExist() {
        long missingId = Long.MAX_VALUE;

        assertThat(conversationParticipantRepository.createIfAbsent(missingId, student.getId(), false, ZonedDateTime.now(), 0L)).isFalse();
        assertThat(conversationParticipantRepository.createIfAbsent(channel.getId(), missingId, false, ZonedDateTime.now(), 0L)).isFalse();

        assertThat(getParticipants(channel.getId())).isEmpty();
    }

    @Test
    void severalRequestsAtTheSameTimeCreateExactlyOneParticipantWithoutAnError() throws Exception {
        int requests = 8;
        var executor = Executors.newFixedThreadPool(requests);
        try {
            for (int round = 0; round < 5; round++) {
                var otherChannel = conversationUtilService.createCourseWideChannel(exampleCourse, "participantcreation" + round);
                var start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int request = 0; request < requests; request++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return conversationParticipantRepository.createIfAbsent(otherChannel.getId(), student.getId(), false, ZonedDateTime.now(), 0L);
                    }));
                }
                start.countDown();

                int created = 0;
                for (var result : results) {
                    // get() rethrows an error of the request, which this test must not see
                    if (result.get(30, TimeUnit.SECONDS)) {
                        created++;
                    }
                }
                assertThat(created).as("requests that created the participant in round %d", round).isEqualTo(1);
                assertThat(getParticipants(otherChannel.getId())).hasSize(1);
            }
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void severalMembershipChecksAtTheSameTimeCreateExactlyOneParticipantWithoutAnError() throws Exception {
        int requests = 8;
        var executor = Executors.newFixedThreadPool(requests);
        try {
            var start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int request = 0; request < requests; request++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return conversationService.isMemberOrCreateForCourseWideElseThrow(channel.getId(), student, Optional.empty());
                }));
            }
            start.countDown();

            for (var result : results) {
                // get() rethrows an error of the request, which this test must not see
                result.get(30, TimeUnit.SECONDS);
            }
            assertThat(getParticipants(channel.getId())).hasSize(1);
        }
        finally {
            executor.shutdownNow();
        }
    }
}
