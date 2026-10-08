package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Covers the stack walk that names the repository method and the application caller of a query.
 */
class QueryCallSiteTest {

    @NoRepositoryBean
    interface ExampleRepository extends Repository<Object, Long> {

        QueryCallSite findByExerciseId();
    }

    /**
     * A JDK dynamic proxy for {@link ExampleRepository}, as Spring Data creates one. Its handler
     * calls {@link QueryCallSite#resolve()} through a method handle rather than a lambda, so that,
     * like the Hibernate/JDBC frames in a real query, no application frame sits between the proxy
     * and the stack walk.
     */
    private static ExampleRepository repositoryProxy() throws ReflectiveOperationException {
        MethodHandle resolve = MethodHandles.lookup().findStatic(QueryCallSite.class, "resolve", MethodType.methodType(QueryCallSite.class));
        InvocationHandler handler = MethodHandleProxies.asInterfaceInstance(InvocationHandler.class,
                MethodHandles.dropArguments(resolve, 0, Object.class, Method.class, Object[].class));
        return (ExampleRepository) Proxy.newProxyInstance(QueryCallSiteTest.class.getClassLoader(), new Class<?>[] { ExampleRepository.class }, handler);
    }

    @Test
    void namesTheRepositoryInterfaceBehindTheProxyAndTheCallingMethod() throws ReflectiveOperationException {
        QueryCallSite site = repositoryProxy().findByExerciseId();

        assertThat(site.repositoryMethod()).isEqualTo("ExampleRepository.findByExerciseId");
        assertThat(site.callerMethod()).isEqualTo("QueryCallSiteTest.namesTheRepositoryInterfaceBehindTheProxyAndTheCallingMethod");
    }

    @Test
    void queryOutsideARepositoryHasOnlyACaller() {
        QueryCallSite site = QueryCallSite.resolve();

        assertThat(site.repositoryMethod()).isNull();
        assertThat(site.callerMethod()).isEqualTo("QueryCallSiteTest.queryOutsideARepositoryHasOnlyACaller");
    }

    @Test
    void classesOutsideTheApplicationAreNoRepository() {
        assertThat(QueryCallSite.repositoryName(String.class)).isNull();
        assertThat(QueryCallSite.repositoryName(ExampleRepository.class)).isEqualTo("ExampleRepository");
    }
}
