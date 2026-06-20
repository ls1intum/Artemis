import { Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { buildEmbedUrl, parseVideoUrl } from './video-url-parser';
import { faArrowLeft, faCircleInfo, faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/foundation/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { GocastStreamPickerComponent } from 'app/videosource/gocast/gocast-stream-picker.component';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
}

// matches structure of the reactive form
export interface FormProperties {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    updateNotificationText?: string;
    videoSource?: string;
    urlHelper?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
    /** Set when a TUM Live stream is selected via the stream picker (Stage 2). */
    gocastStreamId?: number;
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
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
        NgbTooltip,
        FormDateTimePickerComponent,
        CompetencySelectionComponent,
        ArtemisTranslatePipe,
        FeatureToggleHideDirective,
        GocastStreamPickerComponent,
    ],
})
export class AttachmentVideoUnitFormComponent {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly FeatureToggle = FeatureToggle;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    /**
     * The Artemis course id. When provided, the TUM Live stream picker (Stage 2) is rendered
     * below the video URL field. The picker self-resolves the binding status and only shows
     * the stream dropdown when the binding is ACTIVE.
     */
    courseId = input<number | undefined>(undefined);

    /** streamId selected via the TUM Live stream picker; included in the emitted form data. */
    private selectedGocastStreamId: number | undefined;
    /** The URL the picker auto-filled into the video source field; cleared together with the stream on de-selection. */
    private lastAutoFilledGocastUrl: string | undefined;

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    fileInput = viewChild<ElementRef>('fileInput');
    file: File;
    fileInputTouched = false;

    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    private readonly formBuilder = inject(FormBuilder);

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
            if (this.isEditMode() && formData && formData !== this.appliedFormData) {
                this.appliedFormData = formData;
                this.setFormValues(formData);
            }
        });
    }

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        version: [{ value: 1, disabled: true }],
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
        this.file = input.files[0];
        this.fileName.set(this.file.name);
        // automatically set the name in case it is not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                // without extension
                name: this.file.name.replace(/\.[^/.]+$/, ''),
            });
        }
        this.isFileTooBig.set(this.file.size > MAX_FILE_SIZE);
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

    get versionControl() {
        return this.form.get('version');
    }

    get videoSourceControl() {
        return this.form.get('videoSource');
    }

    get urlHelperControl() {
        return this.form.get('urlHelper');
    }

    /**
     * Called when the TUM Live stream picker (Stage 2) selection changes.
     * Records the chosen streamId for inclusion in the submitted form data, or clears it
     * (and the auto-filled URL) when the picker selection is cleared.
     */
    onGocastStreamSelected(event: { streamId: number; streamName: string; slug?: string } | undefined): void {
        if (!event) {
            // Selection cleared — drop the cached id and remove the URL we auto-filled.
            if (this.selectedGocastStreamId !== undefined && this.videoSourceControl?.value === this.lastAutoFilledGocastUrl) {
                this.videoSourceControl?.setValue('');
            }
            this.selectedGocastStreamId = undefined;
            this.lastAutoFilledGocastUrl = undefined;
            return;
        }
        this.selectedGocastStreamId = event.streamId;
        // Auto-fill the video source URL.
        // Format: https://tum.live/w/{courseSlug}/{streamId} — required by TumLiveService regex.
        // Only write when the field is empty or still holds a value we previously auto-filled,
        // so a URL the user typed/edited by hand is preserved. This also keeps the URL in sync
        // when the picker selection changes from one stream to another.
        const currentValue = this.videoSourceControl?.value;
        const canAutoFill = !currentValue || currentValue === this.lastAutoFilledGocastUrl;
        if (canAutoFill && event.slug) {
            const url = `https://tum.live/w/${event.slug}/${event.streamId}`;
            this.videoSourceControl?.setValue(url);
            this.lastAutoFilledGocastUrl = url;
        }
    }

    submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = {
            ...formValue,
            gocastStreamId: this.selectedGocastStreamId,
        };
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
        if (formData?.fileProperties?.file) {
            this.file = formData?.fileProperties?.file;
        }
        if (formData?.fileProperties?.fileName) {
            this.fileName.set(formData?.fileProperties?.fileName);
        }
    }

    get isTransformable() {
        if (this.urlHelperControl!.value === undefined || this.urlHelperControl!.value === null || this.urlHelperControl!.value === '') {
            return false;
        } else {
            return !this.urlHelperControl?.invalid;
        }
    }

    setEmbeddedVideoUrl(event: any) {
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
