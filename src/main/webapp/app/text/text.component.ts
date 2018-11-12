import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise, TextExerciseService } from 'app/entities/text-exercise';
import { Result } from 'app/entities/result';
import { Participation, ParticipationService } from 'app/entities/participation';

@Component({
    templateUrl: './text.component.html',
    providers: [ParticipationService]
})
export class TextComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    private id: number;
    private submission: TextSubmission;
    private textExercise: TextExercise;
    participation: Participation;
    result: Result;

    isActive: boolean;
    isSaving: boolean;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private textSubmissionService: TextSubmissionService,
        private jhiAlertService: JhiAlertService
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.textExerciseService.find(params['participationId']).subscribe(
                    data => {
                        this.textExercise = data.body;
                        this.participation = this.textExercise.participations[0];
                        this.submission = new TextSubmission();

                        this.isActive = this.textExercise.dueDate == null || new Date() <= this.textExercise.dueDate.toDate();

                        console.log(data);
                    },
                    (error: HttpErrorResponse) => this.onError(error)
                );
            }
        });
    }

    ngOnDestroy() {}

    saveText() {
        if (this.isSaving) {
            return;
        }

        this.submission.submitted = false;
        this.isSaving = true;

        let submission: HttpResponse<TextSubmission>;

        this.textSubmissionService[this.submission.id ? 'update' : 'create'](this.submission, this.textExercise.id).subscribe(
            response => (submission = response),
            e => this.jhiAlertService.error('arTeMiSApp.textExercise.error')
        );

        if (submission) {
            this.submission = submission.body;
            this.result = this.submission.result;
            this.jhiAlertService.success('arTeMiSApp.textExercise.saveSuccessful');
        }

        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
