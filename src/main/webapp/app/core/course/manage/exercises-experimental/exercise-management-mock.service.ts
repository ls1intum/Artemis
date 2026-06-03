import { Injectable } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, ExerciseRelation } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { INTRO_JAVA_ALL_EXERCISES, INTRO_JAVA_EXERCISE_GROUPS, INTRO_JAVA_EXERCISE_RELATIONS } from 'app/core/course/manage/exercises/mock/intro-to-programming-java-exercises';

/**
 * Mock-only data source for the experimental v2 exercise-management view. Groups and relations have no
 * server endpoint yet, and group→exercise object references must stay intact, so v2 reads the mock module
 * directly rather than going through HTTP. The HTTP mock interceptor (used by v1) is untouched.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseManagementMockService {
    getExercises(): Exercise[] {
        return INTRO_JAVA_ALL_EXERCISES;
    }

    getGroups(): CourseExerciseGroup[] {
        return INTRO_JAVA_EXERCISE_GROUPS;
    }

    getRelations(): ExerciseRelation[] {
        return INTRO_JAVA_EXERCISE_RELATIONS;
    }
}
