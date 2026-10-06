package de.tum.cit.aet.artemis.core.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class PrefixedKeyGeneratorTest {

    private static Method method(String name) throws NoSuchMethodException {
        return PrefixedKeyGeneratorTest.class.getDeclaredMethod(name);
    }

    @SuppressWarnings("unused")
    void cachedA() {
    }

    @SuppressWarnings("unused")
    void cachedB() {
    }

    @Test
    void keysWithSamePrefixMethodAndParamsAreEqual() throws Exception {
        var generator = new PrefixedKeyGenerator(null, null);

        Object first = generator.generate(this, method("cachedA"), "a", new int[] { 1, 2 });
        Object second = generator.generate(this, method("cachedA"), "a", new int[] { 1, 2 });

        assertThat(first).isEqualTo(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void keysDifferByMethodParamsPrefixAndType() throws Exception {
        var generator = new PrefixedKeyGenerator(null, null);
        var otherGenerator = new PrefixedKeyGenerator(null, null);

        Object key = generator.generate(this, method("cachedA"), "a");

        assertThat(key).isNotEqualTo(generator.generate(this, method("cachedB"), "a")).isNotEqualTo(generator.generate(this, method("cachedA"), "b"))
                .isNotEqualTo(otherGenerator.generate(this, method("cachedA"), "a")).isNotEqualTo("a");
        assertThat(key.hashCode()).isNotEqualTo(generator.generate(this, method("cachedA"), "b").hashCode());
    }

    @Test
    void toStringContainsMethodNameAndParams() throws Exception {
        Object key = new PrefixedKeyGenerator(null, null).generate(this, method("cachedA"), "x", 5);

        assertThat(key.toString()).contains("PrefixedSimpleKey").contains("cachedA(").contains("[x, 5]");
    }
}
