package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HibernateQueryInterceptorTest {

    private final HibernateQueryInterceptor interceptor = new HibernateQueryInterceptor();

    @Test
    void shouldRecordStatementsAndPreserveCountOnlyBehaviorWhenCapturingQueries() {
        interceptor.startQueryCapture();

        assertThat(interceptor.inspect("select 1")).isEqualTo("select 1");
        assertThat(interceptor.inspect("update quiz_question set title = 'x'")).isEqualTo("update quiz_question set title = 'x'");

        HibernateQueryInterceptor.CapturedQueries capturedQueries = interceptor.stopQueryCapture();
        assertThat(capturedQueries.count()).isEqualTo(2);
        assertThat(capturedQueries.queries()).containsExactly("select 1", "update quiz_question set title = 'x'");
        assertThatThrownBy(() -> capturedQueries.queries().add("delete from quiz_question")).isInstanceOf(UnsupportedOperationException.class);

        interceptor.startQueryCount();
        interceptor.inspect("select 2");
        assertThat(interceptor.getQueryCount()).isEqualTo(1);
        assertThat(interceptor.stopQueryCapture().queries()).isEmpty();
    }

    @Test
    void shouldClearThreadLocalStateWhenStoppingCapture() {
        assertThat(interceptor.stopQueryCapture().count()).isZero();
        assertThat(interceptor.stopQueryCapture().queries()).isEmpty();

        interceptor.startQueryCapture();
        interceptor.inspect("select 1");
        interceptor.stopQueryCapture();

        assertThat(interceptor.getQueryCount()).isNull();
        assertThat(interceptor.stopQueryCapture().count()).isZero();
    }
}
