import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from './lecture.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faSave, faHandshakeAngle, faArrowRight, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';

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

    currentStep: number;
    isAddingLearningGoal: boolean;
    isLoadingLearningGoalForm: boolean;
    isLoadingLearningGoals: boolean;

    learningGoalToCreate: LearningGoal;
    learningGoals: LearningGoal[] = [];

    domainCommandsDescription = [new KatexCommand()];
    EditorMode = EditorMode;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSave = faSave;
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
        this.currentStep = this.lecture.startDate !== undefined || this.lecture.endDate !== undefined ? 2 : 1;
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
        return this.currentStep < 5 ? faArrowRight : faSave;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.lecture.home.nextStepLabel' : 'entity.action.save';
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

    createLearningGoal(formData: LearningGoalFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, taxonomy, connectedLectureUnits } = formData;
        this.learningGoalToCreate = new LearningGoal();

        this.learningGoalToCreate.title = title;
        this.learningGoalToCreate.description = description;
        this.learningGoalToCreate.taxonomy = taxonomy;
        this.learningGoalToCreate.lectureUnits = connectedLectureUnits;

        this.isLoadingLearningGoalForm = true;

        this.learningGoalService
            .create(this.learningGoalToCreate!, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingLearningGoalForm = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.isAddingLearningGoal = false;

                    this.alertService.success(`Learning goal ${this.learningGoalToCreate.title} was successfully created.`);
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

    editLearningGoal(learningGoal: LearningGoal) {}

    deleteLearningGoal(learningGoal: LearningGoal) {
        this.learningGoalService.delete(learningGoal.id!, this.lecture.course!.id!).subscribe({
            next: () => {
                this.learningGoals = this.learningGoals.filter((existingLearningGoal) => existingLearningGoal.id !== learningGoal.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
