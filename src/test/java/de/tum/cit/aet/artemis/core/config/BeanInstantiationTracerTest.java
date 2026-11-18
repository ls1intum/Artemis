package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BeanInstantiationTracerTest {

    private static final Class<?> ARTEMIS_CLASS = BeanInstantiationTracerTest.class;

    @SuppressWarnings("unchecked")
    private static Deque<String> stack(BeanInstantiationTracer tracer) {
        return ((ThreadLocal<Deque<String>>) ReflectionTestUtils.getField(tracer, "callStack")).get();
    }

    @Test
    void popsBeanAndRemovesThreadLocalWhenStackBecomesEmpty() {
        var tracer = new BeanInstantiationTracer();
        tracer.postProcessBeforeInstantiation(ARTEMIS_CLASS, "outer");
        tracer.postProcessBeforeInstantiation(ARTEMIS_CLASS, "inner");
        assertThat(stack(tracer)).containsExactly("inner", "outer");

        tracer.postProcessAfterInitialization(this, "inner");
        assertThat(stack(tracer)).containsExactly("outer");

        tracer.postProcessAfterInitialization(this, "outer");
        // the thread local was removed, so a fresh empty deque is created on next access
        Deque<String> fresh = stack(tracer);
        assertThat(fresh).isEmpty();
        assertThat(fresh).isInstanceOf(ArrayDeque.class);
    }

    @Test
    void doesNotPopWhenNameDiffersAndIgnoresForeignBeans() {
        var tracer = new BeanInstantiationTracer();
        tracer.postProcessBeforeInstantiation(ARTEMIS_CLASS, "bean");

        tracer.postProcessAfterInitialization(this, "other");
        assertThat(stack(tracer)).containsExactly("bean");

        Object foreign = "not an artemis bean";
        assertThat(tracer.postProcessAfterInitialization(foreign, "bean")).isSameAs(foreign);
        assertThat(stack(tracer)).containsExactly("bean");
    }

    @Test
    void emptyStackIsClearedWithoutPopping() {
        var tracer = new BeanInstantiationTracer();

        assertThat(tracer.postProcessAfterInitialization(this, "never-started")).isSameAs(this);
        assertThat(stack(tracer)).isEmpty();
    }
}
