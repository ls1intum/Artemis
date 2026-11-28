package de.tum.cit.aet.artemis.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import jakarta.persistence.QueryHint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.JpaQueryMethod;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

import de.tum.cit.aet.artemis.communication.repository.CourseHomeCommunicationRepository;
import de.tum.cit.aet.artemis.core.repository.base.InteractiveQueryTimeout;
import de.tum.cit.aet.artemis.course.repository.CourseHomeRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseHomeRepository;

/**
 * The read queries of the student course home are cancelled after {@link InteractiveQueryTimeout#MILLISECONDS}.
 * <p>
 * The test asks Spring Data how it reads each query method, rather than only looking for the annotation, because the
 * limit is a composed annotation and it only counts if Spring Data resolves it to the JPA hint. A query that is added
 * to one of these repositories without it fails here, instead of being the one that holds a connection for minutes.
 */
class InteractiveQueryTimeoutTest {

    private static final List<Class<?>> HOME_REPOSITORIES = List.of(ExerciseHomeRepository.class, CourseHomeRepository.class, CourseHomeCommunicationRepository.class);

    static Stream<Method> homeQueries() {
        return HOME_REPOSITORIES.stream().flatMap(repository -> Arrays.stream(repository.getDeclaredMethods())).filter(method -> method.isAnnotationPresent(Query.class));
    }

    @Test
    void thereAreHomeQueriesToCheck() {
        // A refactoring that moves the queries must not turn the test below into one that checks nothing.
        assertThat(homeQueries()).hasSizeGreaterThanOrEqualTo(15);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("homeQueries")
    void everyHomeQueryIsCancelledAfterTheInteractiveLimit(Method method) throws ReflectiveOperationException {
        assertThat(method.isAnnotationPresent(Modifying.class)).as("only read queries are limited, a write must not be cancelled half way").isFalse();

        var queryMethod = new JpaQueryMethod(method, new DefaultRepositoryMetadata(method.getDeclaringClass()), new SpelAwareProxyProjectionFactory(),
                PersistenceProvider.HIBERNATE);

        assertThat(hintsOf(queryMethod)).as("hints Spring Data applies to %s", method.getName()).extracting(QueryHint::name, QueryHint::value)
                .contains(tuple("jakarta.persistence.query.timeout", InteractiveQueryTimeout.MILLISECONDS));
    }

    /**
     * Spring Data does not expose the resolved hints, so this reads them the way it does itself. If a Spring Data upgrade
     * renames the method this fails with a NoSuchMethodException, which is the cue to look at how the hints are read now.
     */
    @SuppressWarnings("unchecked")
    private static List<QueryHint> hintsOf(JpaQueryMethod queryMethod) throws ReflectiveOperationException {
        Method getHints = JpaQueryMethod.class.getDeclaredMethod("getHints");
        getHints.setAccessible(true);
        return (List<QueryHint>) getHints.invoke(queryMethod);
    }
}
