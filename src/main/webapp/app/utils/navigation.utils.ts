import { Router } from '@angular/router';
import { Exercise } from 'app/entities/exercise.model';

export const navigateBack = (router: Router, fallbackUrl: string[]): void => {
    if (window.history.length > 1) {
        window.history.back();
    } else {
        router.navigate(fallbackUrl);
    }
};

export const navigateBackFromExerciseUpdate = (router: Router, exercise: Exercise): void => {
    if (window.history.length > 1) {
        window.history.back();
        return;
    }

    // If an exercise group is set we are in exam mode
    if (exercise.exerciseGroup) {
        router.navigate(['course-management', exercise.exerciseGroup!.exam!.course!.id!.toString(), 'exams', exercise.exerciseGroup!.exam!.id!.toString(), 'exercise-groups']);
        return;
    }

    if (exercise.id) {
        router.navigate(['course-management', exercise.course!.id!.toString(), exercise.type! + '-exercises', exercise.id!.toString()]);
    } else {
        router.navigate(['course-management', exercise.course!.id!.toString(), exercise.type! + '-exercises']);
    }
};
