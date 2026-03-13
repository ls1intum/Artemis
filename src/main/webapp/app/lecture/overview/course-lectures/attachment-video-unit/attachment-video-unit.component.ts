import { Component, ElementRef, OnDestroy, computed, inject, input, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import urlParser from 'js-video-url-parser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
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
    faRotateLeft,
    faSearchMinus,
    faSearchPlus,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PdfViewerComponent, PdfViewerModule } from 'ng2-pdf-viewer';
import { ButtonModule } from 'primeng/button';

type PdfDocumentProxyLike = {
    numPages?: number | null;
};
@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [
        LectureUnitComponent,
        TranslateDirective,
        SafeResourceUrlPipe,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        VideoPlayerComponent,
        FaIconComponent,
        PdfViewerModule,
        ButtonModule,
    ],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> implements OnDestroy {
    protected readonly faDownload = faDownload;
    protected readonly faFileLines = faFileLines;
    protected readonly faSearchMinus = faSearchMinus;
    protected readonly faSearchPlus = faSearchPlus;
    protected readonly faRotateLeft = faRotateLeft;

    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly httpClient = inject(HttpClient);

    targetPdfPage = input<number | undefined>(undefined);

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    readonly pdfUrl = signal<string | undefined>(undefined);
    readonly isPdfLoading = signal<boolean>(false);
    readonly pdfLoadError = signal<boolean>(false);
    readonly pdfZoom = signal<number>(1.0);

    private pdfViewer = viewChild(PdfViewerComponent);
    private pdfViewerBox = viewChild<ElementRef<HTMLDivElement>>('pdfViewerBox');

    private playlistSubscription?: Subscription;
    private transcriptSubscription?: Subscription;
    private pdfSubscription?: Subscription;
    private pdfPage = 1;
    private pdfTotalPages = 0;
    readonly pdfMinZoom = 0.5;
    readonly pdfMaxZoom = 3.0;
    readonly pdfZoomStep = 0.25;

    private resizeObserver?: ResizeObserver;
    private pendingScrollRestore?: { xRatio: number; yRatio: number };
    private lastViewportScale = 1;
    private resizeDebounceTimer?: number;
    private lastCapturedSnapshot?: { xRatio: number; yRatio: number };

    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);

    readonly hasPdf = computed(() => {
        const attachment = this.lectureUnit().attachment;
        const candidate = attachment?.studentVersion ?? attachment?.link ?? attachment?.name;
        return this.hasAttachment() && candidate ? candidate.toLowerCase().endsWith('.pdf') : false;
    });

    // TODO: This must use a server configuration to make it compatible with deployments other than TUM
    private readonly videoUrlAllowList = [RegExp('^https://(?:live\\.rbg\\.tum\\.de|tum\\.live)/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1')];

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

    constructor() {
        super();
        this.ensurePdfWorkerSrc();
    }

    private logUnitOpenedEvent(): void {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);
    }

    private clearLoadedContent(): void {
        this.transcriptSegments.set([]);
        this.playlistUrl.set(undefined);
        this.revokePdfUrl();
        this.pdfUrl.set(undefined);
        this.pdfLoadError.set(false);
        this.pdfPage = this.targetPdfPage() ?? 1;
        this.pdfTotalPages = 0;
        this.pdfZoom.set(1.0);
    }

    override toggleCollapse(isCollapsed: boolean): void {
        super.toggleCollapse(isCollapsed);

        if (!isCollapsed) {
            this.logUnitOpenedEvent();
            this.cancelPendingLoads();
            this.clearLoadedContent();

            const src = this.lectureUnit().videoSource;

            if (src) {
                this.isLoading.set(true);

                this.playlistSubscription = this.attachmentVideoUnitService.getPlaylistUrl(src).subscribe({
                    next: (resolvedUrl) => {
                        if (resolvedUrl) {
                            this.playlistUrl.set(resolvedUrl);
                            this.fetchTranscript();
                        }
                        this.isLoading.set(false);
                    },
                    error: () => {
                        this.playlistUrl.set(undefined);
                        this.isLoading.set(false);
                    },
                });
            } else {
                this.isLoading.set(false);
            }

            if (this.hasPdf()) {
                this.loadPdf();
            }
        } else {
            // Clear loaded content and reset state
            this.cancelPendingLoads();
            this.clearLoadedContent();
            this.isLoading.set(false);
            this.isPdfLoading.set(false);
            this.cleanupResizeObserver();
        }
    }

    private fetchTranscript(): void {
        const id = this.lectureUnit().id!;

        this.transcriptSubscription = this.lectureTranscriptionService
            .getTranscription(id)
            .pipe(
                map((dto) => {
                    if (!dto || !dto.segments) {
                        return [];
                    }
                    // Filter segments with required fields
                    return dto.segments.filter((seg): seg is TranscriptSegment => seg.startTime != null && seg.endTime != null && seg.text != null) as TranscriptSegment[];
                }),
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

    private loadPdf(): void {
        this.isPdfLoading.set(true);
        this.pdfLoadError.set(false);

        const link = this.getAttachmentLink();

        if (!link) {
            this.pdfLoadError.set(true);
            this.isPdfLoading.set(false);
            return;
        }

        this.pdfSubscription = this.httpClient.get(link, { responseType: 'blob' }).subscribe({
            next: (blob) => {
                if (blob) {
                    this.revokePdfUrl();
                    this.pdfUrl.set(URL.createObjectURL(blob));
                    this.pdfLoadError.set(false);
                    this.pdfPage = this.targetPdfPage() ?? 1;
                }
                this.isPdfLoading.set(false);
            },
            error: () => {
                this.pdfUrl.set(undefined);
                this.pdfLoadError.set(true);
                this.isPdfLoading.set(false);
            },
        });
    }

    ngOnDestroy(): void {
        this.cancelPendingLoads();
        this.revokePdfUrl();
        this.cleanupResizeObserver();
    }

    private revokePdfUrl(): void {
        const url = this.pdfUrl();
        if (url) {
            URL.revokeObjectURL(url);
        }
    }

    private cancelPendingLoads(): void {
        this.playlistSubscription?.unsubscribe();
        this.playlistSubscription = undefined;
        this.transcriptSubscription?.unsubscribe();
        this.transcriptSubscription = undefined;
        this.pdfSubscription?.unsubscribe();
        this.pdfSubscription = undefined;
    }

    onPdfLoaded(pdf: PdfDocumentProxyLike): void {
        this.pdfTotalPages = pdf.numPages ?? 0;
        if (this.pdfPage < 1) {
            this.pdfPage = 1;
        }
        if (this.pdfTotalPages > 0 && this.pdfPage > this.pdfTotalPages) {
            this.pdfPage = this.pdfTotalPages;
        }
        this.setupResizeObserver();
    }

    onPdfError(): void {
        this.pdfLoadError.set(true);
    }

    zoomIn(): void {
        this.setZoom(this.pdfZoom() + this.pdfZoomStep);
    }

    zoomOut(): void {
        this.setZoom(this.pdfZoom() - this.pdfZoomStep);
    }

    resetZoom(): void {
        this.setZoom(1.0);
    }

    private setZoom(nextZoom: number): void {
        const clamped = Math.max(this.pdfMinZoom, Math.min(this.pdfMaxZoom, nextZoom));
        if (clamped === this.pdfZoom()) {
            return;
        }
        const scrollSnapshot = this.capturePdfScrollCenter();
        this.pdfZoom.set(clamped);
        if (scrollSnapshot) {
            this.restorePdfScrollCenter(scrollSnapshot);
        }
    }

    private capturePdfScrollCenter(): { xRatio: number; yRatio: number } | undefined {
        const container = this.getPdfScrollContainer();
        if (!container) {
            return undefined;
        }
        const { scrollWidth, scrollHeight, scrollLeft, scrollTop, clientWidth, clientHeight } = container;
        if (scrollWidth <= 0 || scrollHeight <= 0) {
            return undefined;
        }
        const centerX = scrollLeft + clientWidth / 2;
        const centerY = scrollTop + clientHeight / 2;
        return {
            xRatio: centerX / scrollWidth,
            yRatio: centerY / scrollHeight,
        };
    }

    private restorePdfScrollCenter(snapshot: { xRatio: number; yRatio: number }): void {
        const container = this.getPdfScrollContainer();
        if (!container) {
            return;
        }
        const apply = () => {
            const { scrollWidth, scrollHeight, clientWidth, clientHeight } = container;
            if (scrollWidth <= 0 || scrollHeight <= 0) {
                return;
            }
            const centerX = snapshot.xRatio * scrollWidth;
            const centerY = snapshot.yRatio * scrollHeight;
            container.scrollLeft = Math.max(0, centerX - clientWidth / 2);
            container.scrollTop = Math.max(0, centerY - clientHeight / 2);
        };
        requestAnimationFrame(() => requestAnimationFrame(apply));
    }

    private getPdfScrollContainer(): HTMLElement | undefined {
        const viewer = this.pdfViewer();
        const viewerContainer = viewer?.pdfViewerContainer?.nativeElement;
        return viewerContainer ?? this.pdfViewerBox()?.nativeElement;
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
        this.logUnitOpenedEvent();

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
        this.logUnitOpenedEvent();

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

    get pdfPageCount(): number {
        return this.pdfTotalPages;
    }

    get currentPdfPage(): number {
        return this.pdfPage;
    }

    set currentPdfPage(page: number) {
        this.pdfPage = page;
    }

    private setupResizeObserver(): void {
        this.cleanupResizeObserver();

        const container = this.getPdfScrollContainer();
        if (!container) {
            return;
        }

        // Initialize viewport scale
        this.lastViewportScale = window.visualViewport?.scale ?? 1;

        this.resizeObserver = new ResizeObserver(() => {
            const currentScale = window.visualViewport?.scale ?? 1;
            const isBrowserZoom = currentScale !== this.lastViewportScale;
            this.lastViewportScale = currentScale;

            // Only restore scroll position for layout changes, not browser zoom
            if (!isBrowserZoom) {
                // Capture snapshot immediately (before resize animation)
                if (!this.lastCapturedSnapshot) {
                    this.lastCapturedSnapshot = this.capturePdfScrollCenter();
                }

                // Clear existing debounce timer
                if (this.resizeDebounceTimer) {
                    clearTimeout(this.resizeDebounceTimer);
                }

                // Wait for resize animation to finish (150ms debounce)
                this.resizeDebounceTimer = window.setTimeout(() => {
                    if (this.lastCapturedSnapshot) {
                        this.pendingScrollRestore = this.lastCapturedSnapshot;
                        this.attemptScrollRestore();
                        this.lastCapturedSnapshot = undefined;
                    }
                    this.resizeDebounceTimer = undefined;
                }, 150);
            }
        });

        this.resizeObserver.observe(container);
    }

    private cleanupResizeObserver(): void {
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
            this.resizeObserver = undefined;
        }
        if (this.resizeDebounceTimer) {
            clearTimeout(this.resizeDebounceTimer);
            this.resizeDebounceTimer = undefined;
        }
        this.pendingScrollRestore = undefined;
        this.lastCapturedSnapshot = undefined;
    }

    private attemptScrollRestore(): void {
        if (!this.pendingScrollRestore) {
            return;
        }

        const snapshot = this.pendingScrollRestore;
        const maxAttempts = 30;
        let attempts = 0;

        const tryRestore = () => {
            attempts++;
            const container = this.getPdfScrollContainer();
            if (!container) {
                this.pendingScrollRestore = undefined;
                return;
            }

            const { scrollWidth, scrollHeight } = container;
            if (scrollWidth > 0 && scrollHeight > 0) {
                // PDF has rendered, restore scroll position
                const centerX = snapshot.xRatio * scrollWidth;
                const centerY = snapshot.yRatio * scrollHeight;
                container.scrollLeft = Math.max(0, centerX - container.clientWidth / 2);
                container.scrollTop = Math.max(0, centerY - container.clientHeight / 2);
                this.pendingScrollRestore = undefined;
            } else if (attempts < maxAttempts) {
                // PDF not ready yet, try again
                requestAnimationFrame(tryRestore);
            } else {
                // Give up after max attempts
                this.pendingScrollRestore = undefined;
            }
        };

        requestAnimationFrame(tryRestore);
    }

    private ensurePdfWorkerSrc(): void {
        if (typeof window === 'undefined') {
            return;
        }
        const globalWindow = window as typeof window & { pdfWorkerSrc?: string };
        if (!globalWindow.pdfWorkerSrc) {
            globalWindow.pdfWorkerSrc = '/content/scripts/pdf.worker.ng2.min.mjs';
        }
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
