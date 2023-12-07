package de.tum.in.www1.artemis.service.iris.session.exercisecreation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record IrisExerciseMetadataDTO(String title, String shortName, List<String> categories, String difficulty, String participation, boolean allowOfflineIDE,
        boolean allowOnlineEditor, boolean publishBuildPlan, String programmingLanguage, String includeInCourseScore, int points, int bonusPoints, String submissionPolicy) {

    public static IrisExerciseMetadataDTO parse(JsonNode jsonNode) {
        return new IrisExerciseMetadataDTO(jsonNode.required("title").asText(), jsonNode.required("short_name").asText(), toList(jsonNode.required("categories")),
                jsonNode.required("difficulty").asText(), jsonNode.required("participation").asText(), jsonNode.required("allow_offline_IDE").asBoolean(),
                jsonNode.required("allow_online_editor").asBoolean(), jsonNode.required("publish_build_plan").asBoolean(), jsonNode.required("programming_language").asText(),
                jsonNode.required("include_in_course_score").asText(), jsonNode.required("points").asInt(), jsonNode.required("bonus_points").asInt(),
                jsonNode.required("submission_policy").asText());
    }

    private static List<String> toList(JsonNode jsonNode) {
        if (jsonNode.isArray()) {
            List<String> list = new ArrayList<>();
            jsonNode.forEach(node -> list.add(node.asText()));
            return list;
        }
        return List.of();
    }
}
