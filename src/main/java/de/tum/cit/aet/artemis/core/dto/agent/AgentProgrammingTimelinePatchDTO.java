package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.AgentProgrammingTimelinePatchDeserializer;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;

/**
 * A partial MCP timeline update. The supplied fields distinguish an omitted date from an explicit null that clears it.
 *
 * @param values         the supplied timeline values
 * @param suppliedFields names of the fields present in the request
 */
@JsonDeserialize(using = AgentProgrammingTimelinePatchDeserializer.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentProgrammingTimelinePatchDTO(ProgrammingExerciseTimelineUpdateDTO values, Set<String> suppliedFields) {

    /**
     * Completes the replacement DTO without changing values the caller omitted.
     *
     * @param current the exercise's current timeline, read with the caller's token
     * @return the replacement timeline for the REST endpoint
     */
    public ProgrammingExerciseTimelineUpdateDTO merge(ProgrammingExerciseTimelineUpdateDTO current) {
        return new ProgrammingExerciseTimelineUpdateDTO(values.id(), value("releaseDate", values.releaseDate(), current.releaseDate()),
                value("startDate", values.startDate(), current.startDate()), value("dueDate", values.dueDate(), current.dueDate()),
                value("assessmentType", values.assessmentType(), current.assessmentType()), value("assessmentDueDate", values.assessmentDueDate(), current.assessmentDueDate()),
                value("exampleSolutionPublicationDate", values.exampleSolutionPublicationDate(), current.exampleSolutionPublicationDate()),
                value("buildAndTestStudentSubmissionsAfterDueDate", values.buildAndTestStudentSubmissionsAfterDueDate(), current.buildAndTestStudentSubmissionsAfterDueDate()));
    }

    private <T> T value(String field, T supplied, T current) {
        return suppliedFields.contains(field) ? supplied : current;
    }
}
