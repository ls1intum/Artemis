package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.validation.InetSocketAddressValidator;

public class InetSocketAddressValidatorTest {

    @Test
    public void shouldAllowCorrectAddress() {
        assertThat(InetSocketAddressValidator.getValidAddress("localhost:8080")).isPresent();
        assertThat(InetSocketAddressValidator.getValidAddress("127.0.0.1:8080")).isPresent();
        assertThat(InetSocketAddressValidator.getValidAddress("[::1]:8080")).isPresent();
        assertThat(InetSocketAddressValidator.getValidAddress("artemis.ase.in.tum.de:8080")).isPresent();
    }

    @Test
    public void shouldDenyIncorrectAddress() {
        assertThat(InetSocketAddressValidator.getValidAddress("localhost:8080A")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("A127.0.0.1:8080")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("A[::1]:8080")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("artemis.ase.in.tum.de:8080A")).isEmpty();

        assertThat(InetSocketAddressValidator.getValidAddress("localhost")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("127.0.0.1")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("[::1]")).isEmpty();
        assertThat(InetSocketAddressValidator.getValidAddress("artemis.ase.in.tum.de")).isEmpty();
    }
}
