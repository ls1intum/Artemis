import { Component, ElementRef, OnChanges, ViewChild, computed, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faQuestionCircle, faTimes, faVideo } from '@fortawesome/free-solid-svg-icons';
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
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { DecimalPipe } from '@angular/common';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
    transcriptionProperties?: TranscriptionProperties;
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
    videoTranscription?: string;
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
}

export interface TranscriptionProperties {
    videoTranscription?: string;
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

function validJsonOrEmpty(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (value === undefined || value === null || value === '') {
        return null;
    }
    try {
        JSON.parse(value);
        return null;
    } catch {
        return { invalidJson: true };
    }
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
        DecimalPipe,
    ],
})
export class AttachmentVideoUnitFormComponent implements OnChanges {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faVideo = faVideo;
    private readonly http = inject(HttpClient);

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    file: File;
    fileInputTouched = false;

    @ViewChild('videoUploadInput', { static: false }) videoUploadInput: ElementRef;
    videoFile: File | undefined;

    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

    // Video upload progress tracking
    isUploading = signal<boolean>(false);
    uploadProgress = signal<number>(0);
    uploadStatus = signal<string>('');

    // Upload cancellation
    private uploadAbortController?: AbortController;
    private progressInterval?: NodeJS.Timeout;
    private transcodingInterval?: NodeJS.Timeout;
    private longUploadWarning?: NodeJS.Timeout;

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    private readonly formBuilder = inject(FormBuilder);
    private readonly accountService = inject(AccountService);

    readonly shouldShowTranscriptionCreation = computed(() => this.accountService.isAdmin());

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        version: [{ value: 1, disabled: true }],
        videoSource: [undefined as string | undefined, this.videoSourceUrlValidator],
        urlHelper: [undefined as string | undefined, this.videoSourceTransformUrlValidator],
        updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
        videoTranscription: [undefined as string | undefined, [validJsonOrEmpty]],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');

    readonly videoSourceSignal = toSignal(this.videoSourceControl!.valueChanges, { initialValue: this.videoSourceControl!.value });

    isFormValid = computed(() => {
        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.datePickerComponent()?.isValid() && (!!this.fileName() || !!this.videoSourceSignal());
    });

    ngOnChanges() {
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData()!);
        }
    }

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

    onVideoUploadChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }

        this.videoFile = input.files[0];
        this.fileName.set(this.videoFile.name);
        this.isFileTooBig.set(this.videoFile.size > MAX_VIDEO_FILE_SIZE); // Check against 5GB limit for videos
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

    get videoTranscriptionControl() {
        return this.form.get('videoTranscription');
    }

    async submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };

        formProperties.videoTranscription = undefined;
        const fileProperties: FileProperties = {
            file: this.videoFile || this.file,
            fileName: this.fileName(),
        };
        const transcriptionProperties: TranscriptionProperties = {
            videoTranscription: formValue.videoTranscription,
        };

        // If a video file was uploaded, call backend API to upload and get playlist URL
        if (this.videoFile) {
            const formData = new FormData();
            formData.append('file', this.videoFile);

            // Start upload progress tracking
            this.isUploading.set(true);
            this.uploadProgress.set(0);
            this.uploadStatus.set('Uploading video...');

            // Create abort controller for cancellation
            this.uploadAbortController = new AbortController();

            // Show warning message if upload takes too long (5 minutes)
            this.longUploadWarning = setTimeout(
                () => {
                    if (this.isUploading()) {
                        this.uploadStatus.set('Large video detected - this may take several minutes to upload and process...');
                    }
                },
                5 * 60 * 1000,
            ); // 5 minutes

            try {
                // Simulate progress updates during upload
                this.progressInterval = setInterval(() => {
                    const current = this.uploadProgress();
                    if (current < 50) {
                        this.uploadProgress.set(current + Math.random() * 10);
                    }
                }, 500);

                // Add a timeout wrapper for the HTTP request (12 minutes = 720 seconds)
                const uploadPromise = lastValueFrom(
                    this.http.post('/api/videos/upload', formData, {
                        responseType: 'text',
                        // Note: AbortController signal for request cancellation
                        // context: this.uploadAbortController.signal
                    }),
                );

                // Create a timeout promise
                const timeoutPromise = new Promise<never>((_, reject) => {
                    setTimeout(
                        () => {
                            reject(new Error('Upload timeout - please try with a smaller video file'));
                        },
                        12 * 60 * 1000,
                    ); // 12 minutes timeout
                });

                // Race between upload and timeout
                const responseString = await Promise.race([uploadPromise, timeoutPromise]);

                // Update progress for transcoding phase
                if (this.progressInterval) {
                    clearInterval(this.progressInterval);
                }
                this.uploadProgress.set(60);
                this.uploadStatus.set('Processing and converting video...');

                // Simulate transcoding progress
                this.transcodingInterval = setInterval(() => {
                    const current = this.uploadProgress();
                    if (current < 95) {
                        this.uploadProgress.set(current + Math.random() * 5);
                    }
                }, 300);

                // The video storage service now returns the playlist URL directly
                // No need to parse JSON or generate URL
                const playlistUrl = responseString;
                formProperties.videoSource = playlistUrl;

                // Complete progress
                if (this.transcodingInterval) {
                    clearInterval(this.transcodingInterval);
                }
                if (this.longUploadWarning) {
                    clearTimeout(this.longUploadWarning);
                }
                this.uploadProgress.set(100);
                this.uploadStatus.set('Video uploaded successfully!');

                // Reset after short delay
                setTimeout(() => {
                    this.isUploading.set(false);
                    this.uploadProgress.set(0);
                    this.uploadStatus.set('');
                }, 1000);
            } catch (error) {
                // Clear any running intervals and timeouts
                if (this.progressInterval) {
                    clearInterval(this.progressInterval);
                }
                if (this.transcodingInterval) {
                    clearInterval(this.transcodingInterval);
                }
                if (this.longUploadWarning) {
                    clearTimeout(this.longUploadWarning);
                }

                // eslint-disable-next-line no-undef
                console.error('Video upload failed:', error);
                this.isUploading.set(false);
                this.uploadProgress.set(0);

                // Provide more specific error messages
                if (error.name === 'AbortError' || error.message === 'The operation was aborted.') {
                    this.uploadStatus.set('Upload cancelled by user');
                    // Clear after shorter time for cancellation
                    setTimeout(() => {
                        this.uploadStatus.set('');
                    }, 2000);
                    return; // Don't show the longer timeout for cancellation
                } else if (error.message && error.message.includes('timeout')) {
                    this.uploadStatus.set('Upload timed out. Please try with a smaller video file or check your connection.');
                } else if (error.status === 0) {
                    this.uploadStatus.set('Server unreachable. Please check your connection and try again.');
                } else if (error.status === 413) {
                    this.uploadStatus.set(`Video file too large for server (max ${MAX_VIDEO_FILE_SIZE / (1024 * 1024 * 1024)}GB).`);
                } else if (error.status === 408) {
                    this.uploadStatus.set('Request timed out. Please try with a smaller video file.');
                } else if (error.status >= 500) {
                    this.uploadStatus.set('Server error occurred during upload.');
                } else if (error.name === 'TimeoutError' || (error.message && error.message.toLowerCase().includes('timeout'))) {
                    this.uploadStatus.set('Upload timed out after 12 minutes. Please try with a smaller video file.');
                } else {
                    this.uploadStatus.set('Upload failed! Please try again.');
                }

                // Clear error message after 8 seconds (longer for timeout messages)
                setTimeout(() => {
                    this.uploadStatus.set('');
                }, 8000);
                return;
            }
        }

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
            transcriptionProperties,
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
        if (formData?.transcriptionProperties) {
            this.form.patchValue(formData.transcriptionProperties);
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
        const embeddedUrl = this.extractEmbeddedUrl(this.urlHelperControl!.value);
        this.videoSourceControl!.setValue(embeddedUrl);
    }

    extractEmbeddedUrl(videoUrl: string) {
        const url = new URL(videoUrl);
        if (isTumLiveUrl(url)) {
            url.searchParams.set('video_only', '1');
            return url.toString();
        }
        return urlParser.create({
            videoInfo: urlParser.parse(videoUrl)!,
            format: 'embed',
        });
    }

    cancelUpload() {
        // Cancel the upload request
        if (this.uploadAbortController) {
            this.uploadAbortController.abort();
        }

        // Clear any running intervals and timeouts
        if (this.progressInterval) {
            clearInterval(this.progressInterval);
        }
        if (this.transcodingInterval) {
            clearInterval(this.transcodingInterval);
        }
        if (this.longUploadWarning) {
            clearTimeout(this.longUploadWarning);
        }

        // Reset upload state
        this.isUploading.set(false);
        this.uploadProgress.set(0);
        this.uploadStatus.set('Upload cancelled');

        // Clear the status message after 3 seconds
        setTimeout(() => {
            this.uploadStatus.set('');
        }, 3000);

        // Clear the video file
        this.videoFile = undefined;
        this.fileName.set(undefined);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
