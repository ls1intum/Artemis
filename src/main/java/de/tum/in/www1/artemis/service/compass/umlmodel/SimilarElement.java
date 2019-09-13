package de.tum.in.www1.artemis.service.compass.umlmodel;

/**
 * A SimilarElement is an object that can calculate the similarity between this and another object of type T. Normally, this interface should be implemented using the type of the
 * implementing class as T to ensure that the object passed to the similarity() method is always of the same type as the implementing class. In this way, the similarity is
 * calculated between two objects of the same type which is the intended behavior in most cases.
 */
public interface SimilarElement<T> {

    /**
     * Calculates the similarity between this and another reference object of type T.
     *
     * @param reference the reference object that should be compared to this object
     * @return the similarity score as a number between 0 and 1
     */
    double similarity(T reference);
}
