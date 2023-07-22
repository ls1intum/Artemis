import { Component, OnInit } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.service';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismResult } from 'app/exercises/shared/plagiarism/types/PlagiarismResult';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';

@Component({
    selector: 'jhi-suspicious-behavior',
    templateUrl: './suspicious-behavior.component.html',
    styleUrls: ['./suspicious-behavior.component.scss'],
})
export class SuspiciousBehaviorComponent implements OnInit {
    exercises: Exercise[] = [];
    plagiarismCasesPerExercise: Map<Exercise, number> = new Map<Exercise, number>();
    plagiarismResultsPerExercise: Map<Exercise, number> = new Map<Exercise, number>();
    anyPlagiarismCases = false;
    suspiciousSessions: SuspiciousExamSessions[] = [];
    examId: number;
    courseId: number;

    constructor(
        private suspiciousSessionsService: SuspiciousSessionsService,
        private activatedRoute: ActivatedRoute,
        private plagiarismCasesService: PlagiarismCasesService,
        private examService: ExamManagementService,
        private plagiarismResultsService: PlagiarismResultsService,
    ) {}

    ngOnInit(): void {
        this.examId = Number(this.activatedRoute.snapshot.paramMap.get('examId'));
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.suspiciousSessionsService.getSuspiciousSessions(this.courseId, this.examId).subscribe((res) => {
            this.suspiciousSessions = res;
        });
        this.examService.getExercisesWithPotentialPlagiarismForExam(this.courseId, this.examId).subscribe((res) => {
            this.exercises = res;
            this.retrievePlagiarismCases();
        });
    }

    retrievePlagiarismCases = () => {
        this.exercises.forEach((exercise) => {
            this.plagiarismCasesService.getNumberOfPlagiarismCasesForExercise(exercise).subscribe((res) => {
                this.plagiarismCasesPerExercise.computeIfAbsent(exercise, () => res);
                if (res > 0) this.anyPlagiarismCases = true;
            });
            this.plagiarismResultsService.getNumberOfPlagiarismResultsForExercise(exercise.id!).subscribe((res) => {
                this.plagiarismResultsPerExercise.computeIfAbsent(exercise, () => res);
            });
        });
    };
}
