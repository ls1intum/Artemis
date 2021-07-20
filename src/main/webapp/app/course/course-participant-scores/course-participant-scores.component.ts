import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { finalize } from 'rxjs/operators';
import { forkJoin } from 'rxjs';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';

@Component({
    selector: 'jhi-course-participant-scores',
    templateUrl: './course-participant-scores.component.html',
})
export class CourseParticipantScoresComponent implements OnInit {
    readonly GradeType = GradeType;

    courseId: number;
    isLoading: boolean;
    participantScores: ParticipantScoreDTO[] = [];
    participantScoresAverage: ParticipantScoreAverageDTO[] = [];
    avgScore = 0;
    avgRatedScore = 0;

    gradingScale?: GradingScale;
    avgGrade?: String;
    avgRatedGrade?: String;

    constructor(
        private participantScoreService: ParticipantScoresService,
        private activatedRoute: ActivatedRoute,
        private alertService: JhiAlertService,
        private gradingSystemService: GradingSystemService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    loadData() {
        this.isLoading = true;

        const scoresObservable = this.participantScoreService.findAllOfCourse(this.courseId);
        const scoresAverageObservable = this.participantScoreService.findAverageOfCoursePerParticipant(this.courseId);
        const avgScoreObservable = this.participantScoreService.findAverageOfCourse(this.courseId, false);
        const avgRatedScoreObservable = this.participantScoreService.findAverageOfCourse(this.courseId, true);
        const gradingScaleObservable = this.gradingSystemService.findGradingScaleForCourse(this.courseId);

        forkJoin([scoresObservable, scoresAverageObservable, avgScoreObservable, avgRatedScoreObservable, gradingScaleObservable])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                ([scoresResult, scoresAverageResult, avgScoreResult, avgRatedScoreResult, gradingScaleResult]) => {
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
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
}
