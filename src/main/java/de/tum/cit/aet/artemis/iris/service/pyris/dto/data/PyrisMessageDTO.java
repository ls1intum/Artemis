package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMessageDTO(@Nullable Long id, Instant sentAt, IrisMessageSender sender, List<PyrisMessageContentBaseDTO> contents) {

    /**
     * Convert an IrisMessage to a PyrisMessageDTO.
     * <p>
     * Text content is forwarded as text and other JSON content (e.g. MCQ artifacts produced by the LLM) is forwarded unchanged as JSON, exactly as before. The only special case is
     * a COMMAND marker (e.g. a past point-out): since Iris consumes it as a plain-text system note, it is rendered here into a human-readable sentence instead of being sent as raw
     * JSON.
     *
     * @param message The message to convert.
     * @return The converted message.
     */
    public static PyrisMessageDTO of(IrisMessage message) {
        var content = message.getContent().stream().map(messageContent -> {
            if (messageContent instanceof IrisTextMessageContent) {
                return (PyrisMessageContentBaseDTO) new PyrisTextMessageContentDTO(messageContent.getContentAsString());
            }
            if (messageContent instanceof IrisJsonMessageContent json) {
                // COMMAND markers are rendered into a plain-text system note; every other JSON payload is forwarded as-is.
                if (message.getSender() == IrisMessageSender.COMMAND) {
                    return (PyrisMessageContentBaseDTO) new PyrisTextMessageContentDTO(renderCommandMarker(json.getJsonNode()));
                }
                return (PyrisMessageContentBaseDTO) new PyrisJsonMessageContentDTO(messageContent.getContentAsString());
            }
            return null;
        }).filter(Objects::nonNull).toList();
        return new PyrisMessageDTO(message.getId(), toInstant(message.getSentAt()), message.getSender(), content);
    }

    /**
     * Render a COMMAND marker's JSON payload into the plain-text system note that Iris sees. Point-out markers get a dedicated, readable sentence; any other marker shape falls
     * back to its raw JSON so nothing is silently lost.
     *
     * @param marker the marker's JSON content
     * @return the text representation forwarded to Pyris
     */
    private static String renderCommandMarker(JsonNode marker) {
        if ("pointOut".equals(marker.path("type").asText(null))) {
            String note = renderPointOut(marker);
            if (note != null) {
                return note;
            }
        }
        return "System command marker from the conversation: " + marker.toString();
    }

    private static @Nullable String renderPointOut(JsonNode marker) {
        if (!marker.hasNonNull("lectureUnitId")) {
            return null;
        }
        var targets = new ArrayList<String>();
        if (marker.hasNonNull("page")) {
            targets.add("page " + marker.get("page").asInt());
        }
        if (marker.hasNonNull("timestamp")) {
            targets.add("the video at " + Math.round(marker.get("timestamp").asDouble()) + "s");
        }
        if (targets.isEmpty()) {
            return null;
        }
        long lectureUnitId = marker.get("lectureUnitId").asLong();
        String name = marker.path("lectureUnitName").asText(null);
        String unit = name != null && !name.isBlank() ? "lecture unit '" + name + "' (id " + lectureUnitId + ")" : "lecture unit " + lectureUnitId;
        String position = String.join(" and ", targets);
        return "Iris already pointed the student to " + position + " of " + unit + " in the combined view.";
    }
}
