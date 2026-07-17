package de.tum.cit.aet.reference;

public interface FeeStrategy {

    /**
     * Calculates the shipping fee for a package of the given weight.
     *
     * @param weightKg the package weight in kilograms
     * @return the fee in euros
     */
    double calculateFee(double weightKg);
}
