import { Component, DestroyRef, OnDestroy, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
import { YouTubePlayerComponent } from 'app/lecture/shared/youtube-player/youtube-player.component';
import { PdfViewerComponent } from 'app/lecture/shared/pdf-viewer/pdf-viewer.component';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { TranslateService } from '@ngx-translate/core';
import {
    faDownload,
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
} from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageModule } from 'primeng/message';
@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [LectureUnitComponent, ArtemisDatePipe, TranslateDirective, SafeResourceUrlPipe, VideoPlayerComponent, YouTubePlayerComponent, PdfViewerComponent, MessageModule],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> implements OnDestroy {
    protected readonly faDownload = faDownload;
    private readonly destroyRef = inject(DestroyRef);
    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly translateService = inject(TranslateService);

    targetTimestamp = input<number | undefined>(undefined); // For video deeplinking
    targetPdfPage = input<number | undefined>(undefined); // For PDF deeplinking

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    readonly rawVideoSource = computed(() => this.lectureUnit()?.videoSource ?? null);
    readonly youtubeVideoId = computed(() => this.lectureUnit()?.youtubeVideoId ?? null);
    readonly youtubePlayerFailed = signal(false);

    // Reset the fallback latch whenever the lecture unit changes (panel reopen, new
    // unit selected). Without this, one transient YouTube init failure sticks this
    // component instance on iframe fallback for its whole lifetime.
    private readonly _resetPlayerFailedOnUnitChange = effect(() => {
        const id = this.lectureUnit()?.id;
        // read id to create the dependency; then schedule reset
        void id;
        untracked(() => this.youtubePlayerFailed.set(false));
    });

    // Uses TranslateService.instant() for efficiency; trade-off: if the user
    // switches language mid-session, this value won't update until the lecture
    // unit input changes. Acceptable for error banners on unit-level components.
    readonly transcriptionErrorMessage = computed(() => {
        const code = this.lectureUnit()?.transcriptionErrorCode;
        if (!code) return null;
        const key =
            (
                {
                    YOUTUBE_PRIVATE: 'artemisApp.lectureUnit.video.transcription.error.private',
                    YOUTUBE_LIVE: 'artemisApp.lectureUnit.video.transcription.error.live',
                    YOUTUBE_TOO_LONG: 'artemisApp.lectureUnit.video.transcription.error.tooLong',
                    YOUTUBE_UNAVAILABLE: 'artemisApp.lectureUnit.video.transcription.error.unavailable',
                    YOUTUBE_DOWNLOAD_FAILED: 'artemisApp.lectureUnit.video.transcription.error.downloadFailed',
                } as Record<string, string>
            )[code] ?? 'artemisApp.lectureUnit.video.transcription.error.generic';
        return this.translateService.instant(key);
    });

    readonly pdfUrl = signal<string | undefined>(undefined);
    readonly isPdfLoading = signal<boolean>(false);
    readonly pdfLoadError = signal<boolean>(false);
    private readonly isBlobLoadInProgress = signal<boolean>(false);
    private blobLoadSubscription?: Subscription;

    readonly validatedPdfPage = computed(() => {
        const page = this.targetPdfPage();
        return page && Number.isInteger(page) && page > 0 ? page : undefined;
    });

    readonly showPdfSpinner = computed(() => this.isPdfLoading() && !!this.pdfUrl() && !this.pdfLoadError());

    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);

    readonly hasPdf = computed(() => {
        const attachment = this.lectureUnit().attachment;
        const candidate = attachment?.studentVersion ?? attachment?.link ?? attachment?.name;
        return this.hasAttachment() && candidate ? candidate.toLowerCase().endsWith('.pdf') : false;
    });

    protected onPdfLoadError(event: { pdfUrl: string }): void {
        const failedUrl = event.pdfUrl;
        const activePdfUrl = this.pdfUrl();

        if (!failedUrl || !activePdfUrl || failedUrl !== activePdfUrl) {
            return;
        }

        if (activePdfUrl?.startsWith('blob:')) {
            this.revokePdfUrl();
            this.pdfUrl.set(undefined);
            this.pdfLoadError.set(true);
            this.isPdfLoading.set(false);
            return;
        }

        if (this.isBlobLoadInProgress()) {
            return;
        }

        if (failedUrl !== this.getAttachmentLink()) {
            return;
        }

        this.loadPdfAsBlob();
    }

    protected onPdfPageRendered(event: { pdfUrl: string }): void {
        const loadedUrl = event.pdfUrl;
        const activePdfUrl = this.pdfUrl();

        if (!loadedUrl || !activePdfUrl || loadedUrl !== activePdfUrl) {
            return;
        }

        this.isPdfLoading.set(false);
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
                if (this.hasPdf()) {
                    this.loadPdf();
                }
                return;
            }

            // For YouTube sources, fetch transcript directly (no playlist URL needed)
            if (this.lectureUnit().youtubeVideoId) {
                this.fetchTranscript();
                this.isLoading.set(false);
                if (this.hasPdf()) {
                    this.loadPdf();
                }
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
            if (this.hasPdf()) {
                this.loadPdf();
            }
        } else {
            this.cancelPdfLoad();
            this.isPdfLoading.set(false);
            this.clearPdfState();
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

    /** Loads PDF via direct URL for streaming and HTTP caching. Falls back to blob on error. */
    private loadPdf(): void {
        this.isPdfLoading.set(true);
        this.pdfLoadError.set(false);

        const link = this.getAttachmentLink();

        if (!link) {
            this.pdfLoadError.set(true);
            this.isPdfLoading.set(false);
            return;
        }

        this.pdfUrl.set(link);
    }

    private loadPdfAsBlob(): void {
        this.cancelPdfLoad();
        this.isPdfLoading.set(true);
        this.isBlobLoadInProgress.set(true);

        const link = this.getAttachmentLink();
        if (!link) {
            this.pdfLoadError.set(true);
            this.isPdfLoading.set(false);
            this.isBlobLoadInProgress.set(false);
            return;
        }

        this.blobLoadSubscription = this.fileService
            .getBlobFromUrl(link)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (blob) => {
                    this.revokePdfUrl();
                    this.pdfUrl.set(URL.createObjectURL(blob));
                    this.pdfLoadError.set(false);
                    this.isBlobLoadInProgress.set(false);
                    this.blobLoadSubscription = undefined;
                },
                error: () => {
                    this.pdfUrl.set(undefined);
                    this.pdfLoadError.set(true);
                    this.isPdfLoading.set(false);
                    this.isBlobLoadInProgress.set(false);
                    this.blobLoadSubscription = undefined;
                },
            });
    }

    ngOnDestroy(): void {
        this.cancelPdfLoad();
        this.revokePdfUrl();
    }

    private cancelPdfLoad(): void {
        this.blobLoadSubscription?.unsubscribe();
        this.blobLoadSubscription = undefined;
        this.isBlobLoadInProgress.set(false);
    }

    private clearPdfState(): void {
        this.revokePdfUrl();
        this.pdfUrl.set(undefined);
        this.pdfLoadError.set(false);
    }

    private revokePdfUrl(): void {
        const url = this.pdfUrl();
        if (url && url.startsWith('blob:')) {
            URL.revokeObjectURL(url);
        }
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

    /** Downloads student version if available, otherwise instructor version. */
    handleDownload() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        const link = this.getAttachmentLink();

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    private getAttachmentLink(): string | undefined {
        const attachment = this.lectureUnit().attachment;
        if (!attachment) {
            return undefined;
        }
        const link = attachment.studentVersion ?? (attachment.link ? this.fileService.createStudentLink(attachment.link) : undefined);
        return link ? addPublicFilePrefix(link) : undefined;
    }

    handleOriginalVersion() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        const link = addPublicFilePrefix(this.lectureUnit().attachment!.link!);

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    onYouTubePlayerFailed(): void {
        this.youtubePlayerFailed.set(true);
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
