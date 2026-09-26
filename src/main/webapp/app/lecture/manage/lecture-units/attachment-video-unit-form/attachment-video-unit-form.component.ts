import { Component, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { buildEmbedUrl, parseVideoUrl } from './video-url-parser';
import { faArrowLeft, faArrowUpRightFromSquare, faCircleInfo, faFileArrowUp, faQuestionCircle, faRotateLeft, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/foundation/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiMessageComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
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
}

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
        TranslateDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        FormDateTimePickerComponent,
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
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faFileArrowUp = faFileArrowUp;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly faRotateLeft = faRotateLeft;
    protected readonly FeatureToggle = FeatureToggle;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
    // the button carries the TUM UI button component, so read the element instead of the component
    private readonly replaceFileButton = viewChild('replaceFileButton', { read: ElementRef<HTMLButtonElement> });
    file?: File;
    readonly fileInputTouched = signal(false);

    /** Name of the chosen file, or in edit mode the stored link of the unit's current file until a new file is chosen. */
    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

    /** Stored link of the file the unit already has when it is edited. */
    private readonly currentFileLink = signal<string | undefined>(undefined);
    /** Readable name of the unit's current file: the stored link without its path and upload timestamp. */
    readonly currentFileName = computed(() => {
        const link = this.currentFileLink();
        return link ? this.fileService.replaceAttachmentPrefixAndUnderscores(decodeFileName(link.substring(link.lastIndexOf('/') + 1))) : undefined;
    });
    readonly currentFileVersion = computed(() => this.formData()?.formProperties?.version);
    /** The version is part of the URL so that the browser does not serve an older, cached file after a replacement. */
    readonly currentFileUrl = computed(() => {
        const url = addPublicFilePrefix(this.currentFileLink());
        return url ? this.fileService.addAttachmentVersionToUrl(url, this.currentFileVersion()) : undefined;
    });
    readonly isReplacingFile = computed(() => !!this.currentFileLink() && !!this.fileName() && this.fileName() !== this.currentFileLink());

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

    readonly videoSourceSignal = toSignal(this.videoSourceControl!.valueChanges, { initialValue: this.videoSourceControl!.value });

    isFormValid = computed(() => {
        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.datePickerComponent()?.isValid() && (!!this.fileName() || !!this.videoSourceSignal());
    });

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
     * Discards the file chosen to replace the unit's current file, so saving keeps the current file.
     */
    keepCurrentFile(): void {
        this.file = undefined;
        this.fileName.set(this.currentFileLink());
        this.isFileTooBig.set(false);
        this.fileInput().nativeElement.value = '';
        // the button that triggered this disappears, so keep the keyboard focus in the file field
        this.replaceFileButton()?.nativeElement.focus();
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
        const formValue = this.form.value;
        const formProperties: FormProperties = deepClone(formValue);
        const fileProperties: FileProperties = {
            file: this.file,
            fileName: this.fileName(),
        };

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
        });
    }

    private setFormValues(formData: AttachmentVideoUnitFormData) {
        if (formData?.formProperties) {
            this.form.patchValue(formData.formProperties);
        }
        // take over the file state completely, so switching to a unit without a file does not keep the previous unit's file
        const { file, fileName } = formData?.fileProperties ?? {};
        this.file = file;
        this.fileName.set(fileName);
        this.currentFileLink.set(file ? undefined : fileName);
        this.isFileTooBig.set(false);
    }

    private clearForm() {
        this.form.reset();
        this.file = undefined;
        this.fileName.set(undefined);
        this.currentFileLink.set(undefined);
        this.isFileTooBig.set(false);
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
