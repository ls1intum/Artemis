import { Injectable } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, ExerciseRelation } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';

interface NewGroupSettings {
    title: string;
    maxPoints?: number;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
}
import { INTRO_JAVA_ALL_EXERCISES, INTRO_JAVA_EXERCISE_GROUPS, INTRO_JAVA_EXERCISE_RELATIONS } from 'app/core/course/manage/exercises/mock/intro-to-programming-java-exercises';

/**
 * Mock-only data source for the experimental v2 exercise-management view. Groups and relations have no
 * server endpoint yet, and group→exercise object references must stay intact, so v2 reads the mock module
 * directly rather than going through HTTP. The HTTP mock interceptor (used by v1) is untouched.
 *
 * The service owns mutable copies of the mock data so that AI-generated variants survive navigation
 * back to the management page within the same browser session.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseManagementMockService {
    private readonly _exercises: Exercise[] = [...INTRO_JAVA_ALL_EXERCISES];
    private readonly _groups: CourseExerciseGroup[] = INTRO_JAVA_EXERCISE_GROUPS.map((g) => ({
        ...g,
        exercises: g.exercises ? [...g.exercises] : [],
    }));
    private readonly _relations: ExerciseRelation[] = [...INTRO_JAVA_EXERCISE_RELATIONS];

    getExercises(): Exercise[] {
        return this._exercises;
    }

    getGroups(): CourseExerciseGroup[] {
        return this._groups;
    }

    getRelations(): ExerciseRelation[] {
        return this._relations;
    }

    findGroupForExercise(exerciseId: number): CourseExerciseGroup | undefined {
        return this._groups.find((g) => g.exercises?.some((e) => e.id === exerciseId));
    }

    addVariantToGroup(variant: Exercise, groupId: number): void {
        this._exercises.push(variant);
        const group = this._groups.find((g) => g.id === groupId);
        group?.exercises?.push(variant);
    }

    addVariantWithNewGroup(variant: Exercise, sourceExercise: Exercise, settings: NewGroupSettings): CourseExerciseGroup {
        this._exercises.push(variant);
        const maxId = this._groups.reduce((max, g) => Math.max(max, g.id ?? 0), 0);
        const newGroup: CourseExerciseGroup = {
            id: maxId + 1,
            title: settings.title,
            order: this._groups.length,
            releaseDate: settings.releaseDate,
            startDate: settings.startDate,
            dueDate: settings.dueDate,
            assessmentDueDate: settings.assessmentDueDate,
            maxPoints: settings.maxPoints,
            exercises: [sourceExercise, variant],
        };
        this._groups.push(newGroup);
        return newGroup;
    }

    addVariantStandalone(variant: Exercise): void {
        this._exercises.push(variant);
    }
}
