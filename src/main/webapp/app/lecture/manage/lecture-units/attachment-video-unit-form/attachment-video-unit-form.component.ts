import { Component, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { buildEmbedUrl, parseVideoUrl } from './video-url-parser';
import { faArrowUp, faArrowUpRightFromSquare, faCircleInfo, faFileArrowUp, faQuestionCircle, faRotateLeft, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE, UPLOAD_FILE_EXTENSIONS } from 'app/foundation/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { UnitFormChange } from 'app/lecture/manage/lecture-units/unit-form-change.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDatePickerComponent,
    TumAetUiFormFieldComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { FileService } from 'app/foundation/service/file.service';
import { addPublicFilePrefix } from 'app/app.constants';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
}

// matches structure of the reactive form
export interface FormProperties {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    /** Version of the unit's current file; only passed in to be shown, the form does not edit it. */
    version?: number;
    updateNotificationText?: string;
    videoSource?: string;
    urlHelper?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
    /** Whether students hear about a new file that the user confirmed for an item edited in place. */
    notifyStudents?: boolean;
}

/** A file or video link the user confirmed, which is saved only on confirmation. */
export type ConfirmedContent = 'file' | 'videoSource';

/** The controls whose changes an item edited in place saves automatically; the video link and its helper are saved on confirmation only. */
const DETAIL_CONTROLS = ['name', 'description', 'releaseDate', 'competencyLinks'];

/** Stored file names are URL encoded in links; keep the raw name if it is not valid percent encoding. */
function decodeFileName(fileName: string): string {
    try {
        return decodeURIComponent(fileName);
    } catch {
        return fileName;
    }
}

function isTumLiveUrl(url: URL): boolean {
    const tumLiveUrls = ['live.rbg.tum.de', 'tum.live'];
    return tumLiveUrls.includes(url.host);
}

function isVideoOnlyTumUrl(url: URL): boolean {
    return url?.searchParams.get('video_only') === '1';
}

function videoSourceTransformUrlValidator(control: AbstractControl): ValidationErrors | undefined {
    const urlValue = control.value;
    if (!urlValue) {
        return undefined;
    }
    let url;
    try {
        url = new URL(urlValue);
    } catch {
        // intentionally empty
    }
    if ((url && isTumLiveUrl(url)) || parseVideoUrl(urlValue)) {
        return undefined;
    }
    return { invalidVideoUrl: true };
}

function videoSourceUrlValidator(control: AbstractControl): ValidationErrors | undefined {
    const urlValue = control.value;
    if (!urlValue) {
        return undefined;
    }
    let url;
    try {
        url = new URL(control.value);
    } catch {
        // intentionally empty
    }
    if (url && !(isTumLiveUrl(url) && !isVideoOnlyTumUrl(url))) {
        return undefined;
    }
    return { invalidVideoUrl: true };
}

@Component({
    selector: 'jhi-attachment-video-unit-form',
    templateUrl: './attachment-video-unit-form.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        NgTemplateOutlet,
        TranslateDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiDatePickerComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        CompetencySelectionComponent,
        ArtemisTranslatePipe,
        FeatureToggleHideDirective,
    ],
})
export class AttachmentVideoUnitFormComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly fileService = inject(FileService);

    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowUp = faArrowUp;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faFileArrowUp = faFileArrowUp;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly faRotateLeft = faRotateLeft;
    protected readonly faSpinner = faSpinner;
    protected readonly FeatureToggle = FeatureToggle;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    /**
     * Reports changes instead of offering Submit, for an item that is edited in place and saved automatically. A chosen file is uploaded
     * at once; a new video link is saved only when the user confirms it, because Artemis then transcribes the video.
     */
    readonly autosave = input<boolean>(false);
    readonly formChanged = output<UnitFormChange<AttachmentVideoUnitFormData>>();
    /** Emits when the user chose a new file, with whether to notify students. */
    readonly fileUploadRequested = output<AttachmentVideoUnitFormData>();
    /** Emits when the user confirms a new video link. */
    readonly videoSourceSaveRequested = output<AttachmentVideoUnitFormData>();
    /** Emits when the user takes back a confirmed file or video link, so it is not sent if it still waits or failed. */
    readonly confirmedContentWithdrawn = output<ConfirmedContent>();
    /** The confirmed file or video link whose request runs; it can no longer be taken back. */
    readonly savingConfirmed = input<ConfirmedContent | undefined>(undefined);
    /** Whether students hear about the next file the user chooses. */
    readonly notifyStudents = signal(false);
    /** Set once the new file was sent or the video link confirmed, until it is saved or taken back, so it is not sent twice. */
    readonly isFileUploadRequested = signal(false);
    readonly isVideoSourceSaveRequested = signal(false);
    /** Set while the form takes over the data of the item, which is not a change of the user. */
    private applyingFormData = false;
    /** The video link the item has, to tell a new link, which needs confirming, apart from the saved one. */
    private readonly savedVideoSource = signal<string | undefined>(undefined);
    /** The video link the user confirmed last, to tell it apart from a link typed while it is saved. */
    private readonly requestedVideoSource = signal<string | undefined>(undefined);

    /** The release date picker keeps its last valid date while the typed text is not a date yet, so that text is tracked separately. */
    readonly isReleaseDateTextValid = signal(true);

    // have to handle the file input as a special case at is not part of the reactive form
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
    // read the elements explicitly, as they are focused through their DOM API
    private readonly replaceFileButton = viewChild('replaceFileButton', { read: ElementRef<HTMLButtonElement> });
    private readonly chooseFileButton = viewChild('chooseFileButton', { read: ElementRef<HTMLButtonElement> });
    private readonly videoSourceInput = viewChild('videoSourceInput', { read: ElementRef<HTMLInputElement> });
    file?: File;
    readonly fileInputTouched = signal(false);

    /** Name of the chosen file, or in edit mode the stored link of the unit's current file until a new file is chosen. */
    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);
    /** Whether the last file chosen or dropped has a type the server does not accept; that file was not taken. */
    readonly isFileTypeUnsupported = signal(false);

    /** Stored link of the file the unit already has when it is edited. */
    private readonly currentFileLink = signal<string | undefined>(undefined);
    /** Readable name of the unit's current file: the stored link without its path and upload timestamp. */
    readonly currentFileName = computed(() => {
        const link = this.currentFileLink();
        return link ? this.fileService.replaceAttachmentPrefixAndUnderscores(decodeFileName(link.substring(link.lastIndexOf('/') + 1))) : undefined;
    });
    /** Version of the unit's current file when it is edited. */
    readonly currentFileVersion = signal<number | undefined>(undefined);
    /** The version is part of the URL so that the browser does not serve an older, cached file after a replacement. */
    readonly currentFileUrl = computed(() => {
        const url = addPublicFilePrefix(this.currentFileLink());
        return url ? this.fileService.addAttachmentVersionToUrl(url, this.currentFileVersion()) : undefined;
    });
    readonly isReplacingFile = computed(() => !!this.currentFileLink() && !!this.fileName() && this.fileName() !== this.currentFileLink());
    /** Whether a file was chosen that the item does not have yet, as a replacement or as the first file of a video item. */
    readonly hasNewFile = computed(() => !!this.fileName() && this.fileName() !== this.currentFileLink());

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    // Tracks the formData reference already applied to the form so the patching effect stays idempotent.
    private appliedFormData?: AttachmentVideoUnitFormData;

    constructor() {
        // Patch ONCE per distinct formData value: form.patchValue() synchronously emits statusChanges,
        // which is mirrored into the `statusChanges` signal via toSignal(...). Under zoneless that signal
        // write reschedules the reactive flush, which re-runs this effect, which patches again — an
        // infinite change-detection loop that leaves the edit form stuck behind the loading spinner.
        // Guarding on the formData reference breaks the cycle and avoids clobbering in-progress edits.
        this.nameControl!.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reportChange(false));
        this.descriptionControl!.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reportChange(false));
        this.form
            .get('competencyLinks')!
            .valueChanges.pipe(takeUntilDestroyed())
            .subscribe(() => this.reportChange(true));

        effect(() => {
            const formData = this.formData();
            if (this.isEditMode()) {
                if (formData && formData !== this.appliedFormData) {
                    this.appliedFormData = formData;
                    this.setFormValues(formData);
                }
            } else if (this.appliedFormData) {
                // The lecture edit page reuses this form instance to create a unit right after editing one,
                // so drop the edited unit's values instead of creating the new unit with them.
                this.appliedFormData = undefined;
                untracked(() => this.clearForm());
            }
        });
    }

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        videoSource: [undefined as string | undefined, this.videoSourceUrlValidator],
        urlHelper: [undefined as string | undefined, this.videoSourceTransformUrlValidator],
        updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');
    /** Every change of a control, also one that leaves the status of the whole form as it was, such as the name turning invalid while the video link is invalid. */
    private readonly formEvent = toSignal(this.form.events);

    readonly videoSourceSignal = toSignal(this.videoSourceControl!.valueChanges, { initialValue: this.videoSourceControl!.value });
    readonly isVideoSourceChanged = computed(() => this.autosave() && (this.videoSourceSignal() || undefined) !== (this.savedVideoSource() || undefined));
    readonly nextFileVersion = computed(() => (this.currentFileVersion() ?? 0) + 1);
    readonly hasSavedVideoSource = computed(() => !!this.savedVideoSource());

    isFormValid = computed(() => {
        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.isReleaseDateTextValid() && (!!this.fileName() || !!this.videoSourceSignal());
    });

    /** Whether the details that an item edited in place saves automatically can be saved; a new video link or its helper do not count. */
    readonly areDetailsValid = computed(() => {
        this.formEvent();
        return this.detailsValid();
    });
    /** An item keeps a file or a video link, so the link can only be removed from an item that has a file. */
    readonly canSaveVideoSource = computed(
        () => this.areDetailsValid() && !this.videoSourceControl?.invalid && (!!this.currentFileLink() || !!this.videoSourceSignal()) && !this.isVideoSourceSaveRequested(),
    );
    /**
     * A new video link that the user entered for an item edited in place but did not confirm yet. While a confirmed link is saved, any other
     * link counts, also the saved one, which the confirmed link would replace. A chosen file is uploaded at once.
     */
    readonly hasUnconfirmedContent = computed(() =>
        this.autosave() && this.isVideoSourceSaveRequested() ? (this.videoSourceSignal() || undefined) !== (this.requestedVideoSource() || undefined) : this.isVideoSourceChanged(),
    );

    private detailsValid(): boolean {
        // A control reports its change before the form takes it over, so the form's own validity lags one change behind in its valueChanges.
        return DETAIL_CONTROLS.every((name) => !this.form.get(name)?.invalid) && this.isReleaseDateTextValid();
    }

    private reportChange(immediate: boolean): void {
        if (!this.autosave() || this.applyingFormData) {
            return;
        }
        this.formChanged.emit({ data: this.currentFormData(), immediate, valid: this.detailsValid() });
    }

    private currentFormData(): AttachmentVideoUnitFormData {
        const formProperties: FormProperties = deepClone(this.form.getRawValue());
        const fileProperties: FileProperties = this.autosave()
            ? { file: this.file, fileName: this.fileName(), notifyStudents: this.notifyStudents() }
            : { file: this.file, fileName: this.fileName() };
        return { formProperties, fileProperties };
    }

    onReleaseDateTextValidityChange(valid: boolean): void {
        // The picker also reports the validity it starts with, which is no change of the user.
        if (valid === this.isReleaseDateTextValid()) {
            return;
        }
        this.isReleaseDateTextValid.set(valid);
        this.reportChange(false);
    }

    /**
     * Uploads the chosen file of an item edited in place; saving it splits the slides again and processes the content for Iris again. The item
     * sends it with its newest valid details, so the details may be invalid meanwhile.
     */
    private uploadNewFile(): void {
        this.isFileUploadRequested.set(true);
        this.fileUploadRequested.emit(this.currentFormData());
    }

    /** Confirms the new video link once; saving it has the video transcribed and processed for Iris. It is sent with the details, so they have to be valid. */
    saveVideoSource(): void {
        if (this.canSaveVideoSource()) {
            this.isVideoSourceSaveRequested.set(true);
            this.requestedVideoSource.set(this.videoSourceSignal());
            this.videoSourceSaveRequested.emit(this.currentFormData());
        }
    }

    /**
     * Takes the file as the item's own after it was uploaded and saved. Everything else stays as typed, including a
     * file the user chose meanwhile.
     * @param fileLink the stored link of the saved file
     * @param version the version of the saved file
     * @param uploadedFile the file that was uploaded
     */
    takeOverSavedFile(fileLink: string | undefined, version: number | undefined, uploadedFile?: File): void {
        this.currentFileLink.set(fileLink);
        this.currentFileVersion.set(version);
        // A file chosen after the upload started stays; without one, the uploaded file is the item's file now, also when the user kept the old one meanwhile.
        if (uploadedFile && this.file && this.file !== uploadedFile) {
            return;
        }
        this.isFileUploadRequested.set(false);
        this.notifyStudents.set(false);
        this.file = undefined;
        this.fileName.set(fileLink);
        this.fileInput().nativeElement.value = '';
    }

    /**
     * Takes the video link as the item's own after the user confirmed it and it was saved.
     * @param videoSource the saved video link
     */
    takeOverSavedVideoSource(videoSource: string | undefined): void {
        this.savedVideoSource.set(videoSource);
        this.isVideoSourceSaveRequested.set(false);
        this.applyingFormData = true;
        this.urlHelperControl?.setValue(undefined);
        this.applyingFormData = false;
    }

    /** Goes back to the video link the item has, and takes back a confirmation of the new one that was not saved yet. */
    discardVideoSource(): void {
        this.videoSourceControl?.setValue(this.savedVideoSource());
        this.urlHelperControl?.setValue(undefined);
        this.withdraw('videoSource');
        // the button that triggered this disappears, so keep the keyboard focus at the video link
        this.videoSourceInput()?.nativeElement.focus();
    }

    private withdraw(content: ConfirmedContent): void {
        const requested = content === 'file' ? this.isFileUploadRequested : this.isVideoSourceSaveRequested;
        if (this.autosave() && requested()) {
            requested.set(false);
            this.confirmedContentWithdrawn.emit(content);
        }
    }

    onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }
        this.setChosenFile(input.files[0]);
    }

    /**
     * Lets a file be dropped onto the file field, as onto the native file input it replaces. Without this, the browser
     * would open the dropped file and leave the form.
     */
    onFileDragOver(event: DragEvent): void {
        event.preventDefault();
    }

    onFileDrop(event: DragEvent): void {
        event.preventDefault();
        const file = event.dataTransfer?.files?.[0];
        if (file) {
            this.fileInputTouched.set(true);
            this.setChosenFile(file);
        }
    }

    private setChosenFile(file: File): void {
        // `accept` only filters the file dialog, so a dropped file, or one picked through the dialog's "All files" option,
        // can have any type; keep the previous file instead of taking one the server would reject on submit
        const extensionStart = file.name.lastIndexOf('.');
        const extension = extensionStart >= 0 ? file.name.substring(extensionStart + 1).toLowerCase() : '';
        this.isFileTypeUnsupported.set(!UPLOAD_FILE_EXTENSIONS.includes(extension));
        if (this.isFileTypeUnsupported()) {
            return;
        }
        // An item edited in place uploads the file at once, so a file the server would reject is not taken either.
        if (this.autosave()) {
            this.isFileTooBig.set(file.size > MAX_FILE_SIZE);
            if (this.isFileTooBig()) {
                return;
            }
        }
        // A new choice replaces a confirmed file that was not saved yet.
        this.withdraw('file');
        this.file = file;
        this.fileName.set(file.name);
        // automatically set the name in case it is not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                // without extension
                name: file.name.replace(/\.[^/.]+$/, ''),
            });
        }
        this.isFileTooBig.set(file.size > MAX_FILE_SIZE);
        if (this.autosave()) {
            this.uploadNewFile();
        }
    }

    /**
     * Opens the browser's file dialog of the hidden file input. The input is cleared first, so choosing the same file again
     * still counts as a change.
     */
    openFilePicker(): void {
        this.fileInputTouched.set(true);
        const input = this.fileInput().nativeElement;
        input.value = '';
        input.click();
    }

    /**
     * Discards the file chosen to replace the unit's current file, or to be the first file of a video item, so saving keeps the current state.
     */
    keepCurrentFile(): void {
        this.withdraw('file');
        this.file = undefined;
        this.fileName.set(this.currentFileLink());
        this.isFileTooBig.set(false);
        this.isFileTypeUnsupported.set(false);
        this.fileInput().nativeElement.value = '';
        // the button that triggered this disappears, so keep the keyboard focus in the file field
        (this.replaceFileButton() ?? this.chooseFileButton())?.nativeElement.focus();
    }

    get nameControl() {
        return this.form.get('name');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    onReleaseDateChange(releaseDate: dayjs.Dayjs | undefined): void {
        this.releaseDateControl?.setValue(releaseDate);
        this.releaseDateControl?.markAsDirty();
        this.reportChange(true);
    }

    get updateNotificationTextControl() {
        return this.form.get('updateNotificationText');
    }

    get videoSourceControl() {
        return this.form.get('videoSource');
    }

    get urlHelperControl() {
        return this.form.get('urlHelper');
    }

    submitForm() {
        this.formSubmitted.emit(this.currentFormData());
    }

    private setFormValues(formData: AttachmentVideoUnitFormData) {
        this.applyingFormData = true;
        if (formData?.formProperties) {
            this.form.patchValue(formData.formProperties);
        }
        this.savedVideoSource.set(formData?.formProperties?.videoSource);
        this.notifyStudents.set(false);
        this.isFileUploadRequested.set(false);
        this.isVideoSourceSaveRequested.set(false);
        this.applyingFormData = false;
        // take over the file state completely, so switching to a unit without a file does not keep the previous unit's file
        const { file, fileName } = formData?.fileProperties ?? {};
        this.file = file;
        this.fileName.set(fileName);
        this.currentFileLink.set(file ? undefined : fileName);
        this.currentFileVersion.set(formData?.formProperties?.version);
        this.isFileTooBig.set(false);
        this.isFileTypeUnsupported.set(false);
    }

    private clearForm() {
        this.form.reset();
        this.file = undefined;
        this.fileName.set(undefined);
        this.currentFileLink.set(undefined);
        this.currentFileVersion.set(undefined);
        this.isFileTooBig.set(false);
        this.isFileTypeUnsupported.set(false);
        this.fileInputTouched.set(false);
        this.fileInput().nativeElement.value = '';
    }

    get isTransformable() {
        if (this.urlHelperControl!.value === undefined || this.urlHelperControl!.value === null || this.urlHelperControl!.value === '') {
            return false;
        } else {
            return !this.urlHelperControl?.invalid;
        }
    }

    setEmbeddedVideoUrl(event: Event) {
        event.stopPropagation();

        const originalUrl = this.urlHelperControl!.value;
        const embeddedUrl = this.extractEmbeddedUrl(originalUrl);
        this.videoSourceControl!.setValue(embeddedUrl);
    }

    extractEmbeddedUrl(videoUrl: string) {
        const url = new URL(videoUrl);
        if (isTumLiveUrl(url)) {
            url.searchParams.set('video_only', '1');
            return url.toString();
        }
        const parsed = parseVideoUrl(videoUrl);
        if (!parsed) {
            return videoUrl;
        }
        return buildEmbedUrl(parsed);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
