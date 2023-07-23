import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Router } from '@angular/router';
import { PlagiarismResult } from 'app/exercises/shared/plagiarism/types/PlagiarismResult';

@Component({
    selector: 'jhi-plagiarism-cases-overview',
    templateUrl: './plagiarism-cases-overview.component.html',
})
export class PlagiarismCasesOverviewComponent {
    @Input() exercises: Exercise[];
    @Input() plagiarismCasesPerExercise: Map<Exercise, number>;
    @Input() plagiarismResultsPerExercise: Map<Exercise, number> = new Map<Exercise, number>();
    @Input() anyPlagiarismCases = false;
    @Input() courseId: number;
    @Input() examId: number;
    constructor(private router: Router) {}

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
            `${exerciseType}-exercises`,
            exercise.id,
            'plagiarism',
        ]);
    }
    goToPlagiarismCases() {
        this.router.navigate(['/course-management', this.courseId, 'exams', this.examId, 'plagiarism-cases']);
    }
}
