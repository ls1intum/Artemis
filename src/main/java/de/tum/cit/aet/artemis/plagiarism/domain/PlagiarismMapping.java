package de.tum.cit.aet.artemis.plagiarism.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.core.domain.User;

public record PlagiarismMapping(Map<Long, Map<Long, PlagiarismCase>> studentIdToExerciseIdToPlagiarismCaseMap) {

    /**
     * Factory method to create a PlagiarismMapping from a PlagiarismCase collection.
     * Useful for creating PlagiarismMapping from a database repository response.
     *
     * @param plagiarismCases a collection of relavant plagiarism cases with student/team ids and exercise ids present
     * @return a populated PlagiarismMapping instance
     */
    public static PlagiarismMapping createFromPlagiarismCases(Collection<PlagiarismCase> plagiarismCases) {
        Map<Long, Map<Long, PlagiarismCase>> outerMap = new HashMap<>();
        for (PlagiarismCase plagiarismCase : plagiarismCases) {
            for (User student : plagiarismCase.getStudents()) {
                var innerMap = outerMap.computeIfAbsent(student.getId(), studentId -> new HashMap<>());
                innerMap.put(plagiarismCase.getExercise().getId(), plagiarismCase);
            }
        }
        return new PlagiarismMapping(outerMap);
    }

    /**
     * Returns an empty PlagiarismMapping.
     */
    public static PlagiarismMapping empty() {
        return new PlagiarismMapping(Collections.emptyMap());
    }

    public PlagiarismCase getPlagiarismCase(Long studentId, Long exerciseId) {
        var innerMap = studentIdToExerciseIdToPlagiarismCaseMap.get(studentId);
        return innerMap != null ? innerMap.get(exerciseId) : null;
    }

    public Map<Long, PlagiarismCase> getPlagiarismCasesForStudent(Long studentId) {
        return studentIdToExerciseIdToPlagiarismCaseMap.getOrDefault(studentId, Collections.emptyMap());
    }

    public boolean studentHasVerdict(Long studentId, PlagiarismVerdict plagiarismVerdict) {
        var innerMap = getPlagiarismCasesForStudent(studentId);
        return innerMap.values().stream().anyMatch(plagiarismCase -> plagiarismVerdict.equals(plagiarismCase.getVerdict()));
    }
}
