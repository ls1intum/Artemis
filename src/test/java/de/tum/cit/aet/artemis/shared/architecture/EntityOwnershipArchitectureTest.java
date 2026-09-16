package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;

/**
 * Holds every entity to one of two statements: it is an {@code @AggregateRoot} and belongs to nothing, or it declares a
 * {@code @Parent} that the database requires.
 * <p>
 * The point is what an orphan costs. A row whose owning foreign key is null is unreachable from the application, so it
 * reads as deleted, and unreachable from the deletion routines, so it never is — which puts it outside account deletion
 * and the data privacy cleanup alike. The mapping alone cannot tell the two apart: a nullable {@code @ManyToOne} looks
 * identical whether it is a parent nobody made required or a reference that genuinely may be absent. The two
 * annotations make the author say which, and this test holds them to it.
 * <p>
 * Both lists below are the work that is left, not permission to add more. They may only shrink.
 *
 * @see de.tum.cit.aet.artemis.core.domain.AggregateRoot
 * @see de.tum.cit.aet.artemis.core.domain.Parent
 */
class EntityOwnershipArchitectureTest extends AbstractArchitectureTest {

    private static final String ENTITY = "jakarta.persistence.Entity";

    private static final String AGGREGATE_ROOT = "de.tum.cit.aet.artemis.core.domain.AggregateRoot";

    private static final String PARENT = "de.tum.cit.aet.artemis.core.domain.Parent";

    private static final String MAPS_ID = "jakarta.persistence.MapsId";

    private static final String JOIN_COLUMN = "jakarta.persistence.JoinColumn";

    private static final String COLUMN = "jakarta.persistence.Column";

    private static final Set<String> TO_ONE_ASSOCIATIONS = Set.of("jakarta.persistence.ManyToOne", "jakarta.persistence.OneToOne");

    private static final Path LIQUIBASE_DIRECTORY = Path.of("src", "main", "resources", "config", "liquibase");

    /**
     * Entities that are neither a root nor able to name a parent, because the foreign key lives on the parent or does
     * not exist at all. Fixing one means moving the key onto the entity, which is a migration with an application
     * change attached; {@code entity-ownership.mdx} records why each is still here.
     */
    private static final Set<String> ENTITIES_WITHOUT_A_DECLARED_PARENT = Set.of(
            // one-to-one where the parent holds the pointer, so nothing in the schema ties the child to it
            "de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile", "de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig",
            "de.tum.cit.aet.artemis.course.domain.CourseConfiguration", "de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig",
            "de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration", "de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig",
            "de.tum.cit.aet.artemis.programming.domain.build.BuildPlan", "de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig",
            "de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy", "de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy",
            "de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy", "de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration",
            // reference a parent through plain columns that carry no foreign key at all
            "de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath", "de.tum.cit.aet.artemis.programming.domain.VcsAccessLog");

    /**
     * Parents the database still lets be null. Each one is a shape that needs a decision before a constraint: the rows
     * that violate it cannot be repaired from what they carry, or the application detaches them on purpose.
     */
    private static final Set<String> PARENTS_THAT_ARE_STILL_NULLABLE = Set.of(
            // SlideSplitterService detaches superseded slides deliberately; what "superseded" should mean comes first
            "de.tum.cit.aet.artemis.lecture.domain.Slide.attachmentVideoUnit",
            // a self-referencing tree, where a null parent is a legitimate root node
            "de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea.parent",
            // rows exist today that carry no evidence of which parent they belonged to
            "de.tum.cit.aet.artemis.exercise.domain.Submission.participation", "de.tum.cit.aet.artemis.exercise.domain.participation.Participation.exercise",
            "de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement.plagiarismSubmission", "de.tum.cit.aet.artemis.assessment.domain.Feedback.result",
            "de.tum.cit.aet.artemis.quiz.domain.QuizQuestion.exercise", "de.tum.cit.aet.artemis.communication.domain.Post.conversation",
            // PlagiarismResource#deletePlagiarismComparisons detaches the submission when it cleans up a comparison
            "de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission.plagiarismComparison",
            // the exercise's collection owns the order column, so Hibernate dissociates a repository it removes
            "de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository.exercise");

    @Test
    void everyEntityIsARootOrDeclaresItsParent() {
        List<String> undeclared = entities().filter(entity -> !entity.isAnnotatedWith(AGGREGATE_ROOT)).filter(entity -> parentsOf(entity).findAny().isEmpty())
                .map(JavaClass::getFullName).filter(name -> !ENTITIES_WITHOUT_A_DECLARED_PARENT.contains(name)).distinct().sorted().toList();

        assertThat(undeclared).as("""
                Every entity says what it belongs to. Annotate the association that owns this entity's lifetime with \
                @Parent, or the entity itself with @AggregateRoot and the reason it belongs to nothing. An entity that \
                can neither is an orphan waiting to happen: unreachable from the application and from the deletion \
                routines alike.""").isEmpty();
    }

    @Test
    void noEntityIsBothARootAndOwned() {
        List<String> both = entities().filter(entity -> entity.isAnnotatedWith(AGGREGATE_ROOT)).filter(entity -> parentsOf(entity).findAny().isPresent())
                .map(JavaClass::getFullName).sorted().toList();

        assertThat(both).as("An entity is a root or it belongs to something, never both. Remove whichever of @AggregateRoot and @Parent is wrong.").isEmpty();
    }

    @Test
    void everyParentIsRequired() {
        List<String> nullable = entities().flatMap(EntityOwnershipArchitectureTest::parentsOf).filter(field -> enforcingConstraintOf(field).isEmpty())
                .filter(field -> !isRequired(field)).map(JavaField::getFullName).filter(name -> !PARENTS_THAT_ARE_STILL_NULLABLE.contains(name)).distinct().sorted().toList();

        assertThat(nullable).as("""
                A parent has to be there. Declare it with optional = false on the association, or nullable = false on \
                its @JoinColumn, and require it in the database with the matching Liquibase change. Where the entity \
                has several possible parents, name the check constraint that makes exactly one of them present in \
                @Parent(enforcedBy = ...) instead.""").isEmpty();
    }

    @Test
    void everyAlternativeParentNamesAConstraintTheSchemaMakes() {
        String changelogs = readLiquibaseChangelogs();
        List<String> unbacked = entities().flatMap(EntityOwnershipArchitectureTest::parentsOf)
                .filter(field -> enforcingConstraintOf(field).filter(constraint -> !changelogs.contains(constraint)).isPresent()).map(JavaField::getFullName).sorted().toList();

        assertThat(unbacked).as("""
                @Parent(enforcedBy = ...) claims the database makes exactly one of several parents present, so the \
                constraint it names has to exist. Add it in a Liquibase changelog, or drop the claim.""").isEmpty();
    }

    @Test
    void theRemainingWorkIsNotGrowing() {
        // The two lists are the backlog this test exists to shrink. Pinning their size makes an addition a deliberate
        // edit with a reviewer attached, rather than the path of least resistance when a new entity does not fit.
        assertThat(ENTITIES_WITHOUT_A_DECLARED_PARENT).as("entities that cannot name a parent: move the foreign key onto the entity instead of adding to this list").hasSize(14);
        assertThat(PARENTS_THAT_ARE_STILL_NULLABLE).as("parents the database still lets be null: require them instead of adding to this list").hasSize(10);
    }

    private static Stream<JavaClass> entities() {
        return productionClasses.stream().filter(javaClass -> javaClass.isAnnotatedWith(ENTITY)).sorted(Comparator.comparing(JavaClass::getFullName));
    }

    /**
     * The parent associations of an entity, including the ones it inherits: a single-table subclass belongs to whatever
     * its base class belongs to.
     *
     * @param entity the entity to read
     * @return its parent associations
     */
    private static Stream<JavaField> parentsOf(JavaClass entity) {
        return entity.getAllFields().stream().filter(field -> field.isAnnotatedWith(PARENT)).sorted(Comparator.comparing(JavaField::getFullName));
    }

    private static Optional<String> enforcingConstraintOf(JavaField field) {
        return field.getAnnotations().stream().filter(annotation -> PARENT.equals(annotation.getRawType().getName())).map(annotation -> annotation.get("enforcedBy"))
                .flatMap(Optional::stream).map(String::valueOf).filter(constraint -> !constraint.isBlank()).findFirst();
    }

    /**
     * Whether the mapping already refuses a row without this parent. A shared primary key needs no further statement:
     * the child cannot exist without the row whose key it borrows.
     *
     * @param field the parent association
     * @return true when the mapping requires it
     */
    private static boolean isRequired(JavaField field) {
        if (field.isAnnotatedWith(MAPS_ID) || field.getRawType().isPrimitive()) {
            return true;
        }
        boolean nonOptionalAssociation = field.getAnnotations().stream().filter(annotation -> TO_ONE_ASSOCIATIONS.contains(annotation.getRawType().getName()))
                .map(annotation -> annotation.get("optional")).flatMap(Optional::stream).anyMatch(optional -> Boolean.FALSE.equals(optional));
        boolean nonNullableColumn = field.getAnnotations().stream().filter(annotation -> JOIN_COLUMN.equals(annotation.getRawType().getName()))
                .map(annotation -> annotation.get("nullable")).flatMap(Optional::stream).anyMatch(nullable -> Boolean.FALSE.equals(nullable));
        boolean nonNullablePlainColumn = field.getAnnotations().stream().filter(annotation -> COLUMN.equals(annotation.getRawType().getName()))
                .map(annotation -> annotation.get("nullable")).flatMap(Optional::stream).anyMatch(nullable -> Boolean.FALSE.equals(nullable));
        return nonOptionalAssociation || nonNullableColumn || nonNullablePlainColumn;
    }

    private static String readLiquibaseChangelogs() {
        try (Stream<Path> files = Files.walk(LIQUIBASE_DIRECTORY)) {
            return files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".xml")).map(file -> {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                }
                catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }).reduce("", String::concat);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
