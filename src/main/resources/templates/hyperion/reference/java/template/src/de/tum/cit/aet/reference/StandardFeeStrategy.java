package de.tum.cit.aet.reference;

public class StandardFeeStrategy {

    // TODO: Create a FeeStrategy interface with a calculateFee(double) method and make this class implement it.
    // TODO: Create a ShippingCalculator with selectStrategy(double)/getStrategy() that picks ExpressFeeStrategy for
    // packages over 10 kilograms and StandardFeeStrategy otherwise, then implement its computeFee(double).

    private static final double RATE_PER_KG = 2.0;

    /**
     * Calculates the standard shipping fee.
     *
     * @param weightKg the package weight in kilograms
     * @return the fee in euros
     */
    public double calculateFee(double weightKg) {
        // TODO: Charge the standard rate per kilogram.
        throw new UnsupportedOperationException("Not implemented");
    }
}
