package de.tum.cit.aet.artemis.core.service.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kinds of exercise a tool can create, read and change. The name is part of the path of the endpoints.
 */
public enum AgentExerciseType {

    @JsonProperty("text")
    TEXT("text"),

    @JsonProperty("modeling")
    MODELING("modeling"),

    @JsonProperty("programming")
    PROGRAMMING("programming");

    private final String path;

    AgentExerciseType(String path) {
        this.path = path;
    }

    /**
     * The path of the endpoints of this kind of exercise, such as {@code /api/text/text-exercises}.
     *
     * @return the path
     */
    public String exercisesPath() {
        return "/api/" + path + "/" + path + "-exercises";
    }

    /**
     * The path of the list of the exercises of this kind in a course.
     *
     * @param courseId the course
     * @return the path
     */
    public String courseExercisesPath(long courseId) {
        return "/api/" + path + "/courses/" + courseId + "/" + path + "-exercises";
    }
}
