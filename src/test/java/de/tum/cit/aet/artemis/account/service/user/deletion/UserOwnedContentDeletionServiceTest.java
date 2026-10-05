package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.cleanup.CommunicationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.UserReferenceCount;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Covers the deletion of the content an account owns rather than merely points at.
 *
 * <p>
 * These statements reach further than a single foreign key - down a discussion thread, across a join table - and
 * several of them have to be written natively, where nothing checks them before they run. The first test builds the
 * shape that is hardest to get right; the second insists that every one of them at least executes.
 */
class UserOwnedContentDeletionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ownedcontent";

    @Autowired
    private UserOwnedContentDeletionService userOwnedContentDeletionService;

    @Autowired
    private CommunicationDataCleanupRepository communicationDataCleanupRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private StudentExamTestRepository studentExamTestRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Test
    void aThreadIsRemovedWithEverythingOtherPeopleHungOnIt() {
        Course course = courseUtilService.addEmptyCourse();
        User author = userUtilService.createAndSaveUser(TEST_PREFIX + "author");
        User other = userUtilService.createAndSaveUser(TEST_PREFIX + "other");
        Channel channel = conversationUtilService.createCourseWideChannel(course, TEST_PREFIX + "channel");

        // A thread the account started, with a reply and reactions left on it by somebody else.
        Post authoredThread = conversationUtilService.addMessageToConversation(author.getLogin(), channel);
        conversationUtilService.addThreadReplyWithReactionForUserToPost(other.getLogin(), authoredThread);
        conversationUtilService.addReactionForUserToPost(other.getLogin(), authoredThread);

        // A thread somebody else started, which the account only reacted to.
        Post foreignThread = conversationUtilService.addMessageToConversation(other.getLogin(), channel);
        conversationUtilService.addReactionForUserToPost(author.getLogin(), foreignThread);

        userOwnedContentDeletionService.deleteCommunicationContent(author.getId());

        assertThat(count(communicationDataCleanupRepository.countPosts(List.of(author.getId())))).as("the thread the account started is gone").isZero();
        assertThat(count(communicationDataCleanupRepository.countReactions(List.of(author.getId())))).as("so is what it left on other people's threads").isZero();
        assertThat(count(communicationDataCleanupRepository.countAnswerPosts(List.of(other.getId())))).as("the reply below that thread cannot outlive it").isZero();

        assertThat(count(communicationDataCleanupRepository.countPosts(List.of(other.getId())))).as("the thread the other account started is untouched").isOne();
        assertThat(count(communicationDataCleanupRepository.countReactions(List.of(other.getId())))).as("only the reaction on its own thread is left").isOne();
    }

    @Test
    void everyCleanupRunsForAnAccountThatOwnsNothing() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "empty");
        long userId = user.getId();

        assertThatCode(() -> {
            assertThat(userOwnedContentDeletionService.deleteDataExports(userId)).isEmpty();
            userOwnedContentDeletionService.deleteTeams(userId);
            userOwnedContentDeletionService.deleteParticipations(userId);
            assertThat(userOwnedContentDeletionService.deleteExamAttendance(userId)).isEmpty();
            userOwnedContentDeletionService.deleteComplaints(userId);
            userOwnedContentDeletionService.deletePlagiarismCases(userId);
            userOwnedContentDeletionService.deleteCommunicationContent(userId);
            userOwnedContentDeletionService.deleteTutorParticipations(userId);
            userOwnedContentDeletionService.anonymiseScienceEvents(user.getLogin(), userId);
        }).doesNotThrowAnyException();
    }

    @Test
    void anOwnedTeamIsHandedToARemainingMember() {
        Course course = courseUtilService.addEmptyCourse();
        User owner = userUtilService.createAndSaveUser(TEST_PREFIX + "teamowner");
        User teammate = userUtilService.createAndSaveUser(TEST_PREFIX + "teammate");
        var exercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        Team team = teamUtilService.createTeam(Set.of(owner, teammate), owner, exercise, TEST_PREFIX + "handedover");

        userOwnedContentDeletionService.deleteTeams(owner.getId());

        Team handedOver = teamRepository.findById(team.getId()).orElseThrow();
        assertThat(handedOver.getOwner()).as("a team with somebody left in it carries on under them").isNotNull();
        assertThat(handedOver.getOwner().getId()).isEqualTo(teammate.getId());
    }

    @Test
    void aTeamTheAccountOnlyOwnedIsLeftWithoutAnOwner() {
        Course course = courseUtilService.addEmptyCourse();
        User owner = userUtilService.createAndSaveUser(TEST_PREFIX + "loneowner");
        var exercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        // The account owns the team without being one of its students, so there is nobody to hand it to and nothing
        // that would make the team its own.
        Team team = teamUtilService.createTeam(Set.of(), owner, exercise, TEST_PREFIX + "ownerless");

        userOwnedContentDeletionService.deleteTeams(owner.getId());

        Team detached = teamRepository.findById(team.getId()).orElseThrow();
        assertThat(detached.getOwner()).as("the team survives the account that owned it").isNull();
    }

    @Test
    void examRegistrationsReportTheirImagesAndGoWithTheirSittings() {
        Course course = courseUtilService.addEmptyCourse();
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "examinee");
        Exam exam = examUtilService.addExam(course);

        ExamUser registration = new ExamUser();
        registration.setUser(user);
        registration.setExam(exam);
        registration.setSigningImagePath("exam-user/signatures/1/signature.png");
        registration.setStudentImagePath("exam-user/1/photo.png");
        registration = examUserRepository.save(registration);
        long registrationId = registration.getId();

        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, user);
        examUtilService.addExamSessionToStudentExam(studentExam, "token", "192.0.2.1", "fingerprint", "instance", "agent");

        List<Path> imagePaths = userOwnedContentDeletionService.deleteExamAttendance(user.getId());

        assertThat(imagePaths).as("both personal images are reported so the files can be removed as well")
                .containsExactly(new FileSystemLocation.ExamUserSignature("signature.png").path(), new FileSystemLocation.ExamUserImage(registrationId, "photo.png").path());
        assertThat(examUserRepository.findById(registration.getId())).isEmpty();
        assertThat(studentExamTestRepository.findById(studentExam.getId())).as("the sitting cannot outlive the exam it belongs to").isEmpty();
    }

    @Test
    void aTeamGoesWithWhatWasHeldAgainstIt() {
        Course course = courseUtilService.addEmptyCourse();
        User soleMember = userUtilService.createAndSaveUser(TEST_PREFIX + "solemember");
        var exercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        Team team = teamUtilService.createTeam(Set.of(soleMember), soleMember, exercise, TEST_PREFIX + "accusedteam");

        // A plagiarism case is held against the team, not against any one of its members, so nothing keyed on the
        // account removes it - and the foreign key from the case to the team refuses the team's deletion.
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);
        plagiarismCase.setTeam(team);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        userOwnedContentDeletionService.deleteTeams(soleMember.getId());

        assertThat(teamRepository.findById(team.getId())).as("the team of an account that was its only member goes with it").isEmpty();
        assertThat(plagiarismCaseRepository.findById(plagiarismCase.getId())).as("and so does what was held against the team").isEmpty();
    }

    private static long count(List<UserReferenceCount> counts) {
        return counts.stream().mapToLong(UserReferenceCount::getCount).sum();
    }
}
