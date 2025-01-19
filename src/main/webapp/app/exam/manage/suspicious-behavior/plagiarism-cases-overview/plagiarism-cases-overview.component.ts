import { Component, inject, input } from '@angular/core';
import { Exercise, getExerciseUrlSegment } from 'app/entities/exercise.model';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-plagiarism-cases-overview',
    templateUrl: './plagiarism-cases-overview.component.html',
    imports: [TranslateDirective],
})
export class PlagiarismCasesOverviewComponent {
    private router = inject(Router);

    exercises = input.required<Exercise[]>();
    plagiarismCasesPerExercise = input.required<Map<Exercise, number>>();
    plagiarismResultsPerExercise = input.required<Map<Exercise, number>>();
    anyPlagiarismCases = input(false);
    courseId = input.required<number>();
    examId = input.required<number>();

    goToPlagiarismDetection(exercise: Exercise) {
        const exerciseGroupId = exercise.exerciseGroup?.id;
        const exerciseType = exercise.type;
        this.router.navigate([
            '/course-management',
            this.courseId(),
            'exams',
            this.examId(),
            'exercise-groups',
            exerciseGroupId,
            getExerciseUrlSegment(exerciseType),
            exercise.id,
            'plagiarism',
        ]);
    }
    goToPlagiarismCases() {
        this.router.navigate(['/course-management', this.courseId(), 'exams', this.examId(), 'plagiarism-cases']);
    }
}
