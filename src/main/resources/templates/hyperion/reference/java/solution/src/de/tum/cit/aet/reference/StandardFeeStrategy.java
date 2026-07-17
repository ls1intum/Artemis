package de.tum.cit.aet.reference;

public class StandardFeeStrategy implements FeeStrategy {

    private static final double RATE_PER_KG = 2.0;

    @Override
    public double calculateFee(double weightKg) {
        return weightKg * RATE_PER_KG;
    }
}
