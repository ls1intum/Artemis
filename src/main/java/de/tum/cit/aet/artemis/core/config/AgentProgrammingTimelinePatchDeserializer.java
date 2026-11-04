package de.tum.cit.aet.artemis.core.config;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.dto.agent.AgentProgrammingTimelinePatchDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;

/** Reads typed scalar values and their presence for an MCP timeline update without a JSON tree. */
public class AgentProgrammingTimelinePatchDeserializer extends ValueDeserializer<AgentProgrammingTimelinePatchDTO> {

    @Override
    public AgentProgrammingTimelinePatchDTO deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.isExpectedStartObjectToken()) {
            return context.reportInputMismatch(AgentProgrammingTimelinePatchDTO.class, "timeline must be an object");
        }
        Long id = null;
        ZonedDateTime releaseDate = null;
        ZonedDateTime startDate = null;
        ZonedDateTime dueDate = null;
        AssessmentType assessmentType = null;
        ZonedDateTime assessmentDueDate = null;
        ZonedDateTime solutionDate = null;
        ZonedDateTime buildDate = null;
        Set<String> fields = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            fields.add(field);
            switch (field) {
                case "id" -> id = parser.currentToken() == JsonToken.VALUE_NULL ? null : STRICT_LONG.deserialize(parser, context);
                case "releaseDate" -> releaseDate = read(parser, context, ZonedDateTime.class);
                case "startDate" -> startDate = read(parser, context, ZonedDateTime.class);
                case "dueDate" -> dueDate = read(parser, context, ZonedDateTime.class);
                case "assessmentType" -> assessmentType = read(parser, context, AssessmentType.class);
                case "assessmentDueDate" -> assessmentDueDate = read(parser, context, ZonedDateTime.class);
                case "exampleSolutionPublicationDate" -> solutionDate = read(parser, context, ZonedDateTime.class);
                case "buildAndTestStudentSubmissionsAfterDueDate" -> buildDate = read(parser, context, ZonedDateTime.class);
                default -> context.reportInputMismatch(AgentProgrammingTimelinePatchDTO.class, "Unknown timeline field: %s", field);
            }
        }
        return new AgentProgrammingTimelinePatchDTO(
                new ProgrammingExerciseTimelineUpdateDTO(id, releaseDate, startDate, dueDate, assessmentType, assessmentDueDate, solutionDate, buildDate), Set.copyOf(fields));
    }

    private static final StrictLongDeserializer STRICT_LONG = new StrictLongDeserializer();

    private static <T> T read(JsonParser parser, DeserializationContext context, Class<T> type) {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : context.readValue(parser, type);
    }
}
