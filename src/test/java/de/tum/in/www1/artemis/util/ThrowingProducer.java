package de.tum.in.www1.artemis.util;

@FunctionalInterface
public interface ThrowingProducer<T, E extends Exception> {

    T call() throws E;
}
