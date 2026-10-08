"""Tests for the yes/no rules of the static query checker, on small hand-written Java sources.

Run: python3 supporting_scripts/test_find_slow_queries.py
"""

import contextlib
import io
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import find_slow_queries as checker  # noqa: E402


ENTITIES = {
    "Competency.java": """
        @Entity
        public class Competency extends CourseCompetency {
        }
    """,
    "CourseCompetency.java": """
        @Entity
        public abstract class CourseCompetency {
            @OneToMany(mappedBy = "competency")
            private Set<CompetencyExerciseLink> exerciseLinks = new HashSet<>();

            @OneToMany(mappedBy = "competency")
            private Set<CompetencyLectureUnitLink> lectureUnitLinks = new HashSet<>();

            @ManyToOne
            private Course course;
        }
    """,
    "Course.java": """
        /**
         * This class is a course; the word class in this comment is no declaration.
         */
        @Entity
        public class Course {
            @OneToMany(mappedBy = "course", fetch = FetchType.EAGER)
            private Set<Exercise> exercises = new HashSet<>();

            @OneToMany(mappedBy = "course")
            private Set<Lecture> lectures = new HashSet<>();
        }
    """,
    "Exercise.java": """
        @Entity
        public class Exercise {
            @OneToMany(mappedBy = "exercise")
            private Set<Result> results = new HashSet<>();
        }
    """,
    "Result.java": """
        @Entity
        public class Result {
            @OneToMany(mappedBy = "result")
            private List<Feedback> feedbacks = new ArrayList<>();
        }
    """,
}


def quietly(function, *args):
    with contextlib.redirect_stdout(io.StringIO()):
        return function(*args)


def learn_entities():
    entities, superclass_of = {}, {}
    for content in ENTITIES.values():
        checker.collect_entity_facts(content, entities, superclass_of)
    return entities, superclass_of


def query_shape_findings(repository_source):
    entities, superclass_of = learn_entities()
    findings = []
    quietly(checker.check_query_shapes, "CompetencyRepository.java", repository_source, entities, superclass_of, findings)
    return findings


class QueryShapeRulesTest(unittest.TestCase):

    def test_sibling_collections_in_one_query_are_flagged(self):
        findings = query_shape_findings('''
            interface CompetencyRepository extends ArtemisJpaRepository<Competency, Long> {
                @Query("""
                        SELECT c FROM Competency c
                            LEFT JOIN FETCH c.exerciseLinks el
                            LEFT JOIN FETCH c.lectureUnitLinks
                        WHERE c.id = :id
                        """)
                Optional<Competency> findWithLinksById(@Param("id") long id);
            }
        ''')
        self.assertEqual([f["key"] for f in findings], ["multiple_collection_fetch:CompetencyRepository.findWithLinksById:c"])

    def test_a_chain_of_collections_and_to_one_fetches_are_not_flagged(self):
        findings = query_shape_findings('''
            interface ExerciseRepository extends ArtemisJpaRepository<Exercise, Long> {
                @Query("""
                        SELECT e FROM Exercise e LEFT JOIN FETCH e.results r LEFT JOIN FETCH r.feedbacks
                        """)
                List<Exercise> findWithResultsAndFeedbacks();

                @Query("SELECT c FROM Competency c LEFT JOIN FETCH c.course LEFT JOIN FETCH c.exerciseLinks")
                List<Competency> findWithCourse();
            }
        ''')
        self.assertEqual(findings, [])

    def test_entity_graph_paths_are_resolved_from_the_repository_entity(self):
        findings = query_shape_findings('''
            interface CourseRepository extends ArtemisJpaRepository<Course, Long> {
                @EntityGraph(type = LOAD, attributePaths = { "lectures", "exercises.results" })
                Optional<Course> findWithEagerLecturesAndExercisesById(long id);
            }
        ''')
        self.assertEqual(len(findings), 1)
        self.assertIn("exercises", findings[0]["detail"])
        self.assertIn("lectures", findings[0]["detail"])

    def test_collection_fetch_in_a_paged_method_is_flagged(self):
        findings = query_shape_findings('''
            interface ExerciseRepository extends ArtemisJpaRepository<Exercise, Long> {
                @Query("SELECT e FROM Exercise e LEFT JOIN FETCH e.results WHERE e.id IN :ids")
                Page<Exercise> findWithResults(@Param("ids") Set<Long> ids, Pageable pageable);
            }
        ''')
        self.assertEqual([f["type"] for f in findings], ["pageable_collection_fetch"])

    def test_eager_to_many_is_flagged_and_inherited_fields_are_found(self):
        entities, superclass_of = learn_entities()
        findings = []
        quietly(checker.check_eager_to_many, entities, {}, findings)
        self.assertEqual([f["key"] for f in findings], ["eager_to_many:Course.exercises"])
        self.assertEqual(checker.lookup_field("Competency", "exerciseLinks", entities, superclass_of)[0], "many")


REPOSITORY_LOOPS = r'''
class StatsService {
    private final ParticipationRepository participationRepository;
    private final AuxiliaryRepository auxiliaryRepository; // an entity type, not a Spring Data repository
    private final ResultRepository resultRepository;

    void forEachLoop(List<Exercise> exercises) {
        for (Exercise e : exercises) {
            participationRepository.findByExerciseId(e.getId());
        }
    }
    void classicFor(long[] ids) {
        for (int i = 0; i < ids.length; i++) participationRepository.countByExerciseId(ids[i]);
    }
    void whileLoop(Iterator<Long> it) {
        while (it.hasNext()) { resultRepository.findById(it.next()); }
    }
    void doLoop() {
        do { this.resultRepository.save(null); } while (more());
    }
    void streamMap(List<Long> ids) {
        ids.stream().filter(Objects::nonNull).map(id -> participationRepository.findByIdElseThrow(id)).toList();
    }
    void streamAfterLookup(List<Long> ids, Map<Long, Long> m) {
        ids.stream().filter(x -> m.get(x) != null).map(id -> participationRepository.findByExerciseId(id)).toList();
    }
    void iterableForEach(List<Long> ids) {
        ids.forEach(id -> {
            resultRepository.deleteById(id);
        });
    }
    void methodReference(List<Long> ids) {
        ids.stream().map(resultRepository::findById).toList();
    }
    void collectorToMap(List<Exercise> exercises) {
        exercises.stream().collect(Collectors.toMap(Exercise::getId, e -> participationRepository.countByExerciseId(e.getId())));
    }
    void nested(List<List<Long>> groups) {
        for (List<Long> g : groups) { for (Long id : g) { resultRepository.findById(id); } }
    }

    // ---- not flagged ----
    void optionalMap(long id) {
        resultRepository.findById(id).map(r -> resultRepository.findByIdElseThrow(r.getId()));
    }
    void optionalAfterTerminal(List<Submission> subs) {
        subs.stream().max(Submission::compareTo).flatMap(sub -> resultRepository.findById(sub.getId()));
    }
    void notInLoop(List<Long> ids) {
        resultRepository.findAllById(ids);
    }
    void commentedOut(List<Long> ids) {
        for (Long id : ids) {
            // resultRepository.findById(id);
            /* participationRepository.findByExerciseId(id); */
            log.info("resultRepository.findById({}) for {", id);
        }
    }
    void entityNamedRepository(List<Long> ids) {
        for (Long id : ids) { auxiliaryRepository.getName(); }
    }
    void referenceProxy(List<Long> ids) {
        for (Long id : ids) { resultRepository.getReferenceById(id); }
    }
    void loopAfterCall(List<Long> ids) {
        var all = resultRepository.findAllById(ids);
        for (Result r : all) { r.getScore(); }
    }
}
'''


class RepositoryCallInLoopTest(unittest.TestCase):

    def test_calls_inside_loops_are_flagged_once_and_nothing_else(self):
        findings = []
        quietly(checker.check_repository_calls_in_loops, "StatsService.java", REPOSITORY_LOOPS, {"ParticipationRepository", "ResultRepository"}, findings)
        found = sorted((f["member"].split(".")[1], f["repositoryMethod"].split(".")[1]) for f in findings)
        self.assertEqual(found, sorted([
            ("forEachLoop", "findByExerciseId"),
            ("classicFor", "countByExerciseId"),
            ("whileLoop", "findById"),
            ("doLoop", "save"),
            ("streamMap", "findByIdElseThrow"),
            ("streamAfterLookup", "findByExerciseId"),
            ("iterableForEach", "deleteById"),
            ("methodReference", "findById"),
            ("collectorToMap", "countByExerciseId"),
            ("nested", "findById"),
        ]))

    def test_comments_and_strings_are_blanked_without_moving_code(self):
        source = 'a = "x{//y}"; // c {\n/* d\n e */ \'{\' """\nq{\n""" b'
        blanked = checker.blank_comments_and_strings(source)
        self.assertEqual(len(blanked), len(source))
        self.assertEqual(blanked.count("\n"), source.count("\n"))
        self.assertNotIn("{", blanked)
        self.assertTrue(blanked.startswith('a = "') and blanked.endswith('""" b'))


if __name__ == "__main__":
    unittest.main()
