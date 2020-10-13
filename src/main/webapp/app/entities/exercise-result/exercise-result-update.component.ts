import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';

import { IExerciseResult, ExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';
import { IUser } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { IParticipation } from 'app/shared/model/participation.model';
import { ParticipationService } from 'app/entities/participation/participation.service';

type SelectableEntity = IUser | ISubmission | IParticipation;

@Component({
    selector: 'jhi-exercise-result-update',
    templateUrl: './exercise-result-update.component.html',
})
export class ExerciseResultUpdateComponent implements OnInit {
    isSaving = false;
    users: IUser[] = [];
    submissions: ISubmission[] = [];
    participations: IParticipation[] = [];

    editForm = this.fb.group({
        id: [],
        resultString: [],
        completionDate: [],
        successful: [],
        buildArtifact: [],
        score: [],
        rated: [],
        hasFeedback: [],
        assessmentType: [],
        hasComplaint: [],
        exampleResult: [],
        assessor: [],
        submission: [],
        participation: [],
    });

    constructor(
        protected exerciseResultService: ExerciseResultService,
        protected userService: UserService,
        protected submissionService: SubmissionService,
        protected participationService: ParticipationService,
        protected activatedRoute: ActivatedRoute,
        private fb: FormBuilder,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ exerciseResult }) => {
            if (!exerciseResult.id) {
                const today = moment().startOf('day');
                exerciseResult.completionDate = today;
            }

            this.updateForm(exerciseResult);

            this.userService.query().subscribe((res: HttpResponse<IUser[]>) => (this.users = res.body || []));

            this.submissionService.query().subscribe((res: HttpResponse<ISubmission[]>) => (this.submissions = res.body || []));

            this.participationService.query().subscribe((res: HttpResponse<IParticipation[]>) => (this.participations = res.body || []));
        });
    }

    updateForm(exerciseResult: IExerciseResult): void {
        this.editForm.patchValue({
            id: exerciseResult.id,
            resultString: exerciseResult.resultString,
            completionDate: exerciseResult.completionDate ? exerciseResult.completionDate.format(DATE_TIME_FORMAT) : null,
            successful: exerciseResult.successful,
            buildArtifact: exerciseResult.buildArtifact,
            score: exerciseResult.score,
            rated: exerciseResult.rated,
            hasFeedback: exerciseResult.hasFeedback,
            assessmentType: exerciseResult.assessmentType,
            hasComplaint: exerciseResult.hasComplaint,
            exampleResult: exerciseResult.exampleResult,
            assessor: exerciseResult.assessor,
            submission: exerciseResult.submission,
            participation: exerciseResult.participation,
        });
    }

    previousState(): void {
        window.history.back();
    }

    save(): void {
        this.isSaving = true;
        const exerciseResult = this.createFromForm();
        if (exerciseResult.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseResultService.update(exerciseResult));
        } else {
            this.subscribeToSaveResponse(this.exerciseResultService.create(exerciseResult));
        }
    }

    private createFromForm(): IExerciseResult {
        return {
            ...new ExerciseResult(),
            id: this.editForm.get(['id'])!.value,
            resultString: this.editForm.get(['resultString'])!.value,
            completionDate: this.editForm.get(['completionDate'])!.value ? moment(this.editForm.get(['completionDate'])!.value, DATE_TIME_FORMAT) : undefined,
            successful: this.editForm.get(['successful'])!.value,
            buildArtifact: this.editForm.get(['buildArtifact'])!.value,
            score: this.editForm.get(['score'])!.value,
            rated: this.editForm.get(['rated'])!.value,
            hasFeedback: this.editForm.get(['hasFeedback'])!.value,
            assessmentType: this.editForm.get(['assessmentType'])!.value,
            hasComplaint: this.editForm.get(['hasComplaint'])!.value,
            exampleResult: this.editForm.get(['exampleResult'])!.value,
            assessor: this.editForm.get(['assessor'])!.value,
            submission: this.editForm.get(['submission'])!.value,
            participation: this.editForm.get(['participation'])!.value,
        };
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<IExerciseResult>>): void {
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

    trackById(index: number, item: SelectableEntity): any {
        return item.id;
    }
}
