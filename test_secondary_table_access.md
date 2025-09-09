# Testing @SecondaryTable Access Through Base Repository

Let me test whether the base ExerciseRepository can actually access @SecondaryTable data:

## Hypothesis to Test
- **My claim**: Base ExerciseRepository cannot access @SecondaryTable data from subtypes
- **Your question**: Can't we use ExerciseRepository directly?

## JPA/Hibernate Behavior with @SecondaryTable

Looking at the inheritance structure:
- `Exercise` uses `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`
- `ProgrammingExercise` adds `@SecondaryTable(name = "programming_exercise_details")`

## The Key Question
When we do:
```java
Exercise exercise = exerciseRepository.findById(programmingExerciseId);
```

Will Hibernate:
1. **Load secondary table data** because it detects the concrete type is ProgrammingExercise?
2. **Skip secondary table data** because the repository is typed to Exercise?

## What I Need to Verify
1. Does `exerciseRepository.findById()` return a ProgrammingExercise instance or Exercise instance?
2. If it returns ProgrammingExercise, are the @SecondaryTable fields populated?
3. Does the @EntityGraph from base repository work with secondary tables?

## My Concern
Even if Hibernate loads the concrete type, the base ExerciseRepository methods like:
```java
@EntityGraph(type = LOAD, attributePaths = { "categories", "competencyLinks.competency" })
Optional<Exercise> findWithBaseFieldsById(Long exerciseId);
```

Might not include secondary table fields in the @EntityGraph paths, requiring explicit field paths like:
```java
@EntityGraph(type = LOAD, attributePaths = { 
    "allowOnlineEditor", 
    "staticCodeAnalysisEnabled" 
})
```

But these fields don't exist on the base Exercise entity, so the @EntityGraph would fail.

## Conclusion
I should test this empirically rather than making assumptions. You may be right that the base repository can access the secondary table data if Hibernate handles the concrete type resolution correctly.