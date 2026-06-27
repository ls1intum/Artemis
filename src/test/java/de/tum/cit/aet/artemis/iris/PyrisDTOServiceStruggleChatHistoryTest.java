package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextMessageContentDTO;

class PyrisDTOServiceStruggleChatHistoryTest {

    private static IrisMessage msg(IrisMessageSender sender, IrisMessageOrigin origin, IrisProactiveOutcome outcome, Boolean helpful, String text) {
        return msg(sender, origin, outcome, helpful, text, ZonedDateTime.now());
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
        var dismissed = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, IrisProactiveOutcome.DISMISSED, null, "try edge cases");
        var engaged = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "check the loop bound");
        var reply = msg(IrisMessageSender.USER, null, null, null, "thanks!");
        var pending = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "consider null input");
        var normal = msg(IrisMessageSender.LLM, null, null, null, "here is the answer");

        var out = new PyrisDTOService(null).toPyrisMessageDTOListForStruggle(List.of(dismissed, engaged, reply, pending, normal));

        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint, dismissed) try edge cases");
        assertThat(firstText(out.get(1))).isEqualTo("(proactive hint, engaged) check the loop bound");
        assertThat(firstText(out.get(2))).isEqualTo("thanks!");
        assertThat(firstText(out.get(3))).isEqualTo("(proactive hint) consider null input");
        assertThat(firstText(out.get(4))).isEqualTo("here is the answer");
    }

    @Test
    void supersededPendingHintIsMarkedIgnored() {
        var older = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "first hint");
        var newer = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "second hint");

        var out = new PyrisDTOService(null).toPyrisMessageDTOListForStruggle(List.of(older, newer));

        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint, ignored) first hint");
        assertThat(firstText(out.get(1))).isEqualTo("(proactive hint) second hint");
    }

    @Test
    void replyOutsideEngagedWindowIsNotEngaged() {
        var base = ZonedDateTime.now();
        var hint = msg(IrisMessageSender.LLM, IrisMessageOrigin.PROACTIVE_STRUGGLE, null, null, "early hint", base);
        var lateReply = msg(IrisMessageSender.USER, null, null, null, "much later", base.plusMinutes(30));

        var out = new PyrisDTOService(null).toPyrisMessageDTOListForStruggle(List.of(hint, lateReply));

        // A reply 30 min later is too late to count as engagement with this hint -> pending, not engaged.
        assertThat(firstText(out.get(0))).isEqualTo("(proactive hint) early hint");
    }
}
