import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';

import { ISubmission, Submission } from 'app/shared/model/submission.model';
import { SubmissionService } from './submission.service';
import { IParticipation } from 'app/shared/model/participation.model';
import { ParticipationService } from 'app/entities/participation/participation.service';

@Component({
    selector: 'jhi-submission-update',
    templateUrl: './submission-update.component.html',
})
export class SubmissionUpdateComponent implements OnInit {
    isSaving = false;
    participations: IParticipation[] = [];

    editForm = this.fb.group({
        id: [],
        submitted: [],
        submissionDate: [],
        type: [],
        exampleSubmission: [],
        participation: [],
    });

    constructor(
        protected submissionService: SubmissionService,
        protected participationService: ParticipationService,
        protected activatedRoute: ActivatedRoute,
        private fb: FormBuilder,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ submission }) => {
            if (!submission.id) {
                const today = moment().startOf('day');
                submission.submissionDate = today;
            }

            this.updateForm(submission);

            this.participationService.query().subscribe((res: HttpResponse<IParticipation[]>) => (this.participations = res.body || []));
        });
    }

    updateForm(submission: ISubmission): void {
        this.editForm.patchValue({
            id: submission.id,
            submitted: submission.submitted,
            submissionDate: submission.submissionDate ? submission.submissionDate.format(DATE_TIME_FORMAT) : null,
            type: submission.type,
            exampleSubmission: submission.exampleSubmission,
            participation: submission.participation,
        });
    }

    previousState(): void {
        window.history.back();
    }

    save(): void {
        this.isSaving = true;
        const submission = this.createFromForm();
        if (submission.id !== undefined) {
            this.subscribeToSaveResponse(this.submissionService.update(submission));
        } else {
            this.subscribeToSaveResponse(this.submissionService.create(submission));
        }
    }

    private createFromForm(): ISubmission {
        return {
            ...new Submission(),
            id: this.editForm.get(['id'])!.value,
            submitted: this.editForm.get(['submitted'])!.value,
            submissionDate: this.editForm.get(['submissionDate'])!.value ? moment(this.editForm.get(['submissionDate'])!.value, DATE_TIME_FORMAT) : undefined,
            type: this.editForm.get(['type'])!.value,
            exampleSubmission: this.editForm.get(['exampleSubmission'])!.value,
            participation: this.editForm.get(['participation'])!.value,
        };
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<ISubmission>>): void {
        result.subscribe(
            () => this.onSaveSuccess(),
            () => this.onSaveError(),
        );
    }

    protected onSaveSuccess(): void {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError(): void {
        this.isSaving = false;
    }

    trackById(index: number, item: IParticipation): any {
        return item.id;
    }
}
