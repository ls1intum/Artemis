# Final (graded) problem statement

Role: the student-facing statement shipped with the exercise, written once solution/template/tests exist. It
documents the contract the tests already enforce; it does not authorize new graded behavior.

## Voice and perspective

The statement speaks TO the student. Frame the goal as "we" ("In this exercise, we want to...") and address
the reader as "you" with imperative tasks ("Implement...", "Create..."). Never write about "students" in the
third person, and never describe the exercise's own construction -- its theme choice, its design rationale,
or the instructor brief ("This exercise uses a whimsical theme" is authoring commentary, not instruction).

## Structure: tasks ARE the narrative, not an appendix

- One `#` title, a short motivating intro (context plus learning goal).
- Organize the work as progressive parts (`### Part 1: ...`), each introduced by one or two sentences of
  context and `**You have the following tasks:**`.
- Number the tasks. Every `[task][Title](exactTestNameA,exactTestNameB)` line is immediately followed by one
  or two imperative sentences naming the exact members the student implements ("Implement
  `calculateFee(double)` in `StandardFeeStrategy`. Charge 2.0 EUR per kilogram."). A bare task line with no
  prose under it is a defect.
- A contract detail (bounds, ordering, tie-breaking, exceptions) belongs with the task that enforces it, or
  in a short contract section -- only where a test observes it.
- Optional unassessed work goes in a clearly marked final part ("These are not tested").

## CRITICAL POLICY: the API appears exactly once

Present the public API students implement against exactly ONCE and compactly: a short signature list, a
table, or (for a multi-type design) a PlantUML diagram. Never reproduce template code blocks, stub bodies, or
javadoc that already live in the template -- the template IS the API reference at the point of use.

## The diagram, when the design earns one

A multi-type design (students create types or wire a pattern) gets a PlantUML class diagram placed right
after the tasks that reference it ("following the below class diagram"). The diagram is interactive in
Artemis: every member and relation linked with `testsColor` renders green/red as the student's tests pass or
fail, showing exactly what remains. Link every element that has a check, and only elements that have one:
members as `<color:testsColor(exactTestName)>+member()</color>`, relations as
`Sub -up-|> Super #testsColor(testClass[Sub])`. Use behavioural test names and seeded structural check names
(`testClass[X]`, `testMethods[X]`, `testAttributes[X]`, `testConstructors[X]`) exactly as `verify` reports
them. End with `hide empty fields` / `hide empty methods`.

## Worked examples

Clarify non-obvious behavior only. Must never reuse a graded test's exact composite input -- pick a smaller or
materially different input that teaches the rule without revealing the oracle.

## Exemplar (FORM only -- never copy its topic, API, or design)

```markdown
# Parcel Shipping Fees

In this exercise, we want to calculate shipping fees with interchangeable pricing strategies, chosen at
runtime.

### Part 1: Fee Strategies

Each shipping method charges differently.

**You have the following tasks:**

1. [task][Implement Standard Fees](testStandardFeeTypical,testStandardFeeZeroWeight)
Implement `calculateFee(double)` in `StandardFeeStrategy`. Charge 2.0 EUR per kilogram.

### Part 2: Strategy Selection

Create the strategy interface and the calculator that picks a strategy, following the below class diagram.

**You have the following tasks:**

2. [task][Create The Strategy Interface](testClass[FeeStrategy],testMethods[FeeStrategy])
Create a `FeeStrategy` interface with `double calculateFee(double weightKg)` and make both strategies
implement it.

3. [task][Select And Delegate](testSelectsExpressForHeavyPackages,testComputeFeeDelegatesToChosenStrategy)
Implement `selectStrategy(double)` and `computeFee(double)` in `ShippingCalculator`: pick
`ExpressFeeStrategy` above 10 kg, otherwise `StandardFeeStrategy`, and delegate the fee calculation.

@startuml
interface FeeStrategy {
  <color:testsColor(testMethods[FeeStrategy])>+calculateFee(double): double</color>
}
class StandardFeeStrategy {
  <color:testsColor(testStandardFeeTypical)>+calculateFee(double): double</color>
}
class ShippingCalculator {
  <color:testsColor(testSelectsExpressForHeavyPackages)>+selectStrategy(double): void</color>
  <color:testsColor(testComputeFeeDelegatesToChosenStrategy)>+computeFee(double): double</color>
}
StandardFeeStrategy .up.|> FeeStrategy #testsColor(testClass[StandardFeeStrategy])
ShippingCalculator -right-> FeeStrategy
hide empty fields
hide empty methods
@enduml
```
