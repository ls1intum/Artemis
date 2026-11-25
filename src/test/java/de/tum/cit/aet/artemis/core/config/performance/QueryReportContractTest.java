package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * The report the server sends is read by {@code supporting_scripts/format_query_report.py}, a separate program with no
 * compiler to notice a renamed field. This test keeps both sides on the same names: the fixture is what the formatter's
 * tests read, and the report of a real collector must have exactly the fields of the fixture. Renaming a field here fails
 * this test until the fixture, and with it the formatter, follows.
 */
class QueryReportContractTest {

    private static final Path FIXTURE = Path.of("supporting_scripts", "tests", "fixtures", "query-report.json");

    private static final String SQL = "select r1_0.id from result r1_0 where r1_0.participation_id=?";

    private static final String DUPLICATE_SQL = "select c1_0.id from course c1_0 where c1_0.id=?";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void theReportOfTheCollectorHasTheFieldsOfTheFixtureTheFormatterIsTestedWith() throws IOException {
        QueryReportDTO report = collectedReport();

        Map<?, ?> actual = mapper.readValue(mapper.writeValueAsString(report), Map.class);
        Map<?, ?> fixture = mapper.readValue(FileUtils.readFileToString(FIXTURE.toFile(), "UTF-8"), Map.class);

        assertThat(fieldPaths(actual)).containsExactlyElementsOf(fieldPaths(fixture));
    }

    /** One N+1 with a known source line, and one duplicate that two places of code issued with the same values. */
    private static QueryReportDTO collectedReport() {
        QueryCollector collector = new QueryCollector(new QueryProperties());
        QueryCallSite loop = new QueryCallSite("ResultRepository.findByParticipationId", "ResultService.loadAll",
                "src/main/java/de/tum/cit/aet/artemis/assessment/service/ResultService.java", 142);
        collector.record(SQL, 1, () -> loop);
        collector.record(SQL, 2, () -> loop);
        collector.record(DUPLICATE_SQL, 7, () -> new QueryCallSite("CourseRepository.findById", "ExerciseService.update"));
        collector.record(DUPLICATE_SQL, 7, () -> new QueryCallSite("CourseRepository.findById", "ExamService.update"));
        collector.finishRequest("GET", "/api/assessment/exercises/{exerciseId}/results", "Results.spec: opens the list", "action");
        return collector.getReport(Map.of("result", List.of(new TableIndex("idx_result_participation", List.of("participation_id")))));
    }

    /** The names of all fields, as paths: list entries share one path, and the table names of the indexes are one wildcard. */
    private static Set<String> fieldPaths(Map<?, ?> json) {
        Set<String> paths = new TreeSet<>();
        collectPaths("", json, paths);
        return paths;
    }

    private static void collectPaths(String prefix, Object value, Set<String> paths) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((name, child) -> {
                String segment = prefix.equals("indexes") ? "*" : String.valueOf(name);
                String path = prefix.isEmpty() ? segment : prefix + "." + segment;
                paths.add(path);
                collectPaths(path, child, paths);
            });
        }
        else if (value instanceof List<?> list) {
            list.forEach(child -> collectPaths(prefix + "[]", child, paths));
        }
    }
}
