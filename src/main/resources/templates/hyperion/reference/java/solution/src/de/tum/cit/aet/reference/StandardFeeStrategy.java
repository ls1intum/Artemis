package de.tum.cit.aet.reference;

public class StandardFeeStrategy implements FeeStrategy {

    private static final double RATE_PER_KG = 2.0;

    /**
     * Calculates the standard shipping fee.
     *
     * @param weightKg the package weight in kilograms
     * @return the fee in euros
     */
    @Override
    public double calculateFee(double weightKg) {
        return weightKg * RATE_PER_KG;
    }
}
