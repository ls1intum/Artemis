package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityKind;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityState;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * The tool activity of a message is a list converted to JSON and stored in a plain text column, so these tests read it
 * back out of the database rather than out of the persistence context: the column and the mapping have to agree on
 * what is in there. They did not. The mapping declared a CLOB, which on PostgreSQL is a large object, so the JSON went
 * into pg_largeobject and the column received the object's id - and a row holding the JSON itself, as every row
 * written before the move to PostgreSQL does, was read as an object id and failed every session load for its user with
 * "Bad value for type long".
 */
class IrisMessageToolActivityPersistenceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "irismessagetoolactivity";

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    // Reads and writes the column past the mapping on purpose: what is in the column is the one thing an entity
    // round-trip through a single mapping cannot show, and a row the mapping would not produce is what has to be read
    // here. Raw JDBC and a transaction of its own are both allowed in a test and neither in production code.
    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Course course;

    private User student;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        course = courseUtilService.addEmptyCourse();
    }

    @Test
    void shouldReadBackARecordedToolActivity() {
        var activity = new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.FINISHED, "Lecture 1", "2 chunks", 120L);

        long sessionId = saveSessionWithToolActivity(List.of(activity));

        assertThat(reloadToolActivity(sessionId)).containsExactly(activity);
    }

    /**
     * An empty list is what a run without tool calls records, so it is the value most rows hold, and the two
     * characters it is stored as - {@code []} - are the ones production failed to read.
     */
    @Test
    void shouldReadBackAnEmptyToolActivity() {
        long sessionId = saveSessionWithToolActivity(List.of());

        assertThat(reloadToolActivity(sessionId)).isEmpty();
    }

    @Test
    void shouldReadBackAnAbsentToolActivity() {
        long sessionId = saveSessionWithToolActivity(null);

        assertThat(reloadToolActivity(sessionId)).isNull();
    }

    /**
     * The tool activity belongs in the column, not in a large object the column points at. A row written as a large
     * object is unreadable to anything that takes the column at face value - MySQL, a database export, a query - and
     * the objects are never reclaimed, because nothing unlinks them when the message is deleted.
     */
    @Test
    void shouldStoreToolActivityAsTextInTheColumn() {
        var activity = new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.FINISHED, null, null, 120L);

        long sessionId = saveSessionWithToolActivity(List.of(activity));

        assertThat(rawToolActivity(sessionId)).isEqualTo("""
                [{"id":"activity-1","kind":"TOOL","name":"lecture_content_retrieval","state":"FINISHED","durationMillis":120}]""");
    }

    /**
     * The production failure, reproduced: a row whose column holds the JSON, which is how every row written before the
     * move to PostgreSQL is stored and how MySQL stores them still. Read as a large object id, this row took the whole
     * session down with it.
     */
    @Test
    void shouldReadAToolActivityThatIsStoredInTheColumn() {
        long sessionId = saveSessionWithToolActivity(null);
        overwriteToolActivityColumn(sessionId, """
                [{"id":"activity-1","kind":"TOOL","name":"retrieval","state":"FINISHED"}]""");

        assertThat(reloadToolActivity(sessionId))
                .containsExactly(new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "retrieval", PyrisActivityState.FINISHED, null, null, null));
    }

    /**
     * A stored value that is not a tool trail - the large object id a repaired row could not resolve, anything a
     * future format change leaves behind - costs the trail of one message and nothing else. The session still opens.
     */
    @Test
    void shouldReadAnUnreadableToolActivityAsNoActivity() {
        long sessionId = saveSessionWithToolActivity(null);
        overwriteToolActivityColumn(sessionId, "19841");

        assertThat(reloadToolActivity(sessionId)).isNull();
    }

    private long saveSessionWithToolActivity(List<PyrisActivityDTO> toolActivity) {
        var session = new IrisChatSession(course, student);
        var message = new IrisMessage();
        message.setSender(IrisMessageSender.LLM);
        message.setToolActivity(toolActivity);
        message.setSession(session);
        session.getMessages().add(message);
        return irisSessionRepository.save(session).getId();
    }

    /**
     * Reads the tool activity back through the query the chat uses to open a session, so the value comes from the
     * database rather than from the persistence context that wrote it.
     */
    private List<PyrisActivityDTO> reloadToolActivity(long sessionId) {
        var reloaded = irisChatSessionRepository.findLatestByEntityIdAndChatModeAndUserIdWithMessages(course.getId(), IrisChatMode.COURSE_CHAT, student.getId(),
                PageRequest.ofSize(1));

        assertThat(reloaded).singleElement().extracting(IrisChatSession::getId).isEqualTo(sessionId);
        return reloaded.getFirst().getMessages().getFirst().getToolActivity();
    }

    private String rawToolActivity(long sessionId) {
        return jdbcClient.sql("SELECT tool_activity FROM iris_message WHERE session_id = ?").param(sessionId).query(String.class).single();
    }

    /**
     * Writes the column directly, to set up a row the mapping would not produce: one holding a value written by
     * another database, or one left behind by an older mapping. The write needs a transaction of its own, because the
     * connection pool is configured without auto-commit and would otherwise discard it on close.
     */
    private void overwriteToolActivityColumn(long sessionId, String toolActivity) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcClient.sql("UPDATE iris_message SET tool_activity = ? WHERE session_id = ?").params(toolActivity, sessionId).update());
    }
}
