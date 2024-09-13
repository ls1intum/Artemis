package de.tum.cit.aet.artemis.util;

@FunctionalInterface
public interface ThrowingProducer<T, E extends Exception> {

    T call() throws E;
}
