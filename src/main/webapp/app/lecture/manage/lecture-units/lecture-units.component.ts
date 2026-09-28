import { Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, inject, input, linkedSignal, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faFileArrowUp, faScissors, faSpinner, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiFormFieldComponent, TumAetUiMessageComponent, TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { AttachmentUpdateIntent, AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import {
    AttachmentVideoUnitFormComponent,
    AttachmentVideoUnitFormData,
    ConfirmedContent,
} from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { onError } from 'app/foundation/util/global.utils';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { objectToJsonBlob } from 'app/foundation/util/blob-util';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/text-unit.service';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import dayjs from 'dayjs/esm';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { concatMap, filter, map } from 'rxjs/operators';
import { Observable, from } from 'rxjs';
import { PdfDropZoneComponent } from '../pdf-drop-zone/pdf-drop-zone.component';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { UnitFormChange } from 'app/lecture/manage/lecture-units/unit-form-change.model';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { isSingleLineField } from 'app/lecture/manage/single-line-field.util';

/** How long typing has to pause before an item that is edited in place is saved. */
export const AUTOSAVE_DELAY_MS = 1500;

/** The state of saving the item that is edited in place, which its form shows. */
export type UnitAutosaveState =
    | { kind: 'idle' }
    /** A change waits for the pause after typing, or its request runs. */
    | { kind: 'saving' }
    | { kind: 'saved'; at: dayjs.Dayjs }
    /** The form cannot be saved as it is; the change is kept until the form is corrected. */
    | { kind: 'invalid' }
    /** The server did not take the change; it is kept for a retry. */
    | { kind: 'failed'; reason?: string };

/** A file or video link the user confirmed, which a later change of the details never replaces and the form takes over once it is saved. */
interface ConfirmedUnitSave {
    confirmed: ConfirmedContent;
    /** The form when the user confirmed; its file or video link is sent, with the details that are newest when the request starts. */
    data: AttachmentVideoUnitFormData;
}

interface PendingUnitSave {
    /** The details of the item the request sends, compared with what was sent last, so an unchanged state sends nothing. */
    key: string;
    request: () => Observable<LectureUnit>;
    confirmed?: ConfirmedUnitSave;
}

/** A confirmed save that failed; it is sent again only on Retry or Save, not with every later change. */
interface HeldUnitSave {
    save: ConfirmedUnitSave;
    reason?: string;
}

/** The save requests of an item edited in place show their failure in the item, not in an alert. */
const AUTOSAVE_REQUEST_CONTEXT = () => new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);

@Component({
    selector: 'jhi-lecture-update-units',
    templateUrl: './lecture-units.component.html',
    styleUrl: './lecture-units.component.scss',
    // Enter is handled on the host: the form of the item that is edited in place is no control itself, so it takes no key handlers.
    host: { '(keydown.enter)': 'onEditorEnter($event)' },
    imports: [
        TranslateDirective,
        LectureUnitManagementComponent,
        UnitCreationCardComponent,
        TextUnitFormComponent,
        OnlineUnitFormComponent,
        AttachmentVideoUnitFormComponent,
        CreateExerciseUnitComponent,
        PdfDropZoneComponent,
        NgTemplateOutlet,
        FormsModule,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiFormFieldComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
    ],
})
export class LectureUpdateUnitsComponent implements OnInit {
    protected activatedRoute = inject(ActivatedRoute);
    protected alertService = inject(AlertService);
    protected textUnitService = inject(TextUnitService);
    protected onlineUnitService = inject(OnlineUnitService);
    protected attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);
    private readonly injector = inject(Injector);

    protected readonly faScissors = faScissors;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheck = faCheck;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faFileArrowUp = faFileArrowUp;

    lecture = input.required<Lecture>();

    unitManagementComponent = viewChild(LectureUnitManagementComponent);
    editFormContainer = viewChild<ElementRef<HTMLElement>>('editFormContainer');
    private readonly inPlaceEditor = viewChild<ElementRef<HTMLElement>>('inPlaceEditor');
    private readonly otherPdfInput = viewChild<ElementRef<HTMLInputElement>>('otherPdfInput');

    textUnitForm = viewChild(TextUnitFormComponent);
    onlineUnitForm = viewChild(OnlineUnitFormComponent);
    attachmentVideoUnitForm = viewChild(AttachmentVideoUnitFormComponent);
    isUnitConfigurationValid = computed(() => {
        return (
            (this.textUnitForm()?.isFormValid() || !this.isTextUnitFormOpen()) &&
            (this.onlineUnitForm()?.isFormValid() || !this.isOnlineUnitFormOpen()) &&
            (this.attachmentVideoUnitForm()?.isFormValid() || !this.isAttachmentVideoUnitFormOpen())
        );
    });

    /** Whether the open form belongs to an existing item, which is edited in place, rather than to a new one. */
    isEditingLectureUnit = signal(false);
    isTextUnitFormOpen = signal<boolean>(false);
    isExerciseUnitFormOpen = signal<boolean>(false);
    isOnlineUnitFormOpen = signal<boolean>(false);
    isAttachmentVideoUnitFormOpen = signal<boolean>(false);
    isUploadingPdfs = signal<boolean>(false);

    /** The content of the lecture as the list shows it. */
    readonly lectureUnits = signal<LectureUnit[]>([]);
    /** The content items that hold a PDF, which Artemis can split into one item per section. */
    readonly pdfUnits = computed(() =>
        this.lectureUnits().filter(
            (unit): unit is AttachmentVideoUnit =>
                unit.type === LectureUnitType.ATTACHMENT_VIDEO && !!(unit as AttachmentVideoUnit).attachment?.link?.toLowerCase().endsWith('.pdf'),
        ),
    );
    /** The PDF to split: the only one when there is one, the chosen one while it still exists. */
    readonly selectedSplitUnitId = linkedSignal<AttachmentVideoUnit[], number | undefined>({
        source: this.pdfUnits,
        computation: (units, previous) => {
            if (previous?.value !== undefined && units.some((unit) => unit.id === previous.value)) {
                return previous.value;
            }
            return units.length === 1 ? units[0].id : undefined;
        },
    });
    readonly selectedSplitUnit = computed(() => this.pdfUnits().find((unit) => unit.id === this.selectedSplitUnitId()));
    readonly isPreparingSplit = signal(false);

    textUnitFormData = signal<TextUnitFormData | undefined>(undefined);
    onlineUnitFormData = signal<OnlineUnitFormData | undefined>(undefined);
    attachmentVideoUnitFormData = signal<AttachmentVideoUnitFormData | undefined>(undefined);

    /** The item that is edited in place; its form sits right below it in the list and saves itself. */
    readonly editingUnit = signal<LectureUnit | undefined>(undefined);
    readonly editingUnitId = computed(() => this.editingUnit()?.id);
    /** The newest change of the details, which replaces an older one that was not sent yet. */
    private readonly pendingDetails = signal<PendingUnitSave | undefined>(undefined);
    /** Confirmed files and video links in the order they were confirmed; they are sent before the details. */
    private readonly pendingConfirmed = signal<ConfirmedUnitSave[]>([]);
    private readonly heldConfirmed = signal<HeldUnitSave[]>([]);
    /** The save that runs and its item; a save of an item that was deleted meanwhile does not count for the item opened next. */
    private readonly inFlight = signal<{ unitId?: number; confirmed?: ConfirmedContent } | undefined>(undefined);
    private readonly isSaveInFlight = computed(() => this.inFlight()?.unitId !== undefined && this.inFlight()?.unitId === this.editingUnitId());
    /** The confirmed file or video link whose request runs, which the form can no longer take back. */
    readonly savingConfirmed = computed(() => (this.isSaveInFlight() ? this.inFlight()?.confirmed : undefined));
    /** Whether the form reported last that it cannot be saved as it is. */
    private readonly isFormInvalid = signal(false);
    /** Set when Save, opening another item or adding one waited for a new file or video link that the user did not confirm or discard. */
    private readonly isCloseBlocked = signal(false);
    readonly showsUnconfirmedContentHint = computed(() => this.isCloseBlocked() && !!this.attachmentVideoUnitForm()?.hasUnconfirmedContent());
    /** A failed save of the details, which the next change or Retry sends again. */
    private readonly saveFailure = signal<{ reason?: string } | undefined>(undefined);
    private readonly lastSavedAt = signal<dayjs.Dayjs | undefined>(undefined);
    readonly autosaveState = computed<UnitAutosaveState>(() => {
        const failure = this.saveFailure() ?? this.heldConfirmed()[0];
        if (failure) {
            return { kind: 'failed', reason: failure.reason };
        }
        if (this.isSaving()) {
            return { kind: 'saving' };
        }
        if (this.isFormInvalid()) {
            return { kind: 'invalid' };
        }
        const savedAt = this.lastSavedAt();
        return savedAt ? { kind: 'saved', at: savedAt } : { kind: 'idle' };
    });
    /** Whether a save of the item runs or waits. */
    readonly isSaving = computed(() => this.isSaveInFlight() || !!this.pendingDetails() || this.pendingConfirmed().length > 0);
    /**
     * Whether leaving the page now would lose a change of the item that is edited in place: one that failed, a form that cannot be saved,
     * or a new file or video link that was not confirmed. A change that can be saved is saved on leaving.
     */
    readonly hasUnsavedContent = computed(
        () =>
            !!this.saveFailure() ||
            this.heldConfirmed().length > 0 ||
            this.isFormInvalid() ||
            (this.isEditingLectureUnit() && !!this.attachmentVideoUnitForm()?.hasUnconfirmedContent()),
    );
    /** Set once the page is left; a save that fails afterwards is reported in an alert, because the item is gone. */
    private isDestroyed = false;
    /** The details the server has. */
    private lastSavedKey?: string;
    /** The details sent last, which the server has or is about to have. */
    private lastRequestedKey?: string;
    /** The newest details of the file item that can be saved, which a confirmed file or video link is sent with. */
    private latestAttachmentDetails?: AttachmentVideoUnitFormData;
    private autosaveTimer?: ReturnType<typeof setTimeout>;
    /** Runs once the item is saved, such as closing its form after Save. */
    private afterAutosave?: () => void;

    constructor() {
        // A change that waits for the pause after typing is sent when the user leaves the page; the save completes on its own.
        this.destroyRef.onDestroy(() => {
            this.isDestroyed = true;
            if (!this.saveFailure() && this.isSaving()) {
                this.flushAutosave();
            }
            this.clearAutosaveTimer();
        });
    }

    ngOnInit() {
        this.activatedRoute.queryParams.subscribe((params) => {
            // Checks if the exercise unit form should be opened initially, i.e. coming back from the exercise creation
            if (params.shouldOpenCreateExercise) {
                this.onCreateLectureUnit(LectureUnitType.EXERCISE);
            }
        });
    }

    /**
     * Takes over the content as the list shows it. When the item that is edited in place was deleted, its form closes.
     * @param lectureUnits the content of the lecture
     */
    onLectureUnitsChange(lectureUnits: LectureUnit[]): void {
        const editingUnitId = this.editingUnitId();
        // An item that was just added may not be in the list yet; only an item that left the list was deleted.
        const wasListed = editingUnitId !== undefined && this.lectureUnits().some((unit) => unit.id === editingUnitId);
        this.lectureUnits.set(lectureUnits);
        if (wasListed && !lectureUnits.some((unit) => unit.id === editingUnitId)) {
            this.closeEditor();
        }
    }

    /**
     * Opens the form of a new item of the given kind. An item that is edited in place is saved and closed first.
     * @param type the kind of item to create
     */
    onCreateLectureUnit(type: LectureUnitType) {
        if (this.editingUnit()) {
            this.saveAndCloseEditor(() => this.openCreationForm(type));
            return;
        }
        this.openCreationForm(type);
    }

    private openCreationForm(type: LectureUnitType) {
        this.onCloseLectureUnitForms();
        switch (type) {
            case LectureUnitType.TEXT:
                this.isTextUnitFormOpen.set(true);
                break;
            case LectureUnitType.EXERCISE:
                this.isExerciseUnitFormOpen.set(true);
                break;
            case LectureUnitType.ONLINE:
                this.isOnlineUnitFormOpen.set(true);
                break;
            case LectureUnitType.ATTACHMENT_VIDEO:
                this.isAttachmentVideoUnitFormOpen.set(true);
                break;
        }
    }

    isAnyUnitFormOpen = computed(() => {
        return this.isTextUnitFormOpen() || this.isOnlineUnitFormOpen() || this.isAttachmentVideoUnitFormOpen() || this.isExerciseUnitFormOpen();
    });

    onCloseLectureUnitForms() {
        this.isTextUnitFormOpen.set(false);
        this.isOnlineUnitFormOpen.set(false);
        this.isAttachmentVideoUnitFormOpen.set(false);
        this.isExerciseUnitFormOpen.set(false);
        this.isEditingLectureUnit.set(false);
    }

    createTextUnit(formData: TextUnitFormData) {
        if (!formData?.name) {
            return;
        }
        const textUnit = new TextUnit();
        textUnit.name = formData.name;
        textUnit.releaseDate = formData.releaseDate;
        textUnit.content = formData.content;
        textUnit.competencyLinks = formData.competencyLinks;

        this.textUnitService.create(textUnit, this.lecture().id!).subscribe({
            next: () => this.onUnitCreated(),
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createOnlineUnit(formData: OnlineUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }
        const onlineUnit = new OnlineUnit();
        onlineUnit.name = formData.name || undefined;
        onlineUnit.releaseDate = formData.releaseDate || undefined;
        onlineUnit.description = formData.description || undefined;
        onlineUnit.source = formData.source || undefined;
        onlineUnit.competencyLinks = formData.competencyLinks || undefined;

        this.onlineUnitService.create(onlineUnit, this.lecture().id!).subscribe({
            next: () => this.onUnitCreated(),
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData): void {
        const { description, name, releaseDate, videoSource, competencyLinks } = attachmentVideoUnitFormData.formProperties;
        const { file, fileName } = attachmentVideoUnitFormData.fileProperties;
        if (!name || (!fileName && !videoSource)) {
            return;
        }

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.name = name;
        attachmentVideoUnit.releaseDate = releaseDate || undefined;
        attachmentVideoUnit.description = description || undefined;
        attachmentVideoUnit.videoSource = videoSource || undefined;
        attachmentVideoUnit.competencyLinks = competencyLinks;

        const formData = new FormData();
        if (file && fileName && file.size > 0) {
            formData.append('file', file, fileName);
            formData.append('attachment', objectToJsonBlob(this.attachmentForUpload(new Attachment(), name, releaseDate)));
        }
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        this.attachmentVideoUnitService.create(formData, this.lecture().id!).subscribe({
            next: () => this.onUnitCreated(),
            error: (res: HttpErrorResponse | Error) => {
                if (res instanceof Error) {
                    this.alertService.error(res.message);
                    return;
                }
                if (res.error?.params === 'file' && res?.error?.title) {
                    this.alertService.error(res.error.title);
                } else {
                    onError(this.alertService, res);
                }
            },
        });
    }

    private onUnitCreated(): void {
        this.onCloseLectureUnitForms();
        this.unitManagementComponent()?.loadData();
    }

    /**
     * Called when all selected exercises were linked from the component
     */
    onExerciseUnitCreated() {
        this.onUnitCreated();
    }

    /**
     * Scrolls to the edit form container
     */
    private scrollToEditForm(): void {
        const container = this.editFormContainer();
        if (container?.nativeElement) {
            container.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    }

    /**
     * Opens an item in place, right below it in the list. An item that is edited already is saved and closed first, so only one is open.
     * @param lectureUnit the item to edit
     */
    startEditLectureUnit(lectureUnit: LectureUnit) {
        const editingUnit = this.editingUnit();
        if (editingUnit?.id !== undefined && editingUnit.id === lectureUnit.id) {
            return;
        }
        if (editingUnit) {
            this.saveAndCloseEditor(() => this.openEditor(lectureUnit));
            return;
        }
        this.openEditor(lectureUnit);
    }

    private openEditor(lectureUnit: LectureUnit) {
        // Work on a copy, so the list shows the item as it was saved until a save succeeds.
        const unit = deepClone(lectureUnit);
        unit.lecture = new Lecture();
        unit.lecture.id = this.lecture().id;
        unit.lecture.course = this.lecture().course;

        this.onCloseLectureUnitForms();
        this.resetAutosave();
        this.editingUnit.set(unit);
        this.isEditingLectureUnit.set(true);
        this.isTextUnitFormOpen.set(unit.type === LectureUnitType.TEXT);
        this.isExerciseUnitFormOpen.set(unit.type === LectureUnitType.EXERCISE);
        this.isOnlineUnitFormOpen.set(unit.type === LectureUnitType.ONLINE);
        this.isAttachmentVideoUnitFormOpen.set(unit.type === LectureUnitType.ATTACHMENT_VIDEO);

        switch (unit.type) {
            case LectureUnitType.TEXT: {
                const textUnit = unit as TextUnit;
                this.textUnitFormData.set({ name: textUnit.name, releaseDate: textUnit.releaseDate, content: textUnit.content, competencyLinks: textUnit.competencyLinks });
                this.lastSavedKey = this.textUnitSaveKey(this.textUnitFormData()!);
                this.lastRequestedKey = this.lastSavedKey;
                break;
            }
            case LectureUnitType.ONLINE: {
                const onlineUnit = unit as OnlineUnit;
                this.onlineUnitFormData.set({
                    name: onlineUnit.name,
                    description: onlineUnit.description,
                    releaseDate: onlineUnit.releaseDate,
                    source: onlineUnit.source,
                    competencyLinks: onlineUnit.competencyLinks,
                });
                this.lastSavedKey = this.onlineUnitSaveKey(this.onlineUnitFormData()!);
                this.lastRequestedKey = this.lastSavedKey;
                break;
            }
            case LectureUnitType.ATTACHMENT_VIDEO: {
                this.attachmentVideoUnitFormData.set(this.toAttachmentVideoUnitFormData(unit));
                this.latestAttachmentDetails = this.attachmentVideoUnitFormData();
                this.lastSavedKey = this.attachmentVideoUnitSaveKey(this.attachmentVideoUnitFormData()!);
                this.lastRequestedKey = this.lastSavedKey;
                break;
            }
        }

        // Scroll to the edit form after a brief delay to allow the form to render, and continue typing in its first field.
        setTimeout(() => {
            this.scrollToEditForm();
            this.editFormContainer()?.nativeElement.querySelector<HTMLElement>('input:not([type="file"]), textarea')?.focus({ preventScroll: true });
        }, 100);
    }

    private toAttachmentVideoUnitFormData(unit: AttachmentVideoUnit): AttachmentVideoUnitFormData {
        return {
            formProperties: {
                name: unit.name,
                description: unit.description,
                releaseDate: unit.releaseDate,
                version: unit.attachment?.version,
                videoSource: unit.videoSource,
                competencyLinks: unit.competencyLinks,
            },
            fileProperties: {
                fileName: unit.attachment?.link,
            },
        };
    }

    /**
     * Saves the item that is edited in place at once and closes its form, as its Save button does. A form that cannot be saved
     * stays open with its message, so nothing typed is lost.
     * @param then runs after the form closed, such as opening the next item
     */
    saveAndCloseEditor(then?: () => void): void {
        const unitId = this.editingUnitId();
        const close = () => {
            this.closeEditor();
            if (then) {
                then();
            } else {
                this.focusEditButtonOf(unitId);
            }
        };
        // The markdown editor reports typing only after a short pause, which closing the form would cut off.
        this.textUnitForm()?.flushPendingEdits();
        if (this.isFormInvalid()) {
            return;
        }
        // A new file or video link is saved only on confirmation, so closing would drop it without asking; the rest is saved already.
        if (this.attachmentVideoUnitForm()?.hasUnconfirmedContent()) {
            this.isCloseBlocked.set(true);
            this.flushAutosave();
            return;
        }
        // Save is a retry of a confirmed file or link that failed.
        this.requeueHeldSaves();
        if (this.isSaving()) {
            this.afterAutosave = close;
            this.flushAutosave();
            return;
        }
        close();
    }

    /** Closes the item without the change that cannot be saved or failed to save; everything saved before stays. */
    discardUnsavedChange(): void {
        const unitId = this.editingUnitId();
        this.closeEditor();
        this.focusEditButtonOf(unitId);
    }

    /** The form and the Save button of the item are gone once it closes, so the keyboard focus continues at its Edit button. */
    private focusEditButtonOf(unitId: number | undefined): void {
        if (unitId !== undefined) {
            afterNextRender(() => this.unitManagementComponent()?.focusEditButton(unitId), { injector: this.injector });
        }
    }

    private closeEditor(): void {
        this.resetAutosave();
        this.editingUnit.set(undefined);
        this.onCloseLectureUnitForms();
    }

    /** Forgets everything about saving the previous item; a request of it that still runs only updates the list when it completes. */
    private resetAutosave(): void {
        this.clearAutosaveTimer();
        this.pendingDetails.set(undefined);
        this.pendingConfirmed.set([]);
        this.heldConfirmed.set([]);
        this.isFormInvalid.set(false);
        this.isCloseBlocked.set(false);
        this.saveFailure.set(undefined);
        this.lastSavedAt.set(undefined);
        this.afterAutosave = undefined;
        this.latestAttachmentDetails = undefined;
    }

    onTextUnitChanged(change: UnitFormChange<TextUnitFormData>): void {
        this.queueDetails(change, { key: this.textUnitSaveKey(change.data), request: () => this.saveTextUnit(change.data) });
    }

    onOnlineUnitChanged(change: UnitFormChange<OnlineUnitFormData>): void {
        this.queueDetails(change, { key: this.onlineUnitSaveKey(change.data), request: () => this.saveOnlineUnit(change.data) });
    }

    onAttachmentVideoUnitChanged(change: UnitFormChange<AttachmentVideoUnitFormData>): void {
        if (change.valid) {
            this.latestAttachmentDetails = change.data;
        }
        this.queueDetails(change, {
            key: this.attachmentVideoUnitSaveKey(change.data),
            request: () => this.saveAttachmentVideoUnit(change.data, { withFile: false, withVideoSource: false }),
        });
    }

    /** Saves the file the user confirmed as the next version of the item, and notifies students if they asked for it. */
    onAttachmentFileUploadRequested(data: AttachmentVideoUnitFormData): void {
        this.queueConfirmed({ confirmed: 'file', data });
    }

    /** Saves the video link the user confirmed. */
    onVideoSourceSaveRequested(data: AttachmentVideoUnitFormData): void {
        this.queueConfirmed({ confirmed: 'videoSource', data });
    }

    /** Takes back a confirmed file or video link that waits or failed, because the user kept the current one or chose another. */
    onConfirmedContentWithdrawn(content: ConfirmedContent): void {
        this.pendingConfirmed.update((waiting) => waiting.filter((save) => save.confirmed !== content));
        this.heldConfirmed.update((held) => held.filter((failed) => failed.save.confirmed !== content));
    }

    /** Saves a waiting change as soon as the user leaves a field of the form, instead of after the pause. */
    onEditorFocusOut(): void {
        this.textUnitForm()?.flushPendingEdits();
        this.flushAutosave();
    }

    /**
     * Enter in a single-line field saves a waiting change at once. The form of an item edited in place has no submit button, so the
     * browser does not submit it on Enter.
     * @param event the keydown event
     */
    onEditorEnter(event: Event): void {
        const inEditor = !!this.inPlaceEditor()?.nativeElement.contains(event.target as Node);
        if (inEditor && isSingleLineField(event.target) && !event.defaultPrevented) {
            this.onEditorFocusOut();
        }
    }

    /** Sends the change again after a failed save, including a confirmed file or link. */
    retryAutosave(): void {
        this.requeueHeldSaves();
        this.flushAutosave();
    }

    private requeueHeldSaves(): void {
        const held = this.heldConfirmed();
        if (held.length) {
            this.heldConfirmed.set([]);
            this.pendingConfirmed.update((waiting) => [...held.map((failed) => failed.save), ...waiting]);
        }
    }

    private queueDetails(change: UnitFormChange<unknown>, pending: PendingUnitSave): void {
        if (!this.editingUnit()) {
            return;
        }
        this.clearAutosaveTimer();
        this.isFormInvalid.set(!change.valid);
        // A change back to the details sent last needs no request. A confirmed save that waits sends the newest details anyway.
        if (!change.valid || pending.key === this.lastRequestedKey) {
            // An invalid form waits until it is corrected; a change back to what is sent needs no request, even while that request runs.
            this.pendingDetails.set(undefined);
            // No change of the details is left to retry.
            this.saveFailure.set(undefined);
            return;
        }
        this.pendingDetails.set(pending);
        if (change.immediate) {
            this.flushAutosave();
        } else {
            this.autosaveTimer = setTimeout(() => this.flushAutosave(), AUTOSAVE_DELAY_MS);
        }
    }

    private queueConfirmed(pending: ConfirmedUnitSave): void {
        if (!this.editingUnit()) {
            return;
        }
        this.latestAttachmentDetails = pending.data;
        // A newer confirmation of the same kind replaces one that waits or failed; a change of the details never does.
        this.heldConfirmed.update((held) => held.filter((failed) => failed.save.confirmed !== pending.confirmed));
        this.pendingConfirmed.update((waiting) => [...waiting.filter((save) => save.confirmed !== pending.confirmed), pending]);
        this.flushAutosave();
    }

    /** Sends the next waiting save, or runs what waits for the item to be saved when nothing is left. */
    private flushAutosave(): void {
        this.clearAutosaveTimer();
        // One save runs at a time; what waits is sent when it completes.
        if (this.isSaveInFlight()) {
            return;
        }
        const pending = this.takeNextSave();
        if (!pending) {
            const afterAutosave = this.afterAutosave;
            this.afterAutosave = undefined;
            // A form that became invalid meanwhile stays open, so the change is not dropped without asking.
            if (!this.isFormInvalid()) {
                afterAutosave?.();
            }
            return;
        }
        const unitId = this.editingUnitId();
        this.lastRequestedKey = pending.key;
        this.inFlight.set({ unitId, confirmed: pending.confirmed?.confirmed });
        this.saveFailure.set(undefined);
        const requestDone = () => {
            if (this.inFlight()?.unitId === unitId) {
                this.inFlight.set(undefined);
            }
        };
        // Not tied to the page: a save that already runs completes even when the user leaves.
        pending.request().subscribe({
            next: (savedUnit) => {
                requestDone();
                this.unitManagementComponent()?.replaceLectureUnit(deepClone(savedUnit));
                if (this.editingUnitId() === unitId) {
                    this.lastSavedKey = pending.key;
                    this.editingUnit.set(savedUnit);
                    this.takeOverSavedContent(pending, savedUnit);
                    this.lastSavedAt.set(dayjs());
                }
                // Also after the form closed because its item was deleted, so the saves of an item opened meanwhile are sent.
                this.flushAutosave();
            },
            error: (error: HttpErrorResponse) => {
                requestDone();
                if (this.isDestroyed) {
                    // Nobody sees the item anymore, and the request skipped the global alert.
                    this.alertService.error('artemisApp.lecture.unitEditor.failedAfterLeaving');
                    return;
                }
                if (this.editingUnitId() !== unitId) {
                    this.flushAutosave();
                    return;
                }
                this.lastRequestedKey = this.lastSavedKey;
                this.afterAutosave = undefined;
                this.keepForRetry(pending, error?.error?.title);
                if (pending.confirmed) {
                    // Only the file or link waits for Retry; a change of the details that waited behind it is still sent.
                    this.flushAutosave();
                }
            },
        });
    }

    /** Confirmed files and video links go first, then the newest details unless a confirmed save sent them already. */
    private takeNextSave(): PendingUnitSave | undefined {
        const [confirmed, ...laterConfirmed] = this.pendingConfirmed();
        if (confirmed) {
            this.pendingConfirmed.set(laterConfirmed);
            return this.confirmedRequest(confirmed);
        }
        const details = this.pendingDetails();
        this.pendingDetails.set(undefined);
        return details && details.key !== this.lastRequestedKey ? details : undefined;
    }

    /**
     * Keeps a failed save for Retry, unless a newer one of its kind waits or the form cannot be saved anymore. A confirmed file or link is
     * held until Retry or Save, so later changes do not send it again and again.
     */
    private keepForRetry(pending: PendingUnitSave, reason: string | undefined): void {
        const confirmed = pending.confirmed;
        if (confirmed) {
            if (!this.pendingConfirmed().some((save) => save.confirmed === confirmed.confirmed)) {
                this.heldConfirmed.update((held) => [...held.filter((failed) => failed.save.confirmed !== confirmed.confirmed), { save: confirmed, reason }]);
            }
            return;
        }
        if (!this.pendingDetails() && !this.isFormInvalid()) {
            this.pendingDetails.set(pending);
        }
        if (this.pendingDetails()) {
            this.saveFailure.set({ reason });
        }
    }

    private takeOverSavedContent(pending: PendingUnitSave, savedUnit: AttachmentVideoUnit): void {
        const form = this.attachmentVideoUnitForm();
        if (pending.confirmed?.confirmed === 'file') {
            form?.takeOverSavedFile(savedUnit.attachment?.link, savedUnit.attachment?.version, pending.confirmed.data.fileProperties.file);
        } else if (pending.confirmed?.confirmed === 'videoSource') {
            form?.takeOverSavedVideoSource(savedUnit.videoSource);
        }
    }

    /**
     * Builds the request of a confirmed file or video link when it starts, with the newest details that can be saved. So a retry does not
     * send the details of the time of the confirmation, which would undo a change that was saved meanwhile.
     */
    private confirmedRequest(confirmed: ConfirmedUnitSave): PendingUnitSave {
        const details = (this.latestAttachmentDetails ?? confirmed.data).formProperties;
        const data: AttachmentVideoUnitFormData = {
            formProperties: {
                name: details.name,
                description: details.description,
                releaseDate: details.releaseDate,
                competencyLinks: details.competencyLinks,
                videoSource: confirmed.data.formProperties.videoSource,
            },
            fileProperties: confirmed.data.fileProperties,
        };
        const withFile = confirmed.confirmed === 'file';
        return {
            key: this.attachmentVideoUnitSaveKey(data),
            request: () => this.saveAttachmentVideoUnit(data, { withFile, withVideoSource: !withFile }),
            confirmed,
        };
    }

    private clearAutosaveTimer(): void {
        if (this.autosaveTimer !== undefined) {
            clearTimeout(this.autosaveTimer);
            this.autosaveTimer = undefined;
        }
    }

    private saveTextUnit(data: TextUnitFormData): Observable<LectureUnit> {
        const textUnit = deepClone(this.editingUnit() as TextUnit);
        textUnit.name = data.name;
        textUnit.releaseDate = data.releaseDate;
        textUnit.content = data.content;
        textUnit.competencyLinks = this.competencyLinksToSend(data.competencyLinks);
        return this.textUnitService.update(textUnit, this.lecture().id!, AUTOSAVE_REQUEST_CONTEXT()).pipe(map(() => textUnit));
    }

    private saveOnlineUnit(data: OnlineUnitFormData): Observable<LectureUnit> {
        const onlineUnit = deepClone(this.editingUnit() as OnlineUnit);
        onlineUnit.name = data.name || undefined;
        onlineUnit.description = data.description || undefined;
        onlineUnit.releaseDate = data.releaseDate || undefined;
        onlineUnit.source = data.source || undefined;
        onlineUnit.competencyLinks = this.competencyLinksToSend(data.competencyLinks);
        return this.onlineUnitService.update(onlineUnit, this.lecture().id!, AUTOSAVE_REQUEST_CONTEXT()).pipe(map(() => onlineUnit));
    }

    /**
     * Saves the item's details. A new file and a new video link are only sent when the user confirmed them, because Artemis then
     * processes the content again; otherwise the item keeps its file and link.
     */
    private saveAttachmentVideoUnit(data: AttachmentVideoUnitFormData, options: { withFile: boolean; withVideoSource: boolean }): Observable<LectureUnit> {
        const savedUnit = this.editingUnit() as AttachmentVideoUnit;
        const attachmentVideoUnit = deepClone(savedUnit);
        const attachment = attachmentVideoUnit.attachment;
        const { name, description, releaseDate, videoSource, competencyLinks } = data.formProperties;
        const { file, fileName, notifyStudents } = data.fileProperties;
        const withUpload = options.withFile && !!file && !!fileName && file.size > 0;

        // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
        attachmentVideoUnit.attachment = undefined;
        attachmentVideoUnit.name = name;
        attachmentVideoUnit.description = description || undefined;
        attachmentVideoUnit.releaseDate = releaseDate || undefined;
        attachmentVideoUnit.competencyLinks = this.competencyLinksToSend(competencyLinks);
        attachmentVideoUnit.videoSource = options.withVideoSource ? videoSource || undefined : savedUnit.videoSource;
        attachmentVideoUnit.attachmentUpdateIntent = withUpload ? AttachmentUpdateIntent.FILE_UPLOAD : AttachmentUpdateIntent.NO_FILE_CHANGE;

        const formData = new FormData();
        if (withUpload) {
            const attachmentToUpdate = attachment ? deepClone(attachment) : new Attachment();
            attachmentToUpdate.attachmentVideoUnit = undefined;
            formData.append('file', file, fileName);
            formData.append('attachment', objectToJsonBlob(this.attachmentForUpload(attachmentToUpdate, name, releaseDate)));
        }
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));
        // The server notifies students whenever a notification text is sent; it does not use the text itself.
        const notificationText = withUpload && notifyStudents ? '' : undefined;

        return this.attachmentVideoUnitService.update(this.lecture().id!, attachmentVideoUnit.id!, formData, notificationText, AUTOSAVE_REQUEST_CONTEXT()).pipe(
            map((response) => {
                // The response has the new version and link of an uploaded file.
                attachmentVideoUnit.attachment = response.body?.attachment ?? attachment;
                return attachmentVideoUnit;
            }),
        );
    }

    /**
     * The competency selection reports no links once the last one is removed, but the server reads missing links as "unchanged". So an item
     * that had links sends an empty list, and one without links sends none.
     */
    private competencyLinksToSend(links: CompetencyLectureUnitLink[] | undefined): CompetencyLectureUnitLink[] | undefined {
        return links ?? (this.editingUnit()?.competencyLinks?.length ? [] : undefined);
    }

    private attachmentForUpload(attachment: Attachment, name: string | undefined, releaseDate: dayjs.Dayjs | undefined): Attachment {
        attachment.name = name;
        attachment.releaseDate = releaseDate;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.version = 1;
        attachment.uploadDate = dayjs();
        return attachment;
    }

    private textUnitSaveKey(data: TextUnitFormData): string {
        return JSON.stringify([data.name ?? '', data.content ?? '', this.dateKey(data.releaseDate), this.competencyKey(data.competencyLinks)]);
    }

    private onlineUnitSaveKey(data: OnlineUnitFormData): string {
        return JSON.stringify([data.name ?? '', data.description ?? '', data.source ?? '', this.dateKey(data.releaseDate), this.competencyKey(data.competencyLinks)]);
    }

    private attachmentVideoUnitSaveKey(data: AttachmentVideoUnitFormData): string {
        const { name, description, releaseDate, competencyLinks } = data.formProperties;
        return JSON.stringify([name ?? '', description ?? '', this.dateKey(releaseDate), this.competencyKey(competencyLinks)]);
    }

    private dateKey(date: dayjs.Dayjs | undefined): number | undefined {
        return date && dayjs(date).isValid() ? dayjs(date).valueOf() : undefined;
    }

    private competencyKey(links: CompetencyLectureUnitLink[] | undefined): string | undefined {
        return links
            ? links
                  .map((link) => `${link.competency?.id}:${link.weight}`)
                  .sort()
                  .join(',')
            : undefined;
    }

    /**
     * Opens the page that splits the selected PDF into one content item per section. The page works on a copy of the file and offers
     * instructors to remove the PDF item afterwards, which it selects by default; unsaved changes to the lecture details are left to
     * the unsaved changes guard of this page.
     */
    splitSelectedPdf(): void {
        const unit = this.selectedSplitUnit();
        const courseId = this.lecture().course?.id;
        if (!unit?.id || !courseId || !this.lecture().id) {
            return;
        }
        this.isPreparingSplit.set(true);
        this.attachmentVideoUnitService
            .getAttachmentFile(courseId, unit.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (blob) => {
                    this.isPreparingSplit.set(false);
                    // The split only accepts file names that end in a lowercase .pdf.
                    this.openSplitPage(new File([blob], `${unit.name || 'lecture'}.pdf`, { type: 'application/pdf' }), unit);
                },
                error: (error: HttpErrorResponse) => {
                    this.isPreparingSplit.set(false);
                    onError(this.alertService, error);
                },
            });
    }

    /** Opens the file dialog for a PDF that is not content of the lecture yet. */
    chooseOtherPdf(): void {
        const input = this.otherPdfInput()?.nativeElement;
        if (input) {
            input.value = '';
            input.click();
        }
    }

    onOtherPdfChosen(input: HTMLInputElement): void {
        const file = input.files?.[0];
        if (!file) {
            return;
        }
        // The file dialog also offers all files.
        if (!file.name.toLowerCase().endsWith('.pdf')) {
            this.alertService.error('artemisApp.attachment.pdfPreview.invalidFileType');
            return;
        }
        // The split only accepts file names that end in a lowercase .pdf.
        const name = file.name.replace(/\.pdf$/i, '');
        this.openSplitPage(new File([file], `${name}.pdf`, { type: 'application/pdf' }));
    }

    private openSplitPage(file: File, sourceUnit?: AttachmentVideoUnit): void {
        const courseId = this.lecture().course?.id;
        const lectureId = this.lecture().id;
        void this.router.navigate(['course-management', courseId, 'lectures', lectureId, 'unit-management', 'attachment-video-units', 'process'], {
            state: {
                file,
                fileName: file.name,
                returnToEditor: true,
                // Deleting a content item needs an instructor, so only they are offered to remove the PDF item after the split.
                sourceUnit: sourceUnit && this.lecture().isAtLeastInstructor ? { id: sourceUnit.id, name: sourceUnit.name } : undefined,
            },
        });
    }

    /**
     * Handles PDF files dropped on the drop zone
     * Creates attachment units for each file with name from filename and release date 15 min in future
     * Opens the edit form for the last created unit
     */
    onPdfFilesDropped(files: File[]): void {
        if (files.length === 0 || !this.lecture().id) {
            return;
        }

        this.isUploadingPdfs.set(true);
        let lastCreatedUnit: AttachmentVideoUnit | undefined;

        from(files)
            .pipe(
                concatMap((file) => this.createAttachmentUnitFromFile(file)),
                filter((response) => response.body != null),
                map((response) => response.body as AttachmentVideoUnit),
            )
            .subscribe({
                next: (unit) => {
                    lastCreatedUnit = unit;
                },
                error: (error: HttpErrorResponse) => {
                    this.isUploadingPdfs.set(false);
                    onError(this.alertService, error);
                },
                complete: () => {
                    this.isUploadingPdfs.set(false);
                    this.alertService.success('artemisApp.lecture.pdfUpload.success');
                    this.unitManagementComponent()?.loadData();
                    // Open edit form for the last created unit
                    if (lastCreatedUnit) {
                        this.startEditLectureUnit(lastCreatedUnit);
                    }
                },
            });
    }

    /**
     * Creates a single attachment unit from a file
     */
    private createAttachmentUnitFromFile(file: File) {
        return this.attachmentVideoUnitService.createAttachmentVideoUnitFromFile(this.lecture().id!, file);
    }
}
