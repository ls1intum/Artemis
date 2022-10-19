import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from '../lecture.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faCheck, faHandshakeAngle, faArrowRight, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';

@Component({
    selector: 'jhi-lecture-update-wizard',
    templateUrl: './lecture-update-wizard.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() saveLectureFunction: () => void;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;
    @Input() startDate: string;
    @Input() endDate: string;

    @ViewChild(LectureUpdateWizardUnitsComponent, { static: false }) unitsComponent: LectureUpdateWizardUnitsComponent;

    currentStep: number;
    isAddingLearningGoal: boolean;
    isLoadingLearningGoalForm: boolean;
    isLoadingLearningGoals: boolean;
    isEditingLearningGoal: boolean;

    currentlyProcessedLearningGoal: LearningGoal;
    learningGoals: LearningGoal[] = [];
    learningGoalFormData: LearningGoalFormData;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faCheck = faCheck;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;
    faPencilAlt = faPencilAlt;

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected learningGoalService: LearningGoalService,
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldOpenCreateExercise) {
                this.unitsComponent.onCreateLectureUnit(LectureUnitType.EXERCISE);
            }

            if (params.step) {
                this.currentStep = params.step;
            } else {
                this.currentStep = this.lecture.id ? 5 : this.lecture.startDate !== undefined || this.lecture.endDate !== undefined ? 2 : 1;
            }

            this.router.navigate([], {
                relativeTo: this.activatedRoute,
                queryParamsHandling: '',
                replaceUrl: true,
            });
        });
    }

    /**
     * Progress to the next step of the wizard mode
     */
    next() {
        if (this.currentStep === 2) {
            this.saveLectureFunction();
            return;
        }

        this.currentStep++;

        if (this.currentStep === 5) {
            this.loadLearningGoals();
        }

        if (this.currentStep > 5) {
            this.toggleWizardMode();
        }
    }

    onLectureCreationSucceeded() {
        this.currentStep++;
    }

    /**
     * Checks if the given step has already been completed
     */
    isCompleted(step: number) {
        return this.currentStep > step;
    }

    /**
     * Checks if the given step is the current one
     */
    isCurrent(step: number) {
        return this.currentStep === step;
    }

    getNextIcon() {
        return this.currentStep < 5 ? faArrowRight : faCheck;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.lecture.home.nextStepLabel' : 'entity.action.finish';
    }

    toggleWizardMode() {
        if (this.currentStep <= 2) {
            this.toggleModeFunction();
        } else {
            this.router.navigate(['course-management', this.lecture.course!.id, 'lectures', this.lecture.id]);
        }
    }

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
            return 'No connected units';
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
