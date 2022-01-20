import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { forkJoin } from 'rxjs';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-exam-participant-scores',
    templateUrl: './exam-participant-scores.component.html',
})
export class ExamParticipantScoresComponent implements OnInit {
    readonly GradeType = GradeType;

    courseId: number;
    course?: Course;
    examId: number;
    isLoading: boolean;
    participantScores: ParticipantScoreDTO[] = [];
    participantScoresAverage: ParticipantScoreAverageDTO[] = [];
    avgScore = 0;
    avgRatedScore = 0;

    gradingScale?: GradingScale;
    avgGrade?: string;
    avgRatedGrade?: string;

    constructor(
        private participantScoreService: ParticipantScoresService,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private gradingSystemService: GradingSystemService,
        private courseManagementService: CourseManagementService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            this.courseManagementService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
            this.examId = +params['examId'];
            if (this.courseId && this.examId) {
                this.loadData();
            }
        });
    }

    loadData() {
        this.isLoading = true;

        const scoresObservable = this.participantScoreService.findAllOfExam(this.examId);
        const scoresAverageObservable = this.participantScoreService.findAverageOfExamPerParticipant(this.examId);
        const avgScoreObservable = this.participantScoreService.findAverageOfExam(this.examId, false);
        const avgRatedScoreObservable = this.participantScoreService.findAverageOfExam(this.examId, true);
        const gradingScaleObservable = this.gradingSystemService.findGradingScaleForExam(this.courseId, this.examId);

        forkJoin([scoresObservable, scoresAverageObservable, avgScoreObservable, avgRatedScoreObservable, gradingScaleObservable])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([scoresResult, scoresAverageResult, avgScoreResult, avgRatedScoreResult, gradingScaleResult]) => {
                    this.participantScoresAverage = scoresAverageResult.body!;
                    this.participantScores = scoresResult.body!;
                    this.avgScore = avgScoreResult.body!;
                    this.avgRatedScore = avgRatedScoreResult.body!;
                    if (gradingScaleResult.body) {
                        this.gradingScale = gradingScaleResult.body;
                        this.avgGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale?.gradeSteps, this.avgScore)?.gradeName;
                        this.avgRatedGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale?.gradeSteps, this.avgRatedScore)?.gradeName;
                        for (const dto of this.participantScoresAverage) {
                            dto.averageGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale?.gradeSteps, dto.averageScore!)?.gradeName;
                            dto.averageRatedGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale?.gradeSteps, dto.averageRatedScore!)?.gradeName;
                        }
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }
}
