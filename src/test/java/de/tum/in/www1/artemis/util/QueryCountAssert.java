package de.tum.in.www1.artemis.util;

import org.assertj.core.api.AbstractAssert;

public class QueryCountAssert<T, E extends Exception> extends AbstractAssert<QueryCountAssert<T, E>, ThrowingProducer<T, E>> {

    private final HibernateQueryInterceptor interceptor;

    protected QueryCountAssert(HibernateQueryInterceptor interceptor, ThrowingProducer<T, E> tProducer, Class<?> selfType) {
        super(tProducer, selfType);
        this.interceptor = interceptor;
    }

    public static <T, E extends Exception> QueryCountAssert<T, E> assertThatDb(HibernateQueryInterceptor interceptor, ThrowingProducer<T, E> call) {
        return new QueryCountAssert<>(interceptor, call, QueryCountAssert.class);
    }

    public T hasBeenCalledTimes(long times) throws E {
        var result = performCall();
        if (result.callCount != times) {
            throw failureWithActualExpected(interceptor.getQueryCount(), times, "Expected <%d> queries, but <%d> were performed.", times, result.callCount);
        }
        return result.result;
    }

    public T hasBeenCalledAtMostTimes(long times) throws E {
        var result = performCall();
        if (result.callCount > times) {
            throw failureWithActualExpected(interceptor.getQueryCount(), times, "Expected at most <%d> queries, but <%d> were performed.", times, result.callCount);
        }
        return result.result;
    }

    private record CallResult<T> (T result, long callCount) {
    }

    private CallResult<T> performCall() throws E {
        interceptor.startQueryCount();
        T result = actual.call();
        long callCount = interceptor.getQueryCount();

        return new CallResult<>(result, callCount);
    }
}
