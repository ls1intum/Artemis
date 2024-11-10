import { Component, OnInit, Signal, ViewChild, computed, effect, signal, viewChild, viewChildren } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from './lecture.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { faBan, faHandshakeAngle, faPuzzlePiece, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { LectureUpdateWizardComponent } from 'app/lecture/wizard-mode/lecture-update-wizard.component';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/difficulty/programming-exercise-difficulty.component';
import { FormSectionStatus } from 'app/forms/form-status-bar/form-status-bar.component';
import { LectureUpdateWizardPeriodComponent } from 'app/lecture/wizard-mode/lecture-wizard-period.component';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
    styleUrls: ['./lecture-update.component.scss'],
})
export class LectureUpdateComponent implements OnInit {
    protected readonly documentationType: DocumentationType = 'Lecture';
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faSave = faSave;
    protected readonly faPuzzleProcess = faPuzzlePiece;
    protected readonly faBan = faBan;
    protected readonly faHandShakeAngle = faHandshakeAngle;
    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    isEditMode = signal<boolean>(false);

    @ViewChild(LectureUpdateWizardComponent, { static: false }) wizardComponent: LectureUpdateWizardComponent;
    @ViewChild(ProgrammingExerciseDifficultyComponent) lecturePeriodComponent?: LectureUpdateWizardPeriodComponent;
    titleSection = viewChild.required(LectureTitleChannelNameComponent);
    periodSectionDatepickers = viewChildren(FormDateTimePickerComponent);
    unitSection = viewChild(LectureUpdateWizardUnitsComponent);

    isPeriodSectionValid: Signal<boolean> = computed(() => {
        for (const periodSectionDatepicker of this.periodSectionDatepickers()) {
            if (!periodSectionDatepicker.isValid()) {
                return false;
            }
        }
        return true;
    });

    lecture = signal<Lecture>(new Lecture());
    isSaving: boolean;
    isProcessing: boolean;
    processUnitMode: boolean;
    isShowingWizardMode: boolean;
    /**
     * Adding attachments and lecture units requires a lecture id, we save the lecture in the background on creation
     * to make sure the id is present in case an attachment / lecture unit is added
     */
    isAutomaticSaveOnCreation: boolean = true;

    formStatusSections: FormSectionStatus[];

    courses: Course[];

    domainActionsDescription = [new FormulaAction()];
    file: File;
    fileName: string;
    fileInputTouched = false;

    toggleModeFunction = () => this.toggleWizardMode();
    saveLectureFunction = () => this.save();

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {
        effect(() => {
            // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
            const updatedFormStatusSections: FormSectionStatus[] = [
                {
                    title: 'artemisApp.lecture.wizardMode.steps.titleStepTitle',
                    valid: Boolean(this.titleSection().titleChannelNameComponent().isFormValidSignal()),
                },
                {
                    title: 'artemisApp.lecture.wizardMode.steps.periodStepTitle',
                    valid: Boolean(this.isPeriodSectionValid()),
                },
                {
                    title: 'artemisApp.lecture.wizardMode.steps.attachmentsStepTitle',
                    valid: true, // TODO retrieve the valid status
                },
                {
                    title: 'artemisApp.lecture.wizardMode.steps.unitsStepTitle',
                    valid: Boolean(this.unitSection()?.isUnitConfigurationValid()),
                },
            ];

            this.formStatusSections = updatedFormStatusSections;
        });
    }

    ngOnInit() {
        this.isSaving = false;
        this.processUnitMode = false;
        this.isProcessing = false;
        this.isShowingWizardMode = false;
        this.activatedRoute.parent!.data.subscribe((data) => {
            // Create a new lecture to use unless we fetch an existing lecture
            const lecture = data['lecture'];
            this.lecture.set(lecture ?? new Lecture());
            const course = data['course'];
            if (course) {
                this.lecture().course = course;
            }
        });

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldBeInWizardMode) {
                this.isShowingWizardMode = params.shouldBeInWizardMode;
            }
        });

        this.isEditMode.set(!this.router.url.endsWith('/new'));

        if (!this.isEditMode()) {
            this.save(true);
        }
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state, and we edited an existing lecture
     * Returns to the overview page if there is no previous state, and we created a new lecture
     */
    previousState() {
        this.navigationUtilService.navigateBackWithOptional(['course-management', this.lecture().course!.id!.toString(), 'lectures'], this.lecture().id?.toString());
    }

    /**
     * Save the changes on a lecture
     * This function is called by pressing save after creating or editing a lecture
     */
    save(isAutomaticSaveOnCreation: boolean = false) {
        this.isSaving = true;
        this.isProcessing = true;
        this.isAutomaticSaveOnCreation = isAutomaticSaveOnCreation;
        if (this.lecture().id !== undefined) {
            this.subscribeToSaveResponse(this.lectureService.update(this.lecture()));
        } else {
            // Newly created lectures must have a channel name, which cannot be undefined
            this.subscribeToSaveResponse(this.lectureService.create(this.lecture()));
        }
    }

    /**
     * Activate or deactivate the wizard mode for easier lecture creation.
     * This function is called by pressing "Switch to guided mode" when creating a new lecture
     */
    toggleWizardMode() {
        this.isShowingWizardMode = !this.isShowingWizardMode;
    }

    proceedToUnitSplit() {
        this.save();
    }

    /**
     * Activate or deactivate the processUnitMode mode for automatic lecture units creation.
     * This function is called by checking Automatic unit processing checkbox when creating a new lecture
     */
    onSelectProcessUnit() {
        this.processUnitMode = !this.processUnitMode;
    }

    onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            this.fileName = '';
            return;
        }
        this.file = input.files[0];
        this.fileName = this.file.name;
    }

    /**
     * @callback callback after saving a lecture, handles appropriate action in case of error
     * @param result The Http response from the server
     */
    protected subscribeToSaveResponse(result: Observable<HttpResponse<Lecture>>) {
        result.subscribe({
            next: (response: HttpResponse<Lecture>) => this.onSaveSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onSaveError(error),
        });
    }

    /**
     * Action on successful lecture creation or edit
     */
    protected onSaveSuccess(lecture: Lecture) {
        this.isSaving = false;

        const isAutomaticSaveForIdForAttachmentsAndUnits = !this.isEditMode() && this.isAutomaticSaveOnCreation;
        if (isAutomaticSaveForIdForAttachmentsAndUnits) {
            this.lectureService.findWithDetails(lecture.id!).subscribe({
                next: (response: HttpResponse<Lecture>) => {
                    this.lecture.set(response.body!);
                },
            });
        } else if (this.isShowingWizardMode && !this.lecture().id) {
            this.lectureService.findWithDetails(lecture.id!).subscribe({
                next: (response: HttpResponse<Lecture>) => {
                    this.lecture.set(response.body!);
                    this.alertService.success(`Lecture with title ${lecture.title} was successfully created.`);
                    this.wizardComponent.onLectureCreationSucceeded();
                },
            });
        } else if (this.processUnitMode) {
            this.isProcessing = false;
            this.alertService.success(`Lecture with title ${lecture.title} was successfully ${this.lecture().id !== undefined ? 'updated' : 'created'}.`);
            this.router.navigate(['course-management', lecture.course!.id, 'lectures', lecture.id, 'unit-management', 'attachment-units', 'process'], {
                state: { file: this.file, fileName: this.fileName },
            });
        } else {
            this.router.navigate(['course-management', lecture.course!.id, 'lectures', lecture.id]);
        }
    }

    /**
     * Action on unsuccessful lecture creation or edit
     * @param errorRes the errorRes handed to the alert service
     */
    protected onSaveError(errorRes: HttpErrorResponse) {
        this.isSaving = false;

        const isAutomaticSaveForIdForAttachmentsAndUnits = this.isAutomaticSaveOnCreation;
        if (isAutomaticSaveForIdForAttachmentsAndUnits) {
            return;
        }
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
    }

    onDatesValuesChanged() {
        const startDate = this.lecture().startDate;
        const endDate = this.lecture().endDate;
        const visibleDate = this.lecture().visibleDate;

        // Prevent endDate from being before startDate, if both dates are set
        if (endDate && startDate?.isAfter(endDate)) {
            this.lecture().endDate = startDate.clone();
        }

        // Prevent visibleDate from being after startDate, if both dates are set
        if (visibleDate && startDate?.isBefore(visibleDate)) {
            this.lecture().visibleDate = startDate.clone();
        }
    }
}
