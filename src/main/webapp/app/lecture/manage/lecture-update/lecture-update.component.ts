import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, effect, inject, model, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faPuzzlePiece, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { captureException } from '@sentry/angular';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { LectureUpdatePeriodComponent } from 'app/lecture/manage/lecture-period/lecture-period.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService } from 'app/shared/service/alert.service';
import { getCurrentLocaleSignal, onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import dayjs, { Dayjs } from 'dayjs/esm';
import cloneDeep from 'lodash-es/cloneDeep';
import { SelectButtonModule } from 'primeng/selectbutton';
import { LectureSeriesCreateComponent } from 'app/lecture/manage/lecture-series-create/lecture-series-create.component';
import { TranslateService } from '@ngx-translate/core';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { Observable, Subscription } from 'rxjs';
import { LectureTitleChannelNameComponent } from '../lecture-title-channel-name/lecture-title-channel-name.component';
import { LectureService } from '../services/lecture.service';

export enum LectureCreationMode {
    SINGLE = 'single',
    SERIES = 'series',
}

interface CreateLectureOption {
    label: string;
    mode: LectureCreationMode;
}

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
    styleUrls: ['./lecture-update.component.scss'],
    imports: [
        FormsModule,
        TranslateDirective,
        DocumentationButtonComponent,
        FormStatusBarComponent,
        LectureTitleChannelNameComponent,
        MarkdownEditorMonacoComponent,
        LectureUpdatePeriodComponent,
        FaIconComponent,
        LectureUpdateUnitsComponent,
        NgbTooltip,
        ArtemisTranslatePipe,
        SelectButtonModule,
        LectureSeriesCreateComponent,
        CheckboxModule,
        TooltipModule,
    ],
})
export class LectureUpdateComponent implements OnInit, OnDestroy {
    protected readonly documentationType: DocumentationType = 'Lecture';
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faSave = faSave;
    protected readonly faPuzzleProcess = faPuzzlePiece;
    protected readonly faBan = faBan;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    private readonly alertService = inject(AlertService);
    private readonly lectureService = inject(LectureService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly calendarService = inject(CalendarService);
    private readonly translateService = inject(TranslateService);
    private readonly router = inject(Router);

    private subscriptions = new Subscription();
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    titleSection = viewChild(LectureTitleChannelNameComponent);
    lecturePeriodSection = viewChild(LectureUpdatePeriodComponent);
    unitSection = viewChild(LectureUpdateUnitsComponent);
    formStatusBar = viewChild(FormStatusBarComponent);
    courseTitle = model<string>('');
    courseId = signal<number | undefined>(undefined);
    lecture = signal<Lecture>(new Lecture());
    lectureOnInit: Lecture;
    existingLectures = signal<Lecture[]>([]);
    isEditMode = signal<boolean>(false);
    isSaving: boolean;
    isProcessing: boolean;
    processUnitMode: boolean;
    formStatusSections: FormSectionStatus[];
    domainActionsDescription = [new FormulaAction()];
    file: File;
    fileName: string;
    fileInputTouched = false;
    isNewlyCreatedExercise = false;
    isChangeMadeToTitleOrPeriodSection = false;
    shouldDisplayDismissWarning = true;
    areSectionsValid = computed(() => this.computeAreSectionsValid());
    createLectureOptions = computed(() => this.computeCreateLectureOptions());
    selectedCreateLectureOption = signal<LectureCreationMode>(LectureCreationMode.SINGLE);
    isLectureSeriesCreationMode = computed(() => !this.isEditMode() && this.selectedCreateLectureOption() === LectureCreationMode.SERIES);
    isTutorialLecture = signal(false);
    tutorialLectureTooltip = computed<string>(() => this.computeTutorialLectureTooltip());

    constructor() {
        effect(() => {
            if (this.selectedCreateLectureOption() === LectureCreationMode.SERIES) return;
            const titleChannelNameComponent = this.titleSection()?.titleChannelNameComponent();
            const lecturePeriodSection = this.lecturePeriodSection();
            if (titleChannelNameComponent) {
                this.subscriptions.add(
                    titleChannelNameComponent.titleChange.subscribe(() => {
                        this.updateIsChangesMadeToTitleOrPeriodSection();
                    }),
                );
                this.subscriptions.add(
                    titleChannelNameComponent.channelNameChange.subscribe(() => {
                        this.updateIsChangesMadeToTitleOrPeriodSection();
                    }),
                );
            }
            if (lecturePeriodSection) {
                lecturePeriodSection.periodSectionDatepickers().forEach((datepicker: FormDateTimePickerComponent) => {
                    const subscription = datepicker.valueChange.subscribe(() => {
                        this.updateIsChangesMadeToTitleOrPeriodSection();
                    });
                    this.subscriptions.add(subscription);
                });
            }
        });

        effect(() => {
            this.updateFormStatusBar();
        });

        effect(
            function scrollToLastSectionAfterLectureCreation() {
                if (this.unitSection() && this.isNewlyCreatedExercise) {
                    this.isNewlyCreatedExercise = false;
                    this.formStatusBar()?.scrollToHeadline('artemisApp.lecture.sections.period');
                }
            }.bind(this),
        );

        effect(() => {
            if (this.selectedCreateLectureOption() === LectureCreationMode.SERIES) {
                this.subscriptions.unsubscribe();
                this.subscriptions = new Subscription();
            }
        });

        effect(() => {
            this.lecture().isTutorialLecture = this.isTutorialLecture();
            this.updateIsChangesMadeToTitleOrPeriodSection();
        });
    }

    ngOnInit() {
        this.isSaving = false;
        this.processUnitMode = false;
        this.isProcessing = false;
        this.activatedRoute.data.subscribe((data) => {
            // Create a new lecture to use unless we fetch an existing lecture
            const lecture = data['lecture'] as Lecture;
            this.lecture.set(lecture ?? new Lecture());
            if (lecture) {
                this.isTutorialLecture.set(lecture.isTutorialLecture ?? false);
            }
            const course = data['course'];
            if (course) {
                this.lecture().course = course;
            }
        });

        const paramMap = this.activatedRoute.parent!.snapshot.paramMap;
        this.courseId.set(Number(paramMap.get('courseId')));

        this.isEditMode.set(!this.router.url.endsWith('/new'));
        this.lectureOnInit = cloneDeep(this.lecture());
        this.courseTitle.set(this.lecture().course?.title ?? '');

        const existingLectures = (this.router.currentNavigation()?.extras.state?.['existingLectures'] ?? []) as Lecture[];
        this.existingLectures.set(existingLectures);
    }

    ngOnDestroy() {
        this.subscriptions.unsubscribe();
    }

    updateFormStatusBar() {
        const updatedFormStatusSections: FormSectionStatus[] = [];

        updatedFormStatusSections.push(
            {
                title: 'artemisApp.lecture.sections.title',
                valid: this.titleSection()?.titleChannelNameComponent().isValid() ?? false,
            },
            {
                title: 'artemisApp.lecture.sections.period',
                valid: this.lecturePeriodSection()?.isPeriodSectionValid() ?? false,
            },
        );

        if (this.isEditMode()) {
            updatedFormStatusSections.push({
                title: 'artemisApp.lecture.sections.units',
                valid: Boolean(this.unitSection()?.isUnitConfigurationValid()),
            });
        }

        this.formStatusSections = updatedFormStatusSections;
    }

    isChangeMadeToTitleSection() {
        return (
            this.lecture().title !== this.lectureOnInit.title ||
            this.lecture().channelName !== this.lectureOnInit.channelName ||
            (this.lecture().description ?? '') !== (this.lectureOnInit.description ?? '') ||
            this.lecture().isTutorialLecture !== this.lectureOnInit.isTutorialLecture
        );
    }

    isChangeMadeToPeriodSection() {
        const { visibleDate, startDate, endDate } = this.lecture();
        const { visibleDate: visibleDateOnInit, startDate: startDateOnInit, endDate: endDateOnInit } = this.lectureOnInit;

        const isInvalid = (date: Dayjs | undefined) => !dayjs(date).isValid();
        const isSame = (date1: Dayjs | undefined, date2: Dayjs | undefined) => dayjs(date1).isSame(dayjs(date2));

        const emptyVisibleDateWasCleared = !visibleDateOnInit && isInvalid(visibleDate);
        const emptyStartDateWasCleared = !startDateOnInit && isInvalid(startDate);
        const emptyEndDateWasCleared = !endDateOnInit && isInvalid(endDate);

        return (
            (!isSame(visibleDate, visibleDateOnInit) && !emptyVisibleDateWasCleared) ||
            (!isSame(startDate, startDateOnInit) && !emptyStartDateWasCleared) ||
            (!isSame(endDate, endDateOnInit) && !emptyEndDateWasCleared)
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
        this.isSaving = true;
        if (this.lecture().id !== undefined) {
            this.subscribeToSaveResponse(this.lectureService.update(this.lecture()));
        } else {
            // Newly created lectures must have a channel name, which cannot be undefined
            this.subscribeToSaveResponse(this.lectureService.create(this.lecture()));
        }
    }

    proceedToUnitSplit() {
        this.isProcessing = true;
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

        if (!lecture.course?.id) {
            captureException('Lecture has no course id: ' + lecture);
            return;
        }

        if (this.processUnitMode) {
            this.isProcessing = false;
            this.alertService.success(`Lecture with title ${lecture.title} was successfully ${this.lecture().id !== undefined ? 'updated' : 'created'}.`);
            this.router.navigate(['course-management', lecture.course.id, 'lectures', lecture.id, 'unit-management', 'attachment-video-units', 'process'], {
                state: { file: this.file, fileName: this.fileName },
            });
        } else if (this.isEditMode()) {
            this.router.navigate(['course-management', lecture.course.id, 'lectures', lecture.id]);
        } else {
            // after create we stay on the edit page, as now lecture units are available (we need the lecture id to save them)
            this.isNewlyCreatedExercise = true;
            this.isEditMode.set(true);
            this.lectureOnInit = cloneDeep(lecture);
            this.lecture.set(lecture);
            this.updateIsChangesMadeToTitleOrPeriodSection();
            this.router.navigate(['course-management', lecture.course.id, 'lectures', lecture.id, 'edit']);
            this.shouldDisplayDismissWarning = true;
        }

        this.calendarService.reloadEvents();
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

    private computeAreSectionsValid(): boolean {
        const titleSection = this.titleSection();
        const lecturePeriodSection = this.lecturePeriodSection();
        const unitSection = this.unitSection();
        if (titleSection && lecturePeriodSection) {
            if (unitSection) {
                return titleSection.titleChannelNameComponent().isValid() && lecturePeriodSection.isPeriodSectionValid() && unitSection.isUnitConfigurationValid();
            } else {
                return titleSection.titleChannelNameComponent().isValid() && lecturePeriodSection.isPeriodSectionValid();
            }
        }
        return false;
    }

    private computeCreateLectureOptions(): CreateLectureOption[] {
        this.currentLocale();
        return [
            { label: this.translateService.instant('artemisApp.lecture.creationMode.singleLectureLabel'), mode: LectureCreationMode.SINGLE },
            { label: this.translateService.instant('artemisApp.lecture.creationMode.lectureSeriesLabel'), mode: LectureCreationMode.SERIES },
        ];
    }

    private computeTutorialLectureTooltip(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.lecture.tutorialLecture.tutorialLectureTooltip');
    }
}
