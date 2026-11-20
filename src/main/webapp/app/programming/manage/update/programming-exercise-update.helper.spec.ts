import { describe, expect, it } from 'vitest';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN } from 'app/foundation/constants/input.constants';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { deriveDefaultPackageName } from 'app/programming/manage/update/programming-exercise-update.helper';

function exercise(language: ProgrammingLanguage, shortName: string, title = 'Bubble Sort', courseShortName = 'ITP2026'): ProgrammingExercise {
    const course = new Course();
    course.shortName = courseShortName;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.programmingLanguage = language;
    programmingExercise.projectType = ProjectType.PLAIN_GRADLE;
    programmingExercise.shortName = shortName;
    programmingExercise.title = title;
    return programmingExercise;
}

describe('deriveDefaultPackageName', () => {
    it('should combine the course and exercise short names for Java', () => {
        expect(deriveDefaultPackageName(exercise(ProgrammingLanguage.JAVA, 'BubbleSort'))).toBe('de.tum.cit.aet.itp2026.bubblesort');
    });

    it('should combine the course and exercise short names for Kotlin', () => {
        expect(deriveDefaultPackageName(exercise(ProgrammingLanguage.KOTLIN, 'sorting'))).toBe('de.tum.cit.aet.itp2026.sorting');
    });

    it('should take the course of an exam exercise from its exam', () => {
        const course = new Course();
        course.shortName = 'eist';
        const exam = new Exam();
        exam.course = course;
        const group = new ExerciseGroup();
        group.exam = exam;
        const programmingExercise = new ProgrammingExercise(undefined, group);
        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        programmingExercise.shortName = 'exam1';
        expect(deriveDefaultPackageName(programmingExercise)).toBe('de.tum.cit.aet.eist.exam1');
    });

    it('should fall back to the title when a short name is a Java keyword', () => {
        expect(deriveDefaultPackageName(exercise(ProgrammingLanguage.JAVA, 'for', 'For Loops'))).toBe('de.tum.cit.aet.forloops');
    });

    it('should derive a name in the rules of other languages', () => {
        expect(deriveDefaultPackageName(exercise(ProgrammingLanguage.SWIFT, 'sorting'))).toBe('BubbleSort');
        expect(deriveDefaultPackageName(exercise(ProgrammingLanguage.GO, 'sorting'))).toBe('bubblesort');
    });

    it('should not derive a name before the short name exists', () => {
        expect(deriveDefaultPackageName(exercise(ProgrammingLanguage.JAVA, ''))).toBeUndefined();
    });

    it('should never derive a name that the package rule rejects', () => {
        const derived = deriveDefaultPackageName(exercise(ProgrammingLanguage.JAVA, 'java'));
        expect(derived).toBeDefined();
        expect(new RegExp(PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN).test(derived!)).toBe(true);
    });
});
