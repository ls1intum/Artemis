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

	// If an exercise group is set we are in exam mod
	if (exercise.exerciseGroup) {
		router.navigate([
			'course-management',
			this.exercise.exerciseGroup!.exam!.course!.id!.toString(),
			'exams',
			this.exercise.exerciseGroup!.exam!.id!.toString(),
			'exercise-groups',
			this.exercise.exerciseGroup!.id!.toString()
		]);
		return;
	}

	if (exercise.id) {
		router.navigate(['course-management', this.exercise.course!.id!.toString(), this.exercise.type! + '-exercises', exercise.id!.toString()]);
	} else {
		router.navigate(['course-management', this.exercise.course!.id!.toString(), this.exercise.type! + '-exercises']);
	}
};
