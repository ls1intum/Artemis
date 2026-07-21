# Shipping Fee Calculator

A parcel service charges different rates depending on how a package is shipped. Implement the fee strategies, then wire them together so the calculator picks the right one
automatically.

### Part 1: Fee Strategies

Two shipping strategies charge different rates.

**You have the following tasks:**

1. [task][Implement Standard Fee Strategy](testStandardFeeTypical,testStandardFeeZeroWeight)
Implement `calculateFee(double)` in `StandardFeeStrategy`. Charge 2.0 EUR per kilogram.

2. [task][Implement Express Fee Strategy](testExpressFeeTypical,testExpressFeeMinimumSurcharge)
Implement `calculateFee(double)` in `ExpressFeeStrategy`. Charge 3.5 EUR per kilogram plus a flat 5.0 EUR surcharge, even for a weightless package.

### Part 2: Strategy Selection

Introduce a `FeeStrategy` interface implemented by both strategies above, and a `ShippingCalculator` that chooses between them.

**You have the following tasks:**

3. [task][Select Strategy By Weight](testSelectsExpressForHeavyPackages,testSelectsStandardForLightPackages)
Create the `FeeStrategy` interface with a `double calculateFee(double weightKg)` method, and make both strategies implement it. Create `ShippingCalculator` with a
`selectStrategy(double weightKg)` method that picks `ExpressFeeStrategy` for packages over 10 kilograms and `StandardFeeStrategy` otherwise, plus a `getStrategy()` accessor.

4. [task][Compute Total Fee](testComputeFeeDelegatesToChosenStrategy)
Implement `computeFee(double weightKg)` in `ShippingCalculator` so it selects the appropriate strategy and delegates the fee calculation to it.

@startuml

interface FeeStrategy {
  +calculateFee(double): double
}

class StandardFeeStrategy {
  <color:testsColor(testStandardFeeTypical)>+calculateFee(double): double</color>
}

class ExpressFeeStrategy {
  <color:testsColor(testExpressFeeTypical)>+calculateFee(double): double</color>
}

class ShippingCalculator {
  -strategy: FeeStrategy
  <color:testsColor(testSelectsExpressForHeavyPackages)>+selectStrategy(double): void</color>
  <color:testsColor(testComputeFeeDelegatesToChosenStrategy)>+computeFee(double): double</color>
}

StandardFeeStrategy .up.|> FeeStrategy #testsColor(testSelectsStandardForLightPackages)
ExpressFeeStrategy .up.|> FeeStrategy #testsColor(testSelectsExpressForHeavyPackages)
ShippingCalculator -right-> FeeStrategy: strategy

hide empty fields
hide empty methods

@enduml
