package de.tum.in.www1.artemis.util;

import org.assertj.core.api.AbstractAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryCountAssert<T, E extends Exception> extends AbstractAssert<QueryCountAssert<T, E>, ThrowingProducer<T, E>> {

    private static final Logger log = LoggerFactory.getLogger(QueryCountAssert.class);

    private final HibernateQueryInterceptor interceptor;

    protected QueryCountAssert(HibernateQueryInterceptor interceptor, ThrowingProducer<T, E> tProducer, Class<?> selfType) {
        super(tProducer, selfType);
        this.interceptor = interceptor;
    }

    public static <T, E extends Exception> QueryCountAssert<T, E> assertThatDb(HibernateQueryInterceptor interceptor, ThrowingProducer<T, E> call) {
        return new QueryCountAssert<>(interceptor, call, QueryCountAssert.class);
    }

    /**
     * Asserts that the number of database queries executed during a REST call exactly matches the expected count
     *
     * @param times the amount of queries expected on the database
     * @return the result of the original call for which this assertion has been performed
     * @throws AssertionError if the expected calls do not match the actual queries on the database
     */
    public T hasBeenCalledTimes(long times) throws E {
        var result = performCall();
        if (result.callCount != times) {
            throw failureWithActualExpected(interceptor.getQueryCount(), times, "Expected <%d> queries, but <%d> were performed.", times, result.callCount);
        }
        log.info("result.callCount: {}", result.callCount);
        return result.result;
    }

    /**
     * Asserts that the number of database queries executed during a REST call don't exceed the expected count
     *
     * @param times the maximal amount of queries expected on the database
     * @return the result of the original call for which this assertion has been performed
     * @throws AssertionError if the number of actual queries on the database exceed the expected calls
     */
    public T hasBeenCalledAtMostTimes(long times) throws E {
        var result = performCall();
        if (result.callCount > times) {
            throw failureWithActualExpected(interceptor.getQueryCount(), times, "Expected at most <%d> queries, but <%d> were performed.", times, result.callCount);
        }
        log.info("result.callCount: {}", result.callCount);
        return result.result;
    }

    private record CallResult<T>(T result, long callCount) {
    }

    private CallResult<T> performCall() throws E {
        interceptor.startQueryCount();
        T result = actual.call();
        long callCount = interceptor.getQueryCount();
        return new CallResult<>(result, callCount);
    }
}
