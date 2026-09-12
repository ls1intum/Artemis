package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextMessageContentDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

class PyrisDTOServiceStruggleChatHistoryTest {

    /**
     * Fixed, because every tag these tests assert is derived from the ORDER of the messages. Taking the clock for it
     * made that order depend on the clock's resolution: two adjacent {@code now()} calls can return the same instant,
     * and a tag that needs a strictly later message then flips.
     */
    private static final ZonedDateTime BASE = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    private static IrisMessage msg(IrisMessageSender sender, IrisMessageOrigin origin, IrisProactiveOutcome outcome, Boolean helpful, String text, int secondsAfterBase) {
        return msg(sender, origin, outcome, helpful, text, BASE.plusSeconds(secondsAfterBase));
    }

    private static IrisMessage msg(IrisMessageSender sender, IrisMessageOrigin origin, IrisProactiveOutcome outcome, Boolean helpful, String text, ZonedDateTime sentAt) {
        var m = new IrisMessage();
        m.setSender(sender);
        m.setOrigin(origin);
        m.setProactiveOutcome(outcome);
        m.setHelpful(helpful);
        m.setSentAt(sentAt);
        m.addContent(new IrisTextMessageContent(text));
        return m;
    }

    /** A proactive hint stamped with the episode it was given in, so the earlier-episode qualifier has something to compare. */
    private static IrisMessage hintInEpisode(String episodeId, IrisProactiveOutcome outcome, Boolean helpful, String text, int secondsAfterBase) {
        var m = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, outcome, helpful, text, secondsAfterBase);
        m.setProactiveEpisodeId(episodeId);
        return m;
    }

    private static List<String> tagsFor(List<IrisMessage> messages, String currentEpisodeId) {
        return new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisMessageDTOListForStruggle(messages, currentEpisodeId).stream()
                .map(PyrisDTOServiceStruggleChatHistoryTest::firstText).toList();
    }

    private static String firstText(de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO dto) {
        return ((PyrisTextMessageContentDTO) dto.contents().get(0)).textContent();
    }

    @Test
    void annotatesProactiveMessagesByOutcome() {
        var dismissed = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, IrisProactiveOutcome.DISMISSED, null, "try edge cases", 0);
        var engaged = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "check the loop bound", 1);
        // Strictly after the hint it engages with, and inside the engagement window.
        var reply = msg(IrisMessageSender.USER, null, null, null, "thanks!", 2);
        var pending = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "consider null input", 3);
        var normal = msg(IrisMessageSender.LLM, null, null, null, "here is the answer", 4);

        var out = new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisMessageDTOListForStruggle(List.of(dismissed, engaged, reply, pending, normal), null);

        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint, dismissed) try edge cases");
        assertThat(firstText(out.get(1))).isEqualTo("(proactive hint, engaged) check the loop bound");
        assertThat(firstText(out.get(2))).isEqualTo("thanks!");
        assertThat(firstText(out.get(3))).isEqualTo("(proactive hint) consider null input");
        assertThat(firstText(out.get(4))).isEqualTo("here is the answer");
    }

    @Test
    void supersededPendingHintIsMarkedIgnored() {
        var older = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "first hint", 0);
        var newer = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "second hint", 1);

        var out = new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisMessageDTOListForStruggle(List.of(older, newer), null);

        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint, ignored) first hint");
        assertThat(firstText(out.get(1))).isEqualTo("(proactive hint) second hint");
    }

    @Test
    void replyOutsideEngagedWindowIsNotEngaged() {
        var hint = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "early hint", 0);
        var lateReply = msg(IrisMessageSender.USER, null, null, null, "much later", (int) TimeUnit.MINUTES.toSeconds(30));

        var out = new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisMessageDTOListForStruggle(List.of(hint, lateReply), null);

        // A reply 30 min later is too late to count as engagement with this hint -> pending, not engaged.
        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint) early hint");
    }

    @Test
    void interruptedHintGetsDerivedTag_neutralOrIgnored() {
        // INTERRUPTED has no explicit branch (like RECOVERED/ABANDONED): it falls through to the derived tag.
        // Alone -> neutral; superseded by a later proactive hint -> ignored.
        var interrupted = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, IrisProactiveOutcome.INTERRUPTED, null, "left mid-hint", 0);
        var later = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "new hint", 1);

        var neutral = new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisMessageDTOListForStruggle(List.of(interrupted), null);
        assertThat(firstText(neutral.get(0))).isEqualTo("(proactive hint) left mid-hint");

        var superseded = new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisMessageDTOListForStruggle(List.of(interrupted, later), null);
        assertThat(firstText(superseded.get(0))).isEqualTo("(proactive hint, ignored) left mid-hint");
    }

    @Test
    void theStruggleExerciseDTOCarriesNoRepositoryContents() {
        // The struggle trigger fires on its own while a student works, and Pyris reads only the problem statement from
        // the exercise, so the three repositories must not be walked and shipped with every one of them.
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        exercise.setTitle("Sorting");
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement merge sort.");

        var dto = new PyrisDTOService(null, null, new IrisProactiveProperties()).toPyrisProgrammingExerciseMetadataDTO(exercise);

        assertThat(dto.problemStatement()).isEqualTo("Implement merge sort.");
        assertThat(dto.templateRepository()).isEmpty();
        assertThat(dto.solutionRepository()).isEmpty();
        assertThat(dto.testRepository()).isEmpty();
    }

    @Test
    void hintFromAnEarlierEpisodeIsMarkedInEveryTagForm() {
        // All four reaction words survive the qualifier. Dropping one of them - dismissed above all - would tell the
        // gate that a rejected hint is merely old, and the prompt would then let it be repeated.
        var dismissed = hintInEpisode("ep-old", IrisProactiveOutcome.DISMISSED, null, "try edge cases", 0);
        var engaged = hintInEpisode("ep-old", null, Boolean.TRUE, "check the loop bound", 1);
        var ignored = hintInEpisode("ep-old", null, null, "consider null input", 2);
        var pending = hintInEpisode("ep-old", null, null, "look at the accumulator", 3);

        var tags = tagsFor(List.of(dismissed, engaged, ignored, pending), "ep-now");

        assertThat(tags.get(0)).startsWith("(proactive hint from an earlier episode, dismissed) ");
        assertThat(tags.get(1)).startsWith("(proactive hint from an earlier episode, engaged) ");
        assertThat(tags.get(2)).startsWith("(proactive hint from an earlier episode, ignored) ");
        assertThat(tags.get(3)).startsWith("(proactive hint from an earlier episode) ");
    }

    @Test
    void hintFromTheCurrentEpisodeIsNotMarkedAsEarlier() {
        var pending = hintInEpisode("ep-now", null, null, "consider null input", 0);

        assertThat(tagsFor(List.of(pending), "ep-now")).containsExactly("(proactive hint) consider null input");
    }

    @Test
    void anUnknownCurrentEpisodeMarksNothingAsEarlier() {
        // An older client sends no episode, so the relation is unknown rather than "earlier". Leaving the hint under
        // today's tag keeps the gate suppressing a repeat, which is the safe direction to be wrong in.
        var dismissed = hintInEpisode("ep-old", IrisProactiveOutcome.DISMISSED, null, "try edge cases", 0);

        assertThat(tagsFor(List.of(dismissed), null)).containsExactly("(proactive hint, dismissed) try edge cases");
    }

    @Test
    void aHintWithoutAnEpisodeIsNeverMarkedAsEarlier() {
        // Rows persisted before episodes existed carry no id, so there is nothing to compare against.
        var legacy = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, IrisProactiveOutcome.DISMISSED, null, "try edge cases", 0);

        assertThat(tagsFor(List.of(legacy), "ep-now")).containsExactly("(proactive hint, dismissed) try edge cases");
    }
}
