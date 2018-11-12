import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise, TextExerciseService } from 'app/entities/text-exercise';
import { Result } from 'app/entities/result';
import { Participation, ParticipationService } from 'app/entities/participation';
import { TextService } from 'app/text/text.service';
import * as moment from 'moment';

@Component({
    templateUrl: './text.component.html',
    providers: [ParticipationService]
})
export class TextComponent implements OnInit, OnDestroy {
    private subscription: Subscription;

    textExercise: TextExercise;
    participation: Participation;
    result: Result;
    submission: TextSubmission;
    isActive: boolean;
    isSaving: boolean;
    answer: string;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private textSubmissionService: TextSubmissionService,
        private textService: TextService,
        private jhiAlertService: JhiAlertService
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.textService.get(params['participationId']).subscribe(
                    data => {
                        console.log(data);

                        this.participation = data.participation;
                        this.textExercise = this.participation.exercise as TextExercise;

                        this.submission = data.textSubmission;

                        if (this.submission && this.submission.result) {
                            this.result = this.submission.result;
                        }

                        if (this.submission && this.submission.text) {
                            this.answer = this.submission.text;
                        }

                        this.isActive = this.textExercise.dueDate == null || new Date() <= moment(this.textExercise.dueDate).toDate();
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

        if (!this.submission) {
            this.submission = new TextSubmission();
        }

        this.submission.submitted = false;
        this.submission.text = this.answer;
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

    submit() {
        if (!this.submission) {
            return;
        }

        this.submission.text = this.answer;

        const confirmSubmit = window.confirm('arTeMiSApp.textExercise.confirmSubmission');

        if (confirmSubmit) {
            this.submission.submitted = true;
            this.textSubmissionService.update(this.submission, this.textExercise.id).subscribe(
                response => {
                    this.submission = response.body;
                    this.result = this.submission.result;

                    if (this.isActive) {
                        this.jhiAlertService.success('arTeMiSApp.textExercise.submitSuccessful');
                    } else {
                        this.jhiAlertService.warning('arTeMiSApp.textExercise.submitDeadlineMissed');
                    }
                },
                err => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this.submission.submitted = false;
                }
            );
        }
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
