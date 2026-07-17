package de.tum.cit.aet.reference;

public class ExpressFeeStrategy implements FeeStrategy {

    private static final double RATE_PER_KG = 3.5;

    private static final double SURCHARGE = 5.0;

    @Override
    public double calculateFee(double weightKg) {
        return weightKg * RATE_PER_KG + SURCHARGE;
    }
}
