Verify the correctness of the UML Model given the actual object model of Artemis. 
CompetencyVariantGroupLink and ExerciseVariantGroup are new entities and expected.
Add the exam, course, and exam group entities to this mockup. Only add required attributes for clearness.

We should make it two UML Models:
1. While ExamGroup sits between exams and exercises, ExerciseVariantGroup should only have a relation to Exercises (e.g. aggregation or composition) but not to course. Exercises keep their relation to course and vice versa
2. In a future iteration, we might also do that for ExamGroups (no more connection to exams, only to exercises and exercises have a direct connection to exams without going through examgroups)

Please make sure the Exercise Variant Object Model markdown file only explains what's new. We do not need to explain every class and attribute.
