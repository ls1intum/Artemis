import { Component, ElementRef, OnDestroy, ViewChild, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faCheck, faExclamationTriangle, faQuestionCircle, faTimes, faVideo } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE, MAX_VIDEO_FILE_SIZE } from 'app/shared/constants/input.constants';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_VIDEO_UPLOAD } from 'app/app.constants';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
    videoFileProperties?: VideoFileProperties;
    uploadProgressCallback?: (progress: number, status: string) => void;
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
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
}

// video file input is also a special case
export interface VideoFileProperties {
    videoFile?: File;
    videoFileName?: string;
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
    let parsedUrl, url;
    try {
        url = new URL(urlValue);
        parsedUrl = urlParser.parse(urlValue);
    } catch {
        //intentionally empty
    }
    // The URL is valid if it's a TUM-Live URL or if it can be parsed by the js-video-url-parser.
    if ((url && isTumLiveUrl(url)) || parsedUrl) {
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
    styleUrl: './attachment-video-unit-form.component.scss',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FaIconComponent, NgbTooltip, FormDateTimePickerComponent, CompetencySelectionComponent, ArtemisTranslatePipe],
})
export class AttachmentVideoUnitFormComponent implements OnDestroy {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faCheck = faCheck;
    protected readonly faVideo = faVideo;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;
    // Upload progress tracking
    isUploading = signal(false);
    uploadProgress = signal(0);
    uploadStatus = signal('');
    private uploadResetTimeoutId?: number;
    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    fileInput = viewChild<ElementRef>('fileInput');
    file: File;
    fileInputTouched = false;

    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

    // video file input handling
    @ViewChild('videoFileInput', { static: false })
    videoFileInput: ElementRef;
    videoFile: File;
    videoFileInputTouched = false;

    videoFileName = signal<string | undefined>(undefined);
    isVideoFileTooBig = signal<boolean>(false);

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    private readonly formBuilder = inject(FormBuilder);
    private readonly profileService = inject(ProfileService);
    readonly isVideoUploadEnabled = computed(() => this.profileService.isModuleFeatureActive(MODULE_FEATURE_VIDEO_UPLOAD));

    constructor() {
        effect(() => {
            const formData = this.formData();
            if (this.isEditMode() && formData) {
                this.setFormValues(formData);
            }
        });
    }

    ngOnDestroy(): void {
        if (this.uploadResetTimeoutId) {
            clearTimeout(this.uploadResetTimeoutId);
        }
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
        const hasValidFile = !!this.fileName();
        const hasValidVideoFile = !!this.videoFileName();
        const hasVideoSource = !!this.videoSourceSignal();

        // If video upload is disabled and user tries to upload a NEW video file, form is invalid
        // Allow pre-populated data (from edit mode) without invalidating the form
        if (this.videoFileInputTouched && hasValidVideoFile && !this.isVideoUploadEnabled()) {
            return false;
        }

        // Must have at least one of: PDF file, video file, or video source URL
        const hasContent = hasValidFile || hasValidVideoFile || hasVideoSource;

        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && !this.isVideoFileTooBig() && this.datePickerComponent()?.isValid() && hasContent;
    });

    onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }

        this.fileInputTouched = true;
        this.file = input.files[0];
        this.fileName.set(this.file.name);

        // Validate file size for PDF files
        this.isFileTooBig.set(this.file.size > MAX_FILE_SIZE);

        // Automatically set the name if not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                name: this.file.name.replace(/\.[^/.]+$/, ''),
            });
        }
    }

    onVideoFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }

        this.videoFileInputTouched = true;
        this.videoFile = input.files[0];
        this.videoFileName.set(this.videoFile.name);

        // Validate file size for video files
        this.isVideoFileTooBig.set(this.videoFile.size > MAX_VIDEO_FILE_SIZE);

        // Automatically set the name if not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                name: this.videoFile.name.replace(/\.[^/.]+$/, ''),
            });
        }
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

    submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };
        const fileProperties: FileProperties = {
            file: this.file,
            fileName: this.fileName(),
        };

        // Create upload progress callback
        const uploadProgressCallback = (progress: number, status: string) => {
            this.isUploading.set(true);
            this.uploadProgress.set(progress);
            this.uploadStatus.set(status);

            if (progress === 100) {
                if (this.uploadResetTimeoutId) {
                    clearTimeout(this.uploadResetTimeoutId);
                }
                // Reset upload state after completion
                this.uploadResetTimeoutId = window.setTimeout(() => {
                    this.isUploading.set(false);
                    this.uploadProgress.set(0);
                    this.uploadStatus.set('');
                    this.uploadResetTimeoutId = undefined;
                }, 1000);
            }
        };

        const videoFileProperties: VideoFileProperties = {
            videoFile: this.videoFile,
            videoFileName: this.videoFileName(),
        };

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
            videoFileProperties,
            uploadProgressCallback,
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
        if (formData?.videoFileProperties?.videoFile) {
            this.videoFile = formData?.videoFileProperties?.videoFile;
        }
        if (formData?.videoFileProperties?.videoFileName) {
            this.videoFileName.set(formData?.videoFileProperties?.videoFileName);
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
        const videoInfo = urlParser.parse(videoUrl);
        if (!videoInfo) {
            return videoUrl;
        }
        return urlParser.create({
            videoInfo,
            format: 'embed',
        });
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
