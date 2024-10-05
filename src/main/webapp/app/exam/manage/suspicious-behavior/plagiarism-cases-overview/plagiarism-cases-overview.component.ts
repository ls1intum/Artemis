import { Component, Input, inject } from '@angular/core';
import { Exercise, getExerciseUrlSegment } from 'app/entities/exercise.model';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-plagiarism-cases-overview',
    templateUrl: './plagiarism-cases-overview.component.html',
})
export class PlagiarismCasesOverviewComponent {
    private router = inject(Router);

    @Input() exercises: Exercise[];
    @Input() plagiarismCasesPerExercise: Map<Exercise, number>;
    @Input() plagiarismResultsPerExercise: Map<Exercise, number> = new Map<Exercise, number>();
    @Input() anyPlagiarismCases = false;
    @Input() courseId: number;
    @Input() examId: number;

    goToPlagiarismDetection(exercise: Exercise) {
        const exerciseGroupId = exercise.exerciseGroup?.id;
        const exerciseType = exercise.type;
        this.router.navigate([
            '/course-management',
            this.courseId,
            'exams',
            this.examId,
            'exercise-groups',
            exerciseGroupId,
            getExerciseUrlSegment(exerciseType),
            exercise.id,
            'plagiarism',
        ]);
    }
    goToPlagiarismCases() {
        this.router.navigate(['/course-management', this.courseId, 'exams', this.examId, 'plagiarism-cases']);
    }
}
