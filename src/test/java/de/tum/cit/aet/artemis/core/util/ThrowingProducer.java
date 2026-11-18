package de.tum.cit.aet.artemis.core.util;

@FunctionalInterface
public interface ThrowingProducer<T, E extends Exception> {

    T call() throws E;
}
