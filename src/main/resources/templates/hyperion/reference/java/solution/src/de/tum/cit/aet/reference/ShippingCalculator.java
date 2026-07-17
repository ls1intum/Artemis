package de.tum.cit.aet.reference;

public class ShippingCalculator {

    /**
     * @oracleIgnore
     */
    private static final double EXPRESS_THRESHOLD_KG = 10.0;

    private FeeStrategy strategy;

    /**
     * Chooses the express strategy for heavy packages and the standard strategy otherwise.
     *
     * @param weightKg the package weight in kilograms
     */
    public void selectStrategy(double weightKg) {
        this.strategy = weightKg > EXPRESS_THRESHOLD_KG ? new ExpressFeeStrategy() : new StandardFeeStrategy();
    }

    public FeeStrategy getStrategy() {
        return strategy;
    }

    /**
     * Selects the appropriate strategy for the given weight and computes its fee.
     *
     * @param weightKg the package weight in kilograms
     * @return the fee in euros
     */
    public double computeFee(double weightKg) {
        selectStrategy(weightKg);
        return strategy.calculateFee(weightKg);
    }
}
