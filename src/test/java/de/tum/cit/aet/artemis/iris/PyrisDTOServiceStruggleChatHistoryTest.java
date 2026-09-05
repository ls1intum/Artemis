package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextMessageContentDTO;

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

        var out = new PyrisDTOService(null, null).toPyrisMessageDTOListForStruggle(List.of(dismissed, engaged, reply, pending, normal));

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

        var out = new PyrisDTOService(null, null).toPyrisMessageDTOListForStruggle(List.of(older, newer));

        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint, ignored) first hint");
        assertThat(firstText(out.get(1))).isEqualTo("(proactive hint) second hint");
    }

    @Test
    void replyOutsideEngagedWindowIsNotEngaged() {
        var hint = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "early hint", 0);
        var lateReply = msg(IrisMessageSender.USER, null, null, null, "much later", (int) TimeUnit.MINUTES.toSeconds(30));

        var out = new PyrisDTOService(null, null).toPyrisMessageDTOListForStruggle(List.of(hint, lateReply));

        // A reply 30 min later is too late to count as engagement with this hint -> pending, not engaged.
        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint) early hint");
    }

    @Test
    void interruptedHintGetsDerivedTag_neutralOrIgnored() {
        // INTERRUPTED has no explicit branch (like RECOVERED/ABANDONED): it falls through to the derived tag.
        // Alone -> neutral; superseded by a later proactive hint -> ignored.
        var interrupted = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, IrisProactiveOutcome.INTERRUPTED, null, "left mid-hint", 0);
        var later = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "new hint", 1);

        var neutral = new PyrisDTOService(null, null).toPyrisMessageDTOListForStruggle(List.of(interrupted));
        assertThat(firstText(neutral.get(0))).isEqualTo("(proactive hint) left mid-hint");

        var superseded = new PyrisDTOService(null, null).toPyrisMessageDTOListForStruggle(List.of(interrupted, later));
        assertThat(firstText(superseded.get(0))).isEqualTo("(proactive hint, ignored) left mid-hint");
    }
}
