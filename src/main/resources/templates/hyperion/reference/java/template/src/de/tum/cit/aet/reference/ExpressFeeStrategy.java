package de.tum.cit.aet.reference;

public class ExpressFeeStrategy {

    // TODO: Make this class implement the FeeStrategy interface as well.

    private static final double RATE_PER_KG = 3.5;

    private static final double SURCHARGE = 5.0;

    /**
     * Calculates the express shipping fee.
     *
     * @param weightKg the package weight in kilograms
     * @return the fee in euros
     */
    public double calculateFee(double weightKg) {
        // TODO: Charge the express rate per kilogram plus a flat surcharge.
        throw new UnsupportedOperationException("Not implemented");
    }
}
