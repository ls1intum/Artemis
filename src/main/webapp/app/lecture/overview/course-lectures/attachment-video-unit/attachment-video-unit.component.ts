import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import urlParser from 'js-video-url-parser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { firstValueFrom } from 'rxjs';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import {
    faDownload,
    faExclamationTriangle,
    faFile,
    faFileArchive,
    faFileCode,
    faFileCsv,
    faFileExcel,
    faFileImage,
    faFileLines,
    faFilePdf,
    faFilePen,
    faFilePowerpoint,
    faFileVideo,
    faFileWord,
    faSpinner,
} from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { map } from 'rxjs/operators';

@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [LectureUnitComponent, ArtemisDatePipe, TranslateDirective, SafeResourceUrlPipe, FaIconComponent, VideoPlayerComponent],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> implements OnInit {
    protected readonly faDownload = faDownload;
    protected readonly faFileLines = faFileLines;
    protected readonly faSpinner = faSpinner;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly TranscriptionStatus = TranscriptionStatus;

    private readonly destroyRef = inject(DestroyRef);
    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly accountService = inject(AccountService);

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);
    transcriptionStatus = signal<TranscriptionStatus | undefined>(undefined);
    isLoadingTranscriptionStatus = signal(false);

    /**
     * Determines whether the "transcription possible" badge should be displayed.
     *
     * The badge is only shown when:
     * - A playlist URL is available (transcription can be generated)
     * - No transcription job exists (no transcriptionProperties, meaning no jobId)
     * - No transcription status exists (no transcriptionProperties)
     * - No completed transcript is available yet
     *
     * @returns {boolean} True if the badge should be shown (no active transcription job and no transcript), false otherwise
     */
    readonly canShowTranscriptionPossibleBadge = computed(() => {
        // Don't show badge if transcription was created (which would have a jobId)
        if (this.lectureUnit().transcriptionProperties) {
            return false;
        }
        // Don't show badge if transcript is already available
        return !this.hasTranscript();
    });

    // TODO: This must use a server configuration to make it compatible with deployments other than TUM
    private readonly videoUrlAllowList = [RegExp('^https://(?:live\\.rbg\\.tum\\.de|tum\\.live)/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1')];

    ngOnInit() {
        // Load transcription status if user is instructor or admin and video source exists
        if ((this.accountService.isAtLeastInstructorInCourse(this.lectureUnit().lecture?.course) || this.accountService.isAdmin()) && this.hasVideo() && this.lectureUnit().id) {
            this.loadTranscriptionStatus();
        }
    }

    async loadTranscriptionStatus() {
        const id = this.lectureUnit().id;
        if (!id) {
            return;
        }

        this.isLoadingTranscriptionStatus.set(true);
        try {
            this.transcriptionStatus.set(await firstValueFrom(this.lectureTranscriptionService.getTranscriptionStatus(id)));
        } catch {
            // Error handling - status remains undefined
        } finally {
            this.isLoadingTranscriptionStatus.set(false);
        }
    }

    readonly shouldShowTranscriptionStatus = computed(() => {
        const isLoading = this.isLoadingTranscriptionStatus();
        const status = this.transcriptionStatus();
        return !isLoading && !!status && (status === TranscriptionStatus.PENDING || status === TranscriptionStatus.PROCESSING || status === TranscriptionStatus.FAILED);
    });

    /**
     * Return the URL of the video source
     */
    readonly videoUrl = computed(() => this.computeVideoUrl());

    /**
     * Computes the video URL based on the video source.
     * Returns undefined if the source is invalid or doesn't match the allow list.
     */
    private computeVideoUrl(): string | undefined {
        const source = this.lectureUnit().videoSource;
        if (!source) {
            return undefined;
        }
        // Check if it matches the allow list (e.g., TUM Live URLs)
        if (this.videoUrlAllowList.some((r) => r.test(source))) {
            return source;
        }
        // Check if urlParser can parse it (e.g., YouTube, Vimeo, etc.)
        if (urlParser) {
            const parsed = urlParser.parse(source);
            if (parsed) {
                return source;
            }
        }
        return undefined;
    }

    override toggleCollapse(isCollapsed: boolean): void {
        super.toggleCollapse(isCollapsed);

        if (!isCollapsed) {
            this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

            // reset stale state
            this.transcriptSegments.set([]);
            this.playlistUrl.set(undefined);
            this.isLoading.set(true);

            const src = this.lectureUnit().videoSource;

            if (!src) {
                this.isLoading.set(false);
                return;
            }

            // Try to resolve a .m3u8 playlist URL from the server
            this.attachmentVideoUnitService
                .getPlaylistUrl(src)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: (resolvedUrl) => {
                        if (resolvedUrl) {
                            this.playlistUrl.set(resolvedUrl);
                            this.fetchTranscript();
                        }
                        this.isLoading.set(false);
                    },
                    error: () => {
                        // Failed to resolve playlist URL, will fall back to iframe
                        this.playlistUrl.set(undefined);
                        this.isLoading.set(false);
                    },
                });
        }
    }

    private fetchTranscript(): void {
        const id = this.lectureUnit().id!;

        this.lectureTranscriptionService
            .getTranscription(id)
            .pipe(
                map((dto) => {
                    if (!dto || !dto.segments) {
                        return [];
                    }
                    // Filter and map to ensure all required fields are present
                    return dto.segments.filter((seg): seg is TranscriptSegment => seg.startTime != null && seg.endTime != null && seg.text != null) as TranscriptSegment[];
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (segments) => {
                    this.transcriptSegments.set(segments);
                },
                error: () => {
                    // Failed to fetch transcript, video player will work without it
                    this.transcriptSegments.set([]);
                },
            });
    }

    /**
     * Returns the name of the attachment file (including its file extension)
     */
    getFileName(): string {
        if (this.lectureUnit().attachment?.link) {
            const link = this.lectureUnit().attachment!.link!;
            const filename = link.substring(link.lastIndexOf('/') + 1);
            return this.fileService.replaceAttachmentPrefixAndUnderscores(filename);
        }
        return '';
    }

    /**
     * Downloads the file as the student version if available, otherwise the instructor version
     * If it is not the student view, it always downloads the original version
     */
    handleDownload() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        // Determine the link based on the availability of a student version
        const link = addPublicFilePrefix(this.lectureUnit().attachment!.studentVersion || this.fileService.createStudentLink(this.lectureUnit().attachment!.link!));

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    handleOriginalVersion() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        const link = addPublicFilePrefix(this.lectureUnit().attachment!.link!);

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    hasAttachment(): boolean {
        return !!this.lectureUnit().attachment;
    }

    hasVideo(): boolean {
        return !!this.lectureUnit().videoSource;
    }

    /**
     * Returns the matching icon for the file extension of the attachment
     */
    getAttachmentIcon(): IconDefinition {
        if (this.hasVideo()) {
            return faFileVideo;
        }

        if (this.lectureUnit().attachment?.link) {
            const fileExtension = this.lectureUnit().attachment?.link?.split('.').pop()!.toLocaleLowerCase();
            switch (fileExtension) {
                case 'png':
                case 'jpg':
                case 'jpeg':
                case 'gif':
                case 'svg':
                    return faFileImage;
                case 'pdf':
                    return faFilePdf;
                case 'zip':
                case 'tar':
                    return faFileArchive;
                case 'txt':
                case 'rtf':
                case 'md':
                    return faFileLines;
                case 'htm':
                case 'html':
                case 'json':
                    return faFileCode;
                case 'doc':
                case 'docx':
                case 'pages':
                case 'pages-tef':
                case 'odt':
                    return faFileWord;
                case 'csv':
                    return faFileCsv;
                case 'xls':
                case 'xlsx':
                case 'numbers':
                case 'ods':
                    return faFileExcel;
                case 'ppt':
                case 'pptx':
                case 'key':
                case 'odp':
                    return faFilePowerpoint;
                case 'odg':
                case 'odc':
                case 'odi':
                case 'odf':
                    return faFilePen;
                default:
                    return faFile;
            }
        }
        return faFile;
    }
}
