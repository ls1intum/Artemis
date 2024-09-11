package de.tum.cit.aet.artemis.service.compass.umlmodel;

/**
 * Similarity is implemented by classes of which an object can calculate the similarity between itself and another similarity object. This interface should be implemented using the
 * type of the implementing class as T to ensure that the object passed to the similarity methods is always of the same type as the implementing class. In this way, the similarity
 * is calculated between two objects of the same type which is the intended behavior in most cases.
 * <p>
 * This interface provides two methods for calculating the similarity between two Similarity<T> objects. If the implementing class contains child entities (e.g. a UML class
 * containing attributes and methods) that should be separately handled in the similarity calculation, overallSimilarity(Similarity<T> reference) can be overridden to provide the
 * specific overall similarity calculation including the child entities. Otherwise, it returns the value of the normal similarity(Similarity<T> reference) method.
 */
public interface Similarity<T extends Similarity<?>> {

    /**
     * Calculates the similarity between this and another reference object of type Similarity<T>. Takes all attributes of the type into account.
     *
     * @param reference the reference object that should be compared to this object
     * @return the similarity score as a number between 0 and 1
     */
    double similarity(Similarity<T> reference);

    /**
     * Calculates the similarity between this and another reference object of type Similarity<T> including child entities. If no special behavior is required concerning child
     * entities, no implementation of this method is necessary. It returns the result of the normal similarity calculation by default.
     *
     * @param reference the reference object that should be compared to this object
     * @return the similarity score as a number between 0 and 1
     */
    default double overallSimilarity(Similarity<T> reference) {
        return similarity(reference);
    }
}
