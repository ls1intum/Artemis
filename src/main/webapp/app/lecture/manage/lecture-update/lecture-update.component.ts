import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { Observable, Subscription, of } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleCheck, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { captureException } from '@sentry/angular';
import { FormulaAction } from 'app/editor/monaco-editor/model/actions/formula.action';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal, onError } from 'app/foundation/util/global.utils';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormSectionStatus } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';
import { LectureTitleChannelNameComponent } from '../lecture-title-channel-name/lecture-title-channel-name.component';
import { LectureSeriesCreateComponent } from 'app/lecture/manage/lecture-series-create/lecture-series-create.component';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { LectureTimelineComponent } from 'app/lecture/manage/lecture-period/lecture-timeline.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { TranslateService } from '@ngx-translate/core';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { LectureService } from '../services/lecture.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnsavedChangesComponent } from 'app/lecture/manage/hasLectureUnsavedChanges.guard';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonGroupComponent,
    TumAetUiCheckboxComponent,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationRequest,
    TumAetUiConfirmationService,
    TumAetUiMessageComponent,
    TumAetUiSelectButtonComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { LectureEditFooterComponent } from 'app/lecture/manage/lecture-update/lecture-edit-footer/lecture-edit-footer.component';
import { PDF_UPLOAD_CONFIRMATION_STATE_KEY, PdfUploadConfirmation } from 'app/lecture/manage/lecture-update/pdf-upload-confirmation.model';
import { isSingleLineField } from 'app/lecture/manage/single-line-field.util';

/** Navigation state that carries the time a new lecture was saved to its editor, which the router creates anew for the edit route. */
const DETAILS_SAVED_AT_STATE_KEY = 'lectureDetailsSavedAt';

export enum LectureCreationMode {
    SINGLE = 'single',
    SERIES = 'series',
}

interface CreateLectureOption {
    label: string;
    mode: LectureCreationMode;
}

/** The translation keys and values of the confirmation shown after PDFs were dropped on the lecture list. */
interface PdfUploadConfirmationText {
    title: string;
    body: string;
    params: Record<string, string | number>;
    release?: string;
    releaseDate?: Dayjs;
}

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
    styleUrls: ['./lecture-update.component.scss'],
    // Enter is handled on the host: the sections it saves are no controls themselves, so they take no key handlers.
    host: { '(window:beforeunload)': 'onBeforeUnload($event)', '(keydown.enter)': 'onEnterInDetails($event)' },
    imports: [
        FormsModule,
        TranslateDirective,
        DocumentationButtonComponent,
        LectureTitleChannelNameComponent,
        MarkdownEditorMonacoComponent,
        LectureTimelineComponent,
        FaIconComponent,
        LectureUpdateUnitsComponent,
        ArtemisTranslatePipe,
        LectureSeriesCreateComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        TumAetUiButtonComponent,
        TumAetUiButtonGroupComponent,
        TumAetUiCheckboxComponent,
        TumAetUiConfirmDialogComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectButtonComponent,
        TumAetUiTooltipDirective,
        ArtemisDatePipe,
        LectureEditFooterComponent,
    ],
    providers: [TumAetUiConfirmationService],
})
export class LectureUpdateComponent implements OnInit, LectureUnsavedChangesComponent {
    private readonly alertService = inject(AlertService);
    private readonly lectureService = inject(LectureService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly calendarService = inject(CalendarService);
    private readonly translateService = inject(TranslateService);
    private readonly router = inject(Router);
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly location = inject(Location);
    private readonly destroyRef = inject(DestroyRef);

    /**
     * Read while the router creates the page: without zone.js, ngOnInit runs at the first change detection, after the navigation has ended.
     * It keeps the objects the previous page handed over, whereas the browser history only holds structured clones of them.
     */
    private readonly navigationState = this.router.currentNavigation()?.extras.state;

    protected readonly documentationType: DocumentationType = 'Lecture';
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    private currentLocale = getCurrentLocaleSignal(this.translateService);

    titleSection = viewChild(LectureTitleChannelNameComponent);
    unitSection = viewChild(LectureUpdateUnitsComponent);
    private readonly detailsSection = viewChild<ElementRef<HTMLElement>>('detailsSection');
    private readonly periodSection = viewChild<ElementRef<HTMLElement>>('periodSection');
    courseId = signal<number | undefined>(undefined);
    lecture = signal<Lecture>(new Lecture());
    lectureOnInit!: Lecture; // set in ngOnInit() (and re-cloned on save success)
    existingLectures = signal<Lecture[]>([]);
    isEditMode = signal<boolean>(false);
    readonly isSaving = signal<boolean>(undefined!);
    readonly formStatusSections = signal<FormSectionStatus[]>(undefined!);
    domainActionsDescription = [new FormulaAction()];
    readonly isChangeMadeToTitleOrPeriodSection = signal(false);
    /** Translation keys of the sections whose unsaved changes the footer names. */
    readonly changedSections = signal<string[]>([]);
    /** Translation keys of the sections whose changes leaving the page would discard: the details, and content that could not be saved. */
    protected readonly unsavedSections = computed(() =>
        this.unitSection()?.hasUnsavedContent() ? [...this.changedSections(), 'artemisApp.lecture.sections.units'] : this.changedSections(),
    );
    /** What leaving the page would discard: the single-lecture form is not shown while a series is created. */
    readonly hasUnsavedChanges = computed(() => !this.isLectureSeriesCreationMode() && (this.isChangeMadeToTitleOrPeriodSection() || !!this.unitSection()?.hasUnsavedContent()));
    /** When the lecture details were last saved on this page, for the footer's confirmation. */
    readonly lastSavedAt = signal<dayjs.Dayjs | undefined>(undefined);
    /** Set when the lecture list opened this page after PDFs were dropped on it; cleared when the user dismisses it. */
    readonly pdfUploadConfirmation = signal<PdfUploadConfirmation | undefined>(undefined);
    readonly pdfUploadConfirmationText = computed(() => this.computePdfUploadConfirmationText());
    readonly isPeriodValid = signal(true);
    /** Translation key of why the lecture details cannot be saved, or undefined when they can. */
    readonly saveBlockedReason = computed(() => this.computeSaveBlockedReason());
    /** The lecture as last sent to the server, which becomes the saved state when the server accepts it. */
    private sentLecture?: Lecture;
    /** Set when this editor replaced the creation page, which may have been the first page of the browser tab. */
    private openedAfterCreation = false;
    /** The decision about leaving that Close or Cancel of the footer waits for; a newer attempt or leaving the page ends it. */
    private leaveAttempt?: Subscription;
    shouldDisplayDismissWarning = true;
    createLectureOptions = computed(() => this.computeCreateLectureOptions());
    selectedCreateLectureOption = signal<LectureCreationMode>(LectureCreationMode.SINGLE);
    isLectureSeriesCreationMode = computed(() => !this.isEditMode() && this.selectedCreateLectureOption() === LectureCreationMode.SERIES);
    isTutorialLecture = signal(false);

    constructor() {
        this.destroyRef.onDestroy(() => this.leaveAttempt?.unsubscribe());
        effect(() => {
            this.updateFormStatusBar();
        });

        // Reviewed for the effect()-debt cleanup (P2.2) and intentionally kept as an effect(): it writes the
        // isTutorialLecture toggle into the (non-signal) lecture entity in place, a side effect that a computed()
        // cannot perform, so the value is present on the object that save() later sends, and refreshes the derived
        // "changes made" flag. (The effect above refreshes the section links, so it is a genuine side effect too.)
        effect(() => {
            this.lecture().isTutorialLecture = this.isTutorialLecture();
            this.updateIsChangesMadeToTitleOrPeriodSection();
        });
    }

    ngOnInit() {
        this.isSaving.set(false);
        this.activatedRoute.data.subscribe((data) => {
            // Create a new lecture to use unless we fetch an existing lecture
            const lecture = data['lecture'] as Lecture;
            const newLecture = lecture ?? new Lecture();
            const course = data['course'];
            if (course) {
                newLecture.course = course;
            }
            this.lecture.set(newLecture);
            if (lecture) {
                this.isTutorialLecture.set(lecture.isTutorialLecture ?? false);
            }
        });

        const paramMap = this.activatedRoute.parent!.snapshot.paramMap;
        this.courseId.set(Number(paramMap.get('courseId')));

        this.isEditMode.set(!this.router.url.endsWith('/new'));
        this.lectureOnInit = deepClone(this.lecture());

        this.existingLectures.set((this.navigationState?.['existingLectures'] ?? []) as Lecture[]);
        const detailsSavedAt = this.navigationState?.[DETAILS_SAVED_AT_STATE_KEY];
        if (typeof detailsSavedAt === 'string') {
            this.lastSavedAt.set(dayjs(detailsSavedAt));
            this.openedAfterCreation = true;
            // The router restores the state of a history entry on reload and on back or forward, where the time would be stale.
            const { [DETAILS_SAVED_AT_STATE_KEY]: _savedAt, ...remainingState } = this.historyState();
            this.location.replaceState(this.location.path(), '', remainingState);
        }
        // Read from the history entry rather than the navigation, so the confirmation stays after a reload until it is dismissed.
        this.pdfUploadConfirmation.set(this.historyState()[PDF_UPLOAD_CONFIRMATION_STATE_KEY] as PdfUploadConfirmation | undefined);
    }

    /**
     * Hides the confirmation and takes it out of the browser history entry, so reloading the page does not confirm the upload a second time.
     */
    dismissPdfUploadConfirmation(): void {
        this.pdfUploadConfirmation.set(undefined);
        const { [PDF_UPLOAD_CONFIRMATION_STATE_KEY]: _dismissed, ...remainingState } = this.historyState();
        this.location.replaceState(this.location.path(), '', remainingState);
        // The focused dismiss button is gone, so the keyboard focus continues at the details.
        document.getElementById('artemisApp.lecture.sections.title')?.focus({ preventScroll: true });
    }

    private historyState(): Record<string, unknown> {
        return (this.location.getState() ?? {}) as Record<string, unknown>;
    }

    /**
     * Scrolls a section of the editor into view.
     * @param sectionId the id of the section's heading, which is the translation key of its title
     */
    scrollToSection(sectionId: string): void {
        const heading = document.getElementById(sectionId);
        heading?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        // Keyboard and screen reader users continue in the section they jumped to.
        heading?.focus({ preventScroll: true });
    }

    updateFormStatusBar() {
        const updatedFormStatusSections: FormSectionStatus[] = [];

        updatedFormStatusSections.push(
            {
                title: 'artemisApp.lecture.sections.title',
                valid: this.titleSection()?.isValid() ?? false,
            },
            {
                title: 'artemisApp.lecture.sections.period',
                valid: this.isPeriodValid(),
            },
        );

        if (this.isEditMode()) {
            updatedFormStatusSections.push({
                title: 'artemisApp.lecture.sections.units',
                valid: Boolean(this.unitSection()?.isUnitConfigurationValid()),
            });
        }

        this.formStatusSections.set(updatedFormStatusSections);
    }

    isChangeMadeToTitleSection() {
        return (
            this.lecture().title !== this.lectureOnInit.title ||
            this.lecture().channelName !== this.lectureOnInit.channelName ||
            (this.lecture().description ?? '') !== (this.lectureOnInit.description ?? '') ||
            (this.lecture().isTutorialLecture ?? false) !== (this.lectureOnInit.isTutorialLecture ?? false)
        );
    }

    isChangeMadeToPeriodSection() {
        const { startDate, endDate } = this.lecture();
        const { startDate: startDateOnInit, endDate: endDateOnInit } = this.lectureOnInit;

        // A missing and an invalid date both mean "no date". Comparing instants avoids dayjs(undefined), which is "now" and differs between two calls.
        const instant = (date: Dayjs | undefined) => (date && dayjs(date).isValid() ? dayjs(date).valueOf() : undefined);

        return instant(startDate) !== instant(startDateOnInit) || instant(endDate) !== instant(endDateOnInit);
    }

    protected updateIsChangesMadeToTitleOrPeriodSection() {
        const changedSections: string[] = [];
        if (this.isChangeMadeToTitleSection()) {
            changedSections.push('artemisApp.lecture.sections.title');
        }
        if (this.isChangeMadeToPeriodSection()) {
            changedSections.push('artemisApp.lecture.sections.period');
        }
        this.changedSections.set(changedSections);
        this.isChangeMadeToTitleOrPeriodSection.set(changedSections.length > 0);
    }

    /**
     * Asks the browser to confirm a reload or closing the tab while something would be lost: unsaved lecture details, content that could
     * not be saved, or content that is still being saved. The unsaved changes guard covers navigation within Artemis.
     * @param event the beforeunload event
     */
    onBeforeUnload(event: BeforeUnloadEvent): void {
        if (!this.shouldDisplayDismissWarning) {
            return;
        }
        // Text typed just before, which the markdown editor still holds back, counts as content that is being saved.
        this.unitSection()?.flushBufferedEdits();
        if (this.hasUnsavedChanges() || !!this.unitSection()?.isSavingContent()) {
            event.preventDefault();
        }
    }

    /**
     * Decides whether the editor can be left now. Text the content still holds back is sent first. Then the user is asked when leaving would
     * discard changes, or while content is still being saved, which would be lost if its save fails after leaving.
     * Emits once: true when the page may be left, false when the user keeps editing.
     */
    confirmLeave(): Observable<boolean> {
        this.unitSection()?.flushBufferedEdits();
        if (this.hasUnsavedChanges()) {
            return this.confirmDiscardChanges();
        }
        if (this.unitSection()?.isSavingContent()) {
            return this.confirmLeaveWhileSaving();
        }
        return of(true);
    }

    /**
     * Asks whether to discard the unsaved changes of the lecture details and content, naming the sections that hold them.
     * Emits once: true when the user discards the changes, false when they keep editing or close the dialog.
     */
    private confirmDiscardChanges(): Observable<boolean> {
        const sections = this.unsavedSections()
            .map((section) => this.translateService.instant(section))
            .join(', ');
        let message = this.translateService.instant('artemisApp.lecture.dismissChangesModal.message', { sections });
        if (this.unitSection()?.isSavingContent()) {
            message += ' ' + this.translateService.instant('artemisApp.lecture.dismissChangesModal.stillSaving');
        }
        return this.decide({
            header: this.translateService.instant('artemisApp.lecture.dismissChangesModal.title'),
            message,
            acceptLabel: this.translateService.instant('entity.action.discardChanges'),
            acceptSeverity: 'danger',
        });
    }

    /** Asks whether to leave while content is still being saved. Keeping the user on the page is the default, also when they close the dialog. */
    private confirmLeaveWhileSaving(): Observable<boolean> {
        return this.decide({
            header: this.translateService.instant('artemisApp.lecture.leaveWhileSavingModal.title'),
            message: this.translateService.instant('artemisApp.lecture.leaveWhileSavingModal.message'),
            acceptLabel: this.translateService.instant('artemisApp.lecture.leaveWhileSavingModal.leave'),
            acceptSeverity: 'secondary',
            rejectSeverity: 'primary',
        });
    }

    /**
     * Shows a decision about leaving. Emits once: true when the user leaves, false when they keep editing, press Escape or close the dialog.
     * @param request the texts and styles of the dialog; Keep editing is always the button that stays
     */
    private decide(request: Pick<TumAetUiConfirmationRequest, 'header' | 'message' | 'acceptLabel' | 'acceptSeverity' | 'rejectSeverity'>): Observable<boolean> {
        return new Observable<boolean>((subscriber) => {
            const decide = (leave: boolean) => {
                subscriber.next(leave);
                subscriber.complete();
            };
            const ownRequest: TumAetUiConfirmationRequest = {
                header: request.header,
                message: request.message,
                icon: faTriangleExclamation,
                acceptLabel: request.acceptLabel,
                acceptSeverity: request.acceptSeverity,
                rejectLabel: this.translateService.instant('artemisApp.lecture.dismissChangesModal.keepEditing'),
                rejectSeverity: request.rejectSeverity,
                accept: () => decide(true),
                reject: () => decide(false),
            };
            this.confirmationService.confirm(ownRequest);
            // A navigation that is superseded before the user decides unsubscribes; its dialog closes, but not a newer one that replaced it.
            return () => {
                if (this.confirmationService.request(undefined) === ownRequest) {
                    this.confirmationService.close(undefined);
                }
            };
        });
    }

    /**
     * Leaves the editor, equivalent to pressing the back button of the browser: back to where the user came from, else to the lecture's detail page
     * when it exists, else to the lecture list. Unsaved changes and content that is still being saved are not dropped silently; the user is asked first.
     */
    previousState() {
        // Ask before navigating: a back navigation that the unsaved changes guard cancels would leave the editor in the history entry it left.
        this.leaveAttempt?.unsubscribe();
        this.leaveAttempt = this.confirmLeave().subscribe((leave) => {
            if (leave) {
                this.shouldDisplayDismissWarning = false;
                this.leave();
            }
        });
    }

    private leave(): void {
        const lectureListUrl = ['course-management', this.lecture().course!.id!.toString(), 'lectures'];
        if (this.openedAfterCreation) {
            // Going back could leave Artemis: the creation page this editor replaced may have opened the browser tab.
            void this.router.navigate(lectureListUrl);
            return;
        }
        this.navigationUtilService.navigateBackWithOptional(lectureListUrl, this.lecture().id?.toString());
    }

    /**
     * Enter in a single-line field of the details or the period saves the details, as the form of the page did.
     * @param event the keydown event
     */
    onEnterInDetails(event: Event): void {
        const inDetails = [this.detailsSection(), this.periodSection()].some((section) => section?.nativeElement.contains(event.target as Node));
        if (!inDetails || !isSingleLineField(event.target) || event.defaultPrevented) {
            return;
        }
        if (!this.isChangeMadeToTitleOrPeriodSection() || this.saveBlockedReason() || this.isSaving()) {
            return;
        }
        event.preventDefault();
        this.save();
    }

    /**
     * Save the changes on a lecture
     * This function is called by pressing save after creating or editing a lecture
     */
    save() {
        // A new lecture leaves this page for its editor once it is saved, which must not ask. An existing lecture stays on this page, so leaving it while
        // its details are saved still asks about what the save does not cover, such as content that could not be saved.
        if (!this.isEditMode()) {
            this.shouldDisplayDismissWarning = false;
        }
        this.isSaving.set(true);
        this.sentLecture = deepClone(this.lecture());
        if (this.lecture().id !== undefined) {
            this.subscribeToSaveResponse(this.lectureService.update(this.lecture()));
        } else {
            // Newly created lectures must have a channel name, which cannot be undefined
            this.subscribeToSaveResponse(this.lectureService.create(this.lecture()));
        }
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
        this.isSaving.set(false);

        if (!lecture.course?.id) {
            captureException('Lecture has no course id: ' + lecture.id);
            return;
        }

        if (this.isEditMode()) {
            // Saving keeps the editor open: the content below is edited on this page too, and Close leaves it. The response describes the
            // lecture only in part (its course lacks the communication settings, for example), so the page keeps its own lecture and takes
            // what it sent as the saved state; whatever was typed while the request ran stays unsaved.
            this.lectureOnInit = this.sentLecture ?? deepClone(this.lecture());
            this.lastSavedAt.set(dayjs());
            this.updateIsChangesMadeToTitleOrPeriodSection();
        } else {
            // A saved lecture can hold content, which is added in its editor. The router creates that editor anew for the edit route, so the
            // creation page is left without asking. Replacing it in the history lets the browser's back button skip the now empty form.
            void this.router
                .navigate(['course-management', lecture.course.id, 'lectures', lecture.id, 'edit'], {
                    replaceUrl: true,
                    state: { [DETAILS_SAVED_AT_STATE_KEY]: dayjs().toISOString() },
                })
                .then(
                    (navigated) => {
                        if (!navigated) {
                            this.keepCreatedLecture(lecture.id!);
                        }
                    },
                    () => this.keepCreatedLecture(lecture.id!),
                );
        }

        this.calendarService.reloadEvents();
    }

    /**
     * Stays on the creation page when its editor could not be opened. The lecture exists now, so a second Save updates it instead of creating another one.
     * @param lectureId the id of the created lecture
     */
    private keepCreatedLecture(lectureId: number): void {
        const createdLecture = deepClone(this.lecture());
        createdLecture.id = lectureId;
        this.lecture.set(createdLecture);
        this.lectureOnInit = deepClone(createdLecture);
        this.updateIsChangesMadeToTitleOrPeriodSection();
        this.shouldDisplayDismissWarning = true;
    }

    /**
     * Action on unsuccessful lecture creation or edit
     * @param errorRes the errorRes handed to the alert service
     */
    protected onSaveError(errorRes: HttpErrorResponse) {
        this.isSaving.set(false);
        // The changes were not saved, so leaving the page has to ask again.
        this.shouldDisplayDismissWarning = true;

        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
    }

    onLectureChange(updatedLecture: Lecture): void {
        this.lecture.set(updatedLecture);
        this.updateIsChangesMadeToTitleOrPeriodSection();
    }

    private computeSaveBlockedReason(): string | undefined {
        if (!(this.titleSection()?.isValid() ?? false)) {
            return 'artemisApp.lecture.editFooter.detailsIncomplete';
        }
        if (!this.isPeriodValid()) {
            return 'artemisApp.lecture.editFooter.periodInvalid';
        }
        return undefined;
    }

    private computePdfUploadConfirmationText(): PdfUploadConfirmationText | undefined {
        const confirmation = this.pdfUploadConfirmation();
        if (!confirmation) {
            return undefined;
        }
        const single = confirmation.fileNames.length === 1;
        const kind = confirmation.lectureCreated ? 'created' : 'added';
        return {
            title: `artemisApp.lecture.pdfUpload.${kind}Title`,
            body: `artemisApp.lecture.pdfUpload.${kind}${single ? 'Single' : 'Multiple'}`,
            params: { title: this.lecture().title ?? '', fileName: confirmation.fileNames[0] ?? '', count: confirmation.fileNames.length },
            release: confirmation.releaseDate ? `artemisApp.lecture.pdfUpload.${single ? 'releaseSingle' : 'releaseMultiple'}` : undefined,
            releaseDate: confirmation.releaseDate ? dayjs(confirmation.releaseDate) : undefined,
        };
    }

    private computeCreateLectureOptions(): CreateLectureOption[] {
        this.currentLocale();
        return [
            { label: this.translateService.instant('artemisApp.lecture.creationMode.singleLectureLabel'), mode: LectureCreationMode.SINGLE },
            { label: this.translateService.instant('artemisApp.lecture.creationMode.lectureSeriesLabel'), mode: LectureCreationMode.SERIES },
        ];
    }
}
