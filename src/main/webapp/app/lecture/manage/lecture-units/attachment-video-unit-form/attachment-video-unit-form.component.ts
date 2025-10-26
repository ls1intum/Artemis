import { Component, ElementRef, OnChanges, ViewChild, computed, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faQuestionCircle, faTimes, faVideo } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { DecimalPipe } from '@angular/common';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
    playlistUrl?: string;
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
    generateTranscript?: boolean;
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

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    // Video upload constants
    protected readonly MAX_VIDEO_FILE_SIZE = 500 * 1024 * 1024; // 500 MB
    protected readonly ALLOWED_VIDEO_FORMATS = '.mp4';

    private readonly http = inject(HttpClient);
    private readonly lectureService = inject(LectureService);
    private readonly alertService = inject(AlertService);

    canGenerateTranscript = signal(false);
    playlistUrl = signal<string | undefined>(undefined);

    // Video upload state
    videoFile = signal<File | undefined>(undefined);
    videoFileName = signal<string | undefined>(undefined);
    isUploading = signal<boolean>(false);
    uploadProgress = signal<number>(0);
    uploadStatus = signal<string>('Uploading video...');
    uploadedVideoId = signal<string | undefined>(undefined);

    // Computed: has a video been uploaded?
    hasUploadedVideo = computed(() => !!this.uploadedVideoId());

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);
    lectureId = input<number | undefined>(undefined); // Required for video upload

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    file: File;
    fileInputTouched = false;

    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

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
        generateTranscript: [false],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');

    readonly videoSourceSignal = toSignal(this.videoSourceControl!.valueChanges, { initialValue: this.videoSourceControl!.value });

    readonly shouldShowTranscriptCheckbox = computed(() => !!this.playlistUrl());

    isFormValid = computed(() => {
        // Form is valid if:
        // 1. All form fields are valid
        // 2. File is not too big
        // 3. Date picker is valid
        // 4. AND one of the following:
        //    - Has an attachment file (fileName)
        //    - Has a video URL (videoSourceSignal)
        //    - Has uploaded a video to Nebula (uploadedVideoId)
        const hasAttachmentOrVideo = !!this.fileName() || !!this.videoSourceSignal() || !!this.uploadedVideoId();

        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.datePickerComponent()?.isValid() && hasAttachmentOrVideo;
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

    checkTumLivePlaylist(originalUrl: string): void {
        const parsedUrl = new URL(originalUrl);

        if (parsedUrl.host === 'live.rbg.tum.de') {
            this.http
                .get('api/nebula/video-utils/tum-live-playlist', {
                    params: { url: originalUrl },
                    responseType: 'text',
                })
                .subscribe({
                    next: (playlist) => {
                        this.canGenerateTranscript.set(true);
                        this.playlistUrl.set(playlist);
                    },
                    error: (error) => {
                        this.canGenerateTranscript.set(false);
                        this.playlistUrl.set(undefined);
                        this.form.get('generateTranscript')?.setValue(false);
                    },
                });
        } else {
            this.canGenerateTranscript.set(false);
            this.playlistUrl.set(undefined);
            this.form.get('generateTranscript')?.setValue(false);
        }
    }

    submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };

        formProperties.videoTranscription = undefined;
        const fileProperties: FileProperties = {
            file: this.file,
            fileName: this.fileName(),
        };
        const transcriptionProperties: TranscriptionProperties = {
            videoTranscription: formValue.videoTranscription,
        };

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
            transcriptionProperties,
            playlistUrl: this.playlistUrl(),
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

        const originalUrl = this.urlHelperControl!.value;
        const embeddedUrl = this.extractEmbeddedUrl(originalUrl);
        this.videoSourceControl!.setValue(embeddedUrl);

        this.checkTumLivePlaylist(originalUrl);
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

    cancelForm() {
        this.onCancel.emit();
    }

    /**
     * Check if user has entered a video URL
     */
    hasVideoUrl(): boolean {
        const videoSource = this.form.get('videoSource')?.value;
        return !!videoSource && videoSource.trim().length > 0;
    }

    /**
     * Handles video file selection for upload
     */
    onVideoUploadChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            this.videoFile.set(undefined);
            this.videoFileName.set(undefined);
            return;
        }

        const file = input.files[0];

        // Validate file extension - only .mp4
        if (!file.name.toLowerCase().endsWith('.mp4')) {
            this.alertService.error('artemisApp.lecture.video.invalidFormat');
            this.videoFile.set(undefined);
            this.videoFileName.set(undefined);
            input.value = '';
            return;
        }

        // Validate file type
        if (file.type !== 'video/mp4') {
            this.alertService.error('artemisApp.lecture.video.invalidFormat');
            this.videoFile.set(undefined);
            this.videoFileName.set(undefined);
            input.value = '';
            return;
        }

        this.videoFile.set(file);
        this.videoFileName.set(file.name);

        // Check file size (500 MB)
        if (file.size > this.MAX_VIDEO_FILE_SIZE) {
            this.alertService.error('artemisApp.lecture.video.fileTooLarge', { size: '500 MB' });
            this.videoFile.set(undefined);
            this.videoFileName.set(undefined);
            return;
        }

        // Upload video immediately to Nebula to avoid holding large files in browser memory
        // If user changes the file or cancels, we'll delete/abort the previous upload
        this.uploadVideoToStorage();
    }

    /**
     * Checks if the video file is too large
     */
    isVideoFileTooBig(): boolean {
        const file = this.videoFile();
        return file !== undefined && file.size > this.MAX_VIDEO_FILE_SIZE;
    }

    /**
     * Uploads the selected video to the Nebula Video Storage Service.
     * This happens immediately when file is selected to:
     * 1. Stream large files (up to 3GB) without holding in browser memory
     * 2. Start transcoding early while user fills out the form
     * 3. Show real-time upload progress
     *
     * If user changes the file or cancels, the previous upload can be deleted via the video_id.
     */
    private uploadVideoToStorage(): void {
        const file = this.videoFile();
        const currentLectureId = this.lectureId();

        if (!file || !currentLectureId) {
            this.alertService.error('artemisApp.lecture.video.uploadError');
            return;
        }

        // If there's a previous upload, we should delete it
        const previousVideoId = this.uploadedVideoId();
        if (previousVideoId) {
            // Delete the previous video from Nebula
            this.lectureService.deleteVideo(currentLectureId).subscribe({
                next: () => {
                    // Previous video deleted before uploading new one
                },
                error: () => {
                    // Failed to delete previous video, continuing with new upload
                },
            });
        }

        this.isUploading.set(true);
        this.uploadProgress.set(0);
        this.uploadStatus.set('Uploading video...');

        this.lectureService.uploadVideo(currentLectureId, file).subscribe({
            next: (event) => {
                if (event.type === HttpEventType.UploadProgress && event.total) {
                    // Update progress
                    const progress = Math.round((100 * event.loaded) / event.total);
                    this.uploadProgress.set(progress);

                    if (progress < 100) {
                        this.uploadStatus.set('Uploading video...');
                    } else {
                        this.uploadStatus.set('Processing video...');
                    }
                } else if (event.type === HttpEventType.Response && event.body) {
                    // Upload complete
                    this.uploadProgress.set(100);
                    this.uploadStatus.set('Upload complete!');
                    this.uploadedVideoId.set(event.body.videoId);
                    // Store the playlist URL for submission (but don't show it in the form yet)
                    if (event.body.playlistUrl) {
                        this.playlistUrl.set(event.body.playlistUrl);
                        this.canGenerateTranscript.set(true);
                        // Do NOT set videoSource here - it will be set on submit
                    }

                    this.alertService.success('artemisApp.lecture.video.uploadSuccess');
                    this.isUploading.set(false);
                }
            },
            error: (error) => {
                this.isUploading.set(false);
                this.uploadProgress.set(0);
                this.uploadedVideoId.set(undefined);
                this.videoFile.set(undefined);
                this.videoFileName.set(undefined);

                this.alertService.error(error?.error?.message || 'artemisApp.lecture.video.uploadError');
            },
        });
    }
}
