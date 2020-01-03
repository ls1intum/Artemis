package de.tum.in.www1.artemis.util;

public class MockitoVerfication {

    private Verifiable verifiable;

    private MockitoVerfication(Verifiable verifiable) {
        this.verifiable = verifiable;
    }

    public static MockitoVerfication from(Verifiable verifiable) {
        return new MockitoVerfication(verifiable);

    }

    public void verify() throws Exception {
        verifiable.performVerification();
    }
}
