import { Component, Input } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { onError } from 'app/shared/util/global.utils';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from 'app/lecture/lecture.service';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { finalize } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-lecture-update-wizard-learning-goals',
    templateUrl: './lecture-wizard-learning-goals.component.html',
})
export class LectureUpdateWizardLearningGoalsComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;

    isAddingLearningGoal: boolean;
    isLoadingLearningGoalForm: boolean;
    isLoadingLearningGoals: boolean;
    isEditingLearningGoal: boolean;

    currentlyProcessedLearningGoal: LearningGoal;
    learningGoals: LearningGoal[] = [];
    learningGoalFormData: LearningGoalFormData;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faPencilAlt = faPencilAlt;

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected learningGoalService: LearningGoalService,
        protected translateService: TranslateService,
    ) {}

    showCreateLearningGoal() {
        this.isLoadingLearningGoalForm = true;
        this.isAddingLearningGoal = !this.isAddingLearningGoal;
        this.learningGoalFormData = {
            id: undefined,
            title: undefined,
            description: undefined,
            taxonomy: undefined,
            connectedLectureUnits: undefined,
        };

        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    protected subscribeToLoadUnitResponse(result: Observable<HttpResponse<Lecture>>) {
        result.subscribe({
            next: (response: HttpResponse<Lecture>) => this.onLoadUnitSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onLoadError(error),
        });
    }

    protected subscribeToLoadLearningGoalsResponse(result: Observable<HttpResponse<LearningGoal[]>>) {
        result.subscribe({
            next: (response: HttpResponse<LearningGoal[]>) => this.onLoadLearningGoalsSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onLoadError(error),
        });
    }

    /**
     * Action on successful lecture unit fetch
     */
    protected onLoadUnitSuccess(lecture: Lecture) {
        this.lecture = lecture;

        this.isLoadingLearningGoalForm = false;
    }

    /**
     * Action on successful learning goals fetch
     */
    protected onLoadLearningGoalsSuccess(learningGoals: LearningGoal[]) {
        this.isLoadingLearningGoals = false;

        this.learningGoals = learningGoals;
    }

    /**
     * Action on unsuccessful fetch
     * @param error the error handed to the alert service
     */
    protected onLoadError(error: HttpErrorResponse) {
        this.isSaving = false;
        this.isLoadingLearningGoalForm = false;
        this.isLoadingLearningGoals = false;

        onError(this.alertService, error);
    }

    onLearningGoalFormSubmitted(formData: LearningGoalFormData) {
        if (this.isEditingLearningGoal) {
            this.editLearningGoal(formData);
        } else {
            this.createLearningGoal(formData);
        }
    }

    createLearningGoal(formData: LearningGoalFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, taxonomy, connectedLectureUnits } = formData;
        this.currentlyProcessedLearningGoal = new LearningGoal();

        this.currentlyProcessedLearningGoal.title = title;
        this.currentlyProcessedLearningGoal.description = description;
        this.currentlyProcessedLearningGoal.taxonomy = taxonomy;
        this.currentlyProcessedLearningGoal.lectureUnits = connectedLectureUnits;

        this.isLoadingLearningGoalForm = true;

        this.learningGoalService
            .create(this.currentlyProcessedLearningGoal!, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingLearningGoalForm = false;
                }),
            )
            .subscribe({
                next: (response: HttpResponse<LearningGoal>) => {
                    this.isAddingLearningGoal = false;
                    this.learningGoals = this.learningGoals.concat(response.body!);

                    this.alertService.success(`Learning goal ${this.currentlyProcessedLearningGoal.title} was successfully created.`);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    editLearningGoal(formData: LearningGoalFormData) {
        const { title, description, taxonomy, connectedLectureUnits } = formData;

        this.currentlyProcessedLearningGoal.title = title;
        this.currentlyProcessedLearningGoal.description = description;
        this.currentlyProcessedLearningGoal.taxonomy = taxonomy;
        this.currentlyProcessedLearningGoal.lectureUnits = connectedLectureUnits;

        this.isLoadingLearningGoalForm = true;

        this.learningGoalService
            .update(this.currentlyProcessedLearningGoal, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingLearningGoalForm = false;
                }),
            )
            .subscribe({
                next: (response: HttpResponse<LearningGoal>) => {
                    this.isEditingLearningGoal = false;
                    const index = this.learningGoals.findIndex((learningGoal) => learningGoal.id === this.currentlyProcessedLearningGoal.id);
                    if (index === -1) {
                        this.learningGoals = this.learningGoals.concat(response.body!);
                    } else {
                        this.learningGoals[index] = response.body!;
                    }

                    this.currentlyProcessedLearningGoal = new LearningGoal();
                    this.alertService.success(`Learning goal ${this.currentlyProcessedLearningGoal.title} was successfully edited.`);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    trackLearningGoalId(index: number, item: LearningGoal) {
        return item.id;
    }

    loadLearningGoals() {
        this.isLoadingLearningGoals = true;

        this.subscribeToLoadLearningGoalsResponse(this.learningGoalService.getAllForCourse(this.lecture.course!.id!));
    }

    getConnectedUnitsForLearningGoal(learningGoal: LearningGoal) {
        const units = learningGoal.lectureUnits?.filter((unit) => unit.lecture?.id === this.lecture.id);

        if (units === undefined || units.length === 0) {
            return this.translateService.instant('artemisApp.lecture.wizardMode.learningGoalNoConnectedUnits');
        }

        return units.map((unit) => unit.name).join(', ');
    }

    startEditLearningGoal(learningGoal: LearningGoal) {
        const connectedUnits: LectureUnit[] = [];
        learningGoal.lectureUnits!.forEach((unit) => connectedUnits.push(Object.assign({}, unit)));

        this.learningGoalFormData = {
            id: learningGoal.id,
            title: learningGoal.title,
            description: learningGoal.description,
            taxonomy: learningGoal.taxonomy,
            connectedLectureUnits: connectedUnits,
        };

        this.isLoadingLearningGoalForm = true;
        this.isEditingLearningGoal = true;
        this.currentlyProcessedLearningGoal = learningGoal;

        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    deleteLearningGoal(learningGoal: LearningGoal) {
        this.learningGoalService.delete(learningGoal.id!, this.lecture.course!.id!).subscribe({
            next: () => {
                this.learningGoals = this.learningGoals.filter((existingLearningGoal) => existingLearningGoal.id !== learningGoal.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    onLearningGoalFormCanceled() {
        this.isAddingLearningGoal = false;
        this.isEditingLearningGoal = false;
        this.isLoadingLearningGoalForm = false;

        this.currentlyProcessedLearningGoal = new LearningGoal();
    }
}
