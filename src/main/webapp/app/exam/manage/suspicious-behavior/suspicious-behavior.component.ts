import { Component, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { ActivatedRoute, Router } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';

@Component({
    selector: 'jhi-suspicious-behavior',
    templateUrl: './suspicious-behavior.component.html',
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
        private router: Router,
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

    private retrievePlagiarismCases = () => {
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

    goToSuspiciousSessions() {
        this.router.navigate(['/course-management', this.courseId, 'exams', this.examId, 'suspicious-behavior', 'suspicious-sessions'], {
            state: { suspiciousSessions: this.suspiciousSessions },
        });
    }
}
