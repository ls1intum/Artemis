import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { finalize } from 'rxjs/operators';
import { forkJoin } from 'rxjs';

@Component({
    selector: 'jhi-exam-participant-scores',
    templateUrl: './exam-participant-scores.component.html',
    styles: [],
})
export class ExamParticipantScoresComponent implements OnInit {
    examId: number;
    isLoading: boolean;
    participantScores: ParticipantScoreDTO[] = [];
    participantScoresAverage: ParticipantScoreAverageDTO[] = [];
    avgScore = 0;
    avgRatedScore = 0;
    mode: 'individual' | 'average' = 'individual';

    constructor(private participantScoreService: ParticipantScoresService, private activatedRoute: ActivatedRoute, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.examId = +params['examId'];
            if (this.examId) {
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
