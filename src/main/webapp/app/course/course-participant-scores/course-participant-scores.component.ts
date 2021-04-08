import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { finalize } from 'rxjs/operators';
import { forkJoin } from 'rxjs';

@Component({
    selector: 'jhi-course-participant-scores',
    templateUrl: './course-participant-scores.component.html',
})
export class CourseParticipantScoresComponent implements OnInit {
    courseId: number;
    isLoading: boolean;
    participantScores: ParticipantScoreDTO[] = [];
    participantScoresAverage: ParticipantScoreAverageDTO[] = [];
    avgScore = 0;
    avgRatedScore = 0;

    constructor(private participantScoreService: ParticipantScoresService, private activatedRoute: ActivatedRoute, private alertService: JhiAlertService) {}

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

        forkJoin([scoresObservable, scoresAverageObservable, avgScoreObservable, avgRatedScoreObservable])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                ([scoresResult, scoresAverageResult, avgScoreResult, avgRatedScoreResult]) => {
                    this.participantScoresAverage = scoresAverageResult.body!;
                    this.participantScores = scoresResult.body!;
                    this.avgScore = avgScoreResult.body!;
                    this.avgRatedScore = avgRatedScoreResult.body!;
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
}
