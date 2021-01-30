import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-exam-participant-scores',
    templateUrl: './exam-participant-scores.component.html',
    styles: [],
})
export class ExamParticipantScoresComponent implements OnInit {
    examId: number;
    isLoading: boolean;
    participantScores: ParticipantScoreDTO[] = [];

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
        this.participantScoreService
            .findAllOfExam(this.examId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (participantScoresResponse) => {
                    this.participantScores = participantScoresResponse.body!;
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
}
