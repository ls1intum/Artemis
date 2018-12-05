import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise, TextExerciseService } from 'app/entities/text-exercise';
import { Result } from 'app/entities/result';
import { Participation, ParticipationService } from 'app/entities/participation';
import { TextEditorService } from 'app/text-editor/text-editor.service';
import * as moment from 'moment';
import { HighlightColors } from 'app/text-shared/highlight-colors';

@Component({
    templateUrl: './text-editor.component.html',
    providers: [ParticipationService]
})
export class TextEditorComponent implements OnInit, OnDestroy {
    private subscription: Subscription;

    textExercise: TextExercise;
    participation: Participation;
    result: Result;
    submission: TextSubmission;
    isActive: boolean;
    isSaving: boolean;
    answer: string;

    public getColorForIndex = HighlightColors.forIndex;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private textSubmissionService: TextSubmissionService,
        private textService: TextEditorService,
        private jhiAlertService: JhiAlertService
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.textService.get(params['participationId']).subscribe(
                    (data: Participation) => {
                        this.participation = data;
                        this.textExercise = this.participation.exercise as TextExercise;

                        this.submission = data.submissions[0] as TextSubmission;
                        if (this.submission && data.results) {
                            this.result = data.results.find(r => r.submission.id === this.submission.id);
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

        this.textSubmissionService[this.submission.id ? 'update' : 'create'](this.submission, this.textExercise.id).subscribe(
            response => {
                if (response) {
                    this.submission = response.body;
                    this.result = this.submission.result;
                    this.jhiAlertService.success('arTeMiSApp.textExercise.saveSuccessful');

                    this.isSaving = false;
                }
            },
            e => {
                this.jhiAlertService.error('arTeMiSApp.textExercise.error');
                this.isSaving = false;
            }
        );
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
