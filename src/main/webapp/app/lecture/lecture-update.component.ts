import { Component, OnDestroy, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { faBan, faPuzzlePiece, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { FormSectionStatus, FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import cloneDeep from 'lodash-es/cloneDeep';
import dayjs from 'dayjs';
import { LectureUpdateUnitsComponent } from 'app/lecture/lecture-units/lecture-units.component';
import { LectureUpdatePeriodComponent } from 'app/lecture/lecture-period/lecture-period.component';

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
    styleUrls: ['./lecture-update.component.scss'],
})
export class LectureUpdateComponent implements OnInit, OnDestroy {
    protected readonly documentationType: DocumentationType = 'Lecture';
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faSave = faSave;
    protected readonly faPuzzleProcess = faPuzzlePiece;
    protected readonly faBan = faBan;
    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    private readonly alertService = inject(AlertService);
    private readonly lectureService = inject(LectureService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly router = inject(Router);

    titleSection = viewChild.required(LectureTitleChannelNameComponent);
    lecturePeriodSection = viewChild.required(LectureUpdatePeriodComponent);
    attachmentsSection = viewChild(LectureAttachmentsComponent);
    unitSection = viewChild(LectureUpdateUnitsComponent);
    formStatusBar = viewChild(FormStatusBarComponent);

    isEditMode = signal<boolean>(false);
    pressedSave: boolean = false;
    lecture = signal<Lecture>(new Lecture());
    lectureOnInit: Lecture;
    isSaving: boolean;
    isProcessing: boolean;
    processUnitMode: boolean;

    formStatusSections: FormSectionStatus[];

    courses: Course[];

    domainActionsDescription = [new FormulaAction()];
    file: File;
    fileName: string;
    fileInputTouched = false;

    isNewlyCreatedExercise = false;

    isChangeMadeToTitleOrPeriodSection = false;
    shouldDisplayDismissWarning = true;

    private subscriptions = new Subscription();

    constructor() {
        effect(() => {
            this.subscriptions.add(
                this.titleSection()
                    .titleChannelNameComponent()
                    .titleChange.subscribe(() => {
                        this.updateIsChangesMadeToTitleOrPeriodSection();
                    }),
            );
            this.subscriptions.add(
                this.titleSection()
                    .titleChannelNameComponent()
                    .channelNameChange.subscribe(() => {
                        this.updateIsChangesMadeToTitleOrPeriodSection();
                    }),
            );
            this.subscriptions.add(
                this.lecturePeriodSection()
                    .periodSectionDatepickers()
                    .forEach((datepicker) => {
                        datepicker.valueChange.subscribe(() => {
                            this.updateIsChangesMadeToTitleOrPeriodSection();
                        });
                    }),
            );
        });

        effect(
            function updateFormStatusBarAfterLectureCreation() {
                const updatedFormStatusSections: FormSectionStatus[] = [];

                if (this.isEditMode()) {
                    updatedFormStatusSections.push(
                        {
                            title: 'artemisApp.lecture.wizardMode.steps.attachmentsStepTitle',
                            valid: Boolean(this.attachmentsSection()?.isFormValid()),
                        },
                        {
                            title: 'artemisApp.lecture.wizardMode.steps.unitsStepTitle',
                            valid: Boolean(this.unitSection()?.isUnitConfigurationValid()),
                        },
                    );
                }

                updatedFormStatusSections.unshift(
                    {
                        title: 'artemisApp.lecture.wizardMode.steps.titleStepTitle',
                        valid: Boolean(this.titleSection().titleChannelNameComponent().isFormValidSignal()),
                    },
                    {
                        title: 'artemisApp.lecture.wizardMode.steps.periodStepTitle',
                        valid: Boolean(this.lecturePeriodSection().isPeriodSectionValid()),
                    },
                );

                this.formStatusSections = updatedFormStatusSections;
            }.bind(this),
        );

        effect(
            function scrollToLastSectionAfterLectureCreation() {
                if (this.unitSection() && this.isNewlyCreatedExercise) {
                    this.isNewlyCreatedExercise = false;
                    this.formStatusBar()?.scrollToHeadline('artemisApp.lecture.wizardMode.steps.periodStepTitle');
                }
            }.bind(this),
        );
    }

    ngOnInit() {
        this.isSaving = false;
        this.processUnitMode = false;
        this.isProcessing = false;
        this.activatedRoute.parent!.data.subscribe((data) => {
            // Create a new lecture to use unless we fetch an existing lecture
            const lecture = data['lecture'];
            this.lecture.set(lecture ?? new Lecture());
            const course = data['course'];
            if (course) {
                this.lecture().course = course;
            }
        });

        this.isEditMode.set(!this.router.url.endsWith('/new'));
        this.lectureOnInit = cloneDeep(this.lecture());
    }

    ngOnDestroy() {
        this.subscriptions.unsubscribe();
    }

    isChangeMadeToTitleSection() {
        return (
            this.lecture().title !== this.lectureOnInit.title ||
            this.lecture().channelName !== this.lectureOnInit.channelName ||
            this.lecture().description !== this.lectureOnInit.description
        );
    }

    isChangeMadeToPeriodSection() {
        return (
            !dayjs(this.lecture().visibleDate).isSame(dayjs(this.lectureOnInit.visibleDate)) ||
            !dayjs(this.lecture().startDate).isSame(dayjs(this.lectureOnInit.startDate)) ||
            !dayjs(this.lecture().endDate).isSame(dayjs(this.lectureOnInit.endDate))
        );
    }

    protected updateIsChangesMadeToTitleOrPeriodSection() {
        this.isChangeMadeToTitleOrPeriodSection = this.isChangeMadeToTitleSection() || this.isChangeMadeToPeriodSection();
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state, and we edited an existing lecture
     * Returns to the overview page if there is no previous state, and we created a new lecture
     */
    previousState() {
        this.shouldDisplayDismissWarning = false;
        this.navigationUtilService.navigateBackWithOptional(['course-management', this.lecture().course!.id!.toString(), 'lectures'], this.lecture().id?.toString());
    }

    /**
     * Save the changes on a lecture
     * This function is called by pressing save after creating or editing a lecture
     */
    save() {
        this.shouldDisplayDismissWarning = false;
        this.pressedSave = true;
        this.isSaving = true;
        this.isProcessing = true;
        if (this.lecture().id !== undefined) {
            this.subscribeToSaveResponse(this.lectureService.update(this.lecture()));
        } else {
            // Newly created lectures must have a channel name, which cannot be undefined
            this.subscribeToSaveResponse(this.lectureService.create(this.lecture()));
        }
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

        if (this.processUnitMode) {
            this.isProcessing = false;
            this.alertService.success(`Lecture with title ${lecture.title} was successfully ${this.lecture().id !== undefined ? 'updated' : 'created'}.`);
            this.router.navigate(['course-management', lecture.course!.id, 'lectures', lecture.id, 'unit-management', 'attachment-units', 'process'], {
                state: { file: this.file, fileName: this.fileName },
            });
        } else if (this.isEditMode()) {
            this.router.navigate(['course-management', lecture.course!.id, 'lectures', lecture.id]);
        } else {
            // after create we stay on the edit page, as now attachments and lecture units are available (we need the lecture id to save them)
            this.isNewlyCreatedExercise = true;
            this.isEditMode.set(true);
            this.lecture.set(lecture);
            window.history.replaceState({}, '', `course-management/${lecture.course!.id}/lectures/${lecture.id}/edit`);
            this.shouldDisplayDismissWarning = true;
        }
    }

    /**
     * Action on unsuccessful lecture creation or edit
     * @param errorRes the errorRes handed to the alert service
     */
    protected onSaveError(errorRes: HttpErrorResponse) {
        this.isSaving = false;

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
