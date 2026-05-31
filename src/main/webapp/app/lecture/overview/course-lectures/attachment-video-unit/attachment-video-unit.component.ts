import {
    Component,
    DestroyRef,
    ElementRef,
    Injector,
    OnDestroy,
    ViewEncapsulation,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
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
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/foundation/pipes/safe-resource-url.pipe';
import { FileService } from 'app/foundation/service/file.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageModule } from 'primeng/message';
import { LectureChatbotComponent } from 'app/iris/overview/lecture-chatbot/lecture-chatbot.component';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { LectureUnitFullscreenLayoutComponent } from 'app/lecture/shared/lecture-unit-fullscreen-layout/lecture-unit-fullscreen-layout.component';
import { FormsModule } from '@angular/forms';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

type SplitSizes = [number, number];

@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [
        LectureUnitComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TranslateDirective,
        SafeResourceUrlPipe,
        VideoPlayerComponent,
        YouTubePlayerComponent,
        PdfViewerComponent,
        MessageModule,
        LectureChatbotComponent,
        FaIconComponent,
        LectureUnitFullscreenLayoutComponent,
        FormsModule,
        ToggleSwitchModule,
    ],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
    encapsulation: ViewEncapsulation.None,
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> implements OnDestroy {
    protected readonly faDownload = faDownload;
    protected readonly faXmark = faXmark;
    private readonly destroyRef = inject(DestroyRef);
    private readonly hostElement = inject(ElementRef<HTMLElement>);
    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly injector = inject(Injector);
    private readonly translateService = inject(TranslateService);
    private readonly themeService = inject(ThemeService);

    targetTimestamp = input<number | undefined>(undefined); // For video deeplinking
    targetPdfPage = input<number | undefined>(undefined); // For PDF deeplinking
    irisSettings = input<IrisCourseSettingsWithRateLimitDTO | undefined>(undefined);

    readonly lectureUnitCard = viewChild(LectureUnitComponent);
    readonly fullscreenLayout = viewChild(LectureUnitFullscreenLayoutComponent);
    readonly videoContainerElement = viewChild<ElementRef>('videoContainer');
    readonly pdfContainerElement = viewChild<ElementRef>('pdfContainer');
    readonly videoPlayer = viewChild(VideoPlayerComponent);
    readonly youtubePlayer = viewChild(YouTubePlayerComponent);
    readonly pdfViewer = viewChild(PdfViewerComponent);

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    readonly rawVideoSource = computed(() => this.lectureUnit()?.videoSource ?? null);
    readonly youtubeVideoId = computed(() => this.lectureUnit()?.youtubeVideoId ?? null);
    readonly youtubePlayerFailed = signal(false);

    // For iframe fallback: YouTube watch/share URLs cannot be framed, so we
    // construct a privacy-enhanced embed URL from the video ID when available.
    readonly iframeFallbackUrl = computed(() => {
        const id = this.youtubeVideoId();
        if (id) {
            return `https://www.youtube-nocookie.com/embed/${id}`;
        }
        return this.rawVideoSource();
    });

    private readonly pdfFullscreenState = signal<boolean>(false);
    readonly hasPdfFullscreen = this.pdfFullscreenState.asReadonly();

    private readonly fullscreenState = signal<boolean>(false);
    readonly isFullscreen = this.fullscreenState.asReadonly();

    // Split panel sizes (percentage values)
    readonly defaultVerticalSplitSizes: SplitSizes = [66.67, 33.33]; // [content, iris]
    readonly defaultHorizontalSplitSizes: SplitSizes = [50, 50]; // [video, pdf]
    private readonly verticalSplitSizesState = signal<SplitSizes>(this.defaultVerticalSplitSizes);
    private readonly horizontalSplitSizesState = signal<SplitSizes>(this.defaultHorizontalSplitSizes);
    readonly minVerticalSplitSizes: SplitSizes = [220, 220];
    readonly minHorizontalSplitSizes: SplitSizes = [140, 140];

    readonly verticalSplitSizes = this.verticalSplitSizesState.asReadonly();
    readonly horizontalSplitSizes = this.horizontalSplitSizesState.asReadonly();

    readonly pdfUrl = signal<string | undefined>(undefined);
    readonly isPdfLoading = signal<boolean>(false);
    readonly pdfLoadError = signal<boolean>(false);
    readonly synchronizeVideoAndSlides = signal(false);
    private readonly isBlobLoadInProgress = signal<boolean>(false);
    private blobLoadSubscription?: Subscription;
    private pendingPdfTargetPage?: number;
    private pendingVideoTargetSlideNumber?: number;

    readonly validatedPdfPage = computed(() => {
        const page = this.targetPdfPage();
        return page && Number.isInteger(page) && page > 0 ? page : undefined;
    });

    readonly showPdfSpinner = computed(() => this.isPdfLoading() && !!this.pdfUrl() && !this.pdfLoadError());

    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);
    readonly hasSyncCapableVideo = computed(() => !!this.playlistUrl() || (!!this.youtubeVideoId() && !this.youtubePlayerFailed()));
    readonly synchronizationState = computed(() => this.computeSynchronizationState());
    readonly synchronizationAvailable = computed(() => this.synchronizationState().available);

    readonly hasPdf = computed(() => {
        const attachment = this.lectureUnit().attachment;
        const candidate = attachment?.studentVersion ?? attachment?.link ?? attachment?.name;
        return this.hasAttachment() && candidate ? candidate.toLowerCase().endsWith('.pdf') : false;
    });

    readonly hasRenderableVideo = computed(() => !!this.rawVideoSource() || !!this.youtubeVideoId());

    readonly hasFullscreenContent = computed(() => (this.hasRenderableVideo() || this.hasPdf()) && this.shouldShowIrisSidebarInFullscreen());

    readonly lectureId = computed(() => this.lectureUnit().lecture?.id);

    readonly isCollapsed = computed(() => {
        const card = this.lectureUnitCard();
        return card ? card.isCollapsed() : true;
    });

    readonly showIrisSidebar = computed(() => this.isFullscreen() && this.shouldShowIrisSidebarInFullscreen());

    readonly verticalSplitConfig = computed(() => ({
        sizes: this.verticalSplitSizes(),
        minSizes: this.minVerticalSplitSizes,
        defaultSizes: this.defaultVerticalSplitSizes,
    }));

    readonly horizontalSplitConfig = computed(() => ({
        enabled: this.isFullscreen() && this.hasRenderableVideo() && this.hasPdf(),
        sizes: this.horizontalSplitSizes(),
        minSizes: this.minHorizontalSplitSizes,
        defaultSizes: this.defaultHorizontalSplitSizes,
        topElement: this.videoContainerElement(),
        bottomElement: this.pdfContainerElement(),
    }));

    readonly fullscreenAriaLabel = computed(() => {
        if (!this.isFullscreen()) {
            return undefined;
        }
        const unitName = this.lectureUnit().name ?? this.translateService.instant('artemisApp.lectureUnit.lectureUnit');
        return this.translateService.instant('artemisApp.lectureUnit.fullscreenView', { title: unitName });
    });

    readonly irisSidebarAriaLabel = computed(() => {
        return this.isFullscreen() ? this.translateService.instant('artemisApp.lectureUnit.irisSidebarLabel') : undefined;
    });

    readonly closeFullscreenAriaLabel = computed(() => {
        return this.isFullscreen() ? this.translateService.instant('artemisApp.lectureUnit.closeFullscreen') : undefined;
    });

    constructor() {
        super();

        // Reset the fallback latch whenever the lecture unit changes (panel reopen, new
        // unit selected). Without this, one transient YouTube init failure sticks this
        // component instance on iframe fallback for its whole lifetime.
        effect(() => {
            const id = this.lectureUnit()?.id;
            // read id to create the dependency; then schedule reset
            void id;
            untracked(() => this.youtubePlayerFailed.set(false));
        });

        // Update dark-mode class based on theme
        effect(() => {
            this.hostElement.nativeElement.classList.toggle('dark-mode', this.themeService.currentTheme() === Theme.DARK);
        });

        effect(() => {
            if (!this.synchronizationAvailable() && this.synchronizeVideoAndSlides()) {
                this.synchronizeVideoAndSlides.set(false);
                this.clearSynchronizationTargets();
            }
        });
    }

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
            this.youtubePlayerFailed.set(false);
            this.cancelPdfLoad();
            this.isPdfLoading.set(false);
            this.clearPdfState();
            this.synchronizeVideoAndSlides.set(false);
            this.clearSynchronizationTargets();
        }
    }

    private fetchTranscript(): void {
        const id = this.lectureUnit().id;
        if (id === undefined) {
            this.transcriptSegments.set([]);
            return;
        }

        this.lectureTranscriptionService
            .getTranscription(id)
            .pipe(
                map((dto) => dto?.segments?.filter((segment): segment is TranscriptSegment => this.isValidTranscriptSegment(segment)) ?? []),
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

    private isValidTranscriptSegment(segment: Partial<TranscriptSegment> | undefined): segment is TranscriptSegment {
        return !!segment && segment.startTime != null && segment.endTime != null && segment.text != null;
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

    /**
     * Opens the lecture unit in fullscreen.
     * If the card is collapsed, it is expanded first so the fullscreen content can render.
     */
    openFullscreen(): void {
        if (!this.hasFullscreenContent()) {
            return;
        }

        const card = this.lectureUnitCard();
        const layout = this.fullscreenLayout();

        if (!layout) {
            return;
        }

        // Auto-expand if collapsed
        if (card && card.isCollapsed()) {
            card.toggleCollapse();
            afterNextRender(
                () => {
                    // Re-check state before opening to prevent desync if user closed/re-toggled
                    if (layout && this.hasFullscreenContent() && card && !card.isCollapsed()) {
                        layout.open();
                    }
                },
                { injector: this.injector },
            );
        } else {
            layout.open();
        }
    }

    protected onVerticalSplitSizesChange(sizes: SplitSizes): void {
        this.verticalSplitSizesState.set(sizes);
    }

    protected onHorizontalSplitSizesChange(sizes: SplitSizes): void {
        this.horizontalSplitSizesState.set(sizes);
    }

    protected onFullscreenChange(isFullscreen: boolean): void {
        this.fullscreenState.set(isFullscreen);
    }

    protected onSynchronizationToggleChange(enabled: boolean): void {
        if (!enabled || !this.synchronizationAvailable()) {
            this.synchronizeVideoAndSlides.set(false);
            this.clearSynchronizationTargets();
            return;
        }

        this.synchronizeVideoAndSlides.set(true);
        this.synchronizeCurrentState();
    }

    protected onPdfCurrentPageChange(page: number): void {
        if (this.pendingPdfTargetPage === page) {
            this.pendingPdfTargetPage = undefined;
            return;
        }

        if (!this.synchronizeVideoAndSlides()) {
            return;
        }

        const displayedPageNumber = this.synchronizationState().pdfPageToDisplayedPageNumber.get(page);
        if (displayedPageNumber === undefined) {
            return;
        }

        this.seekVideoToDisplayedPageNumber(displayedPageNumber);
    }

    protected onVideoSlideNumberChange(slideNumber: number | undefined): void {
        if (slideNumber === undefined) {
            return;
        }

        if (this.pendingVideoTargetSlideNumber === slideNumber) {
            this.pendingVideoTargetSlideNumber = undefined;
            return;
        }

        if (!this.synchronizeVideoAndSlides()) {
            return;
        }

        const targetPdfPage = this.synchronizationState().displayedPageNumberToPdfPage.get(slideNumber);
        if (targetPdfPage === undefined || this.pdfViewer()?.getCurrentPage() === targetPdfPage) {
            return;
        }

        this.pendingPdfTargetPage = targetPdfPage;
        this.pdfViewer()?.goToPage(targetPdfPage);
    }

    private shouldShowIrisSidebarInFullscreen(): boolean {
        const settings = this.irisSettings();
        const lecId = this.lectureId();
        const isTutorial = this.lectureUnit().lecture?.isTutorialLecture;
        return !!settings?.settings?.enabled && lecId !== undefined && !isTutorial;
    }

    /**
     * Closes fullscreen.
     */
    closeFullscreen(): void {
        this.fullscreenLayout()?.close();
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
     * Tracks fullscreen state of the nested PDF viewer to avoid conflicting Escape handling.
     */
    protected onPdfFullscreenChange(isFullscreen: boolean): void {
        this.pdfFullscreenState.set(isFullscreen);
    }

    private synchronizeCurrentState(): void {
        const activeSlideNumber = this.getActiveVideoSlideNumber();
        if (activeSlideNumber !== undefined) {
            this.onVideoSlideNumberChange(activeSlideNumber);
            return;
        }

        const currentPdfPage = this.pdfViewer()?.getCurrentPage();
        if (currentPdfPage !== undefined) {
            this.onPdfCurrentPageChange(currentPdfPage);
        }
    }

    private seekVideoToDisplayedPageNumber(displayedPageNumber: number): void {
        const timestamp = this.synchronizationState().displayedPageNumberToVideoTimestamp.get(displayedPageNumber);
        if (timestamp === undefined) {
            return;
        }

        const shouldResumePlayback = this.isVideoCurrentlyPlaying();
        this.pendingVideoTargetSlideNumber = displayedPageNumber;

        const videoPlayer = this.videoPlayer();
        if (videoPlayer) {
            videoPlayer.seekTo(timestamp, shouldResumePlayback);
            return;
        }

        this.youtubePlayer()?.seekTo(timestamp, shouldResumePlayback);
    }

    private isVideoCurrentlyPlaying(): boolean {
        return this.videoPlayer()?.isPlaying() ?? this.youtubePlayer()?.isPlaying() ?? false;
    }

    private getActiveVideoSlideNumber(): number | undefined {
        return this.videoPlayer()?.getCurrentSlideNumber() ?? this.youtubePlayer()?.getCurrentSlideNumber();
    }

    private clearSynchronizationTargets(): void {
        this.pendingPdfTargetPage = undefined;
        this.pendingVideoTargetSlideNumber = undefined;
    }

    private computeSynchronizationState(): {
        available: boolean;
        displayedPageNumberToPdfPage: Map<number, number>;
        pdfPageToDisplayedPageNumber: Map<number, number>;
        displayedPageNumberToVideoTimestamp: Map<number, number>;
    } {
        const displayedPageNumbers = this.lectureUnit().attachment?.slidePageNumbers ?? [];
        const transcriptSegments = this.transcriptSegments();

        const displayedPageNumberToPdfPage = new Map<number, number>();
        const pdfPageToDisplayedPageNumber = new Map<number, number>();
        const displayedPageNumberToVideoTimestamp = new Map<number, number>();

        if (!this.hasPdf() || !this.hasTranscript() || !this.hasSyncCapableVideo() || displayedPageNumbers.length === 0) {
            return { available: false, displayedPageNumberToPdfPage, pdfPageToDisplayedPageNumber, displayedPageNumberToVideoTimestamp };
        }

        const distinctDisplayedPageNumbers = new Set<number>();
        for (const [index, displayedPageNumber] of displayedPageNumbers.entries()) {
            if (!Number.isInteger(displayedPageNumber) || distinctDisplayedPageNumbers.has(displayedPageNumber)) {
                return { available: false, displayedPageNumberToPdfPage, pdfPageToDisplayedPageNumber, displayedPageNumberToVideoTimestamp };
            }

            distinctDisplayedPageNumbers.add(displayedPageNumber);
            displayedPageNumberToPdfPage.set(displayedPageNumber, index + 1);
            pdfPageToDisplayedPageNumber.set(index + 1, displayedPageNumber);
        }

        let hasTranscriptSlideNumber = false;
        for (const segment of transcriptSegments) {
            const slideNumber = segment.slideNumber;
            if (slideNumber === undefined) {
                continue;
            }

            hasTranscriptSlideNumber = true;
            if (!displayedPageNumberToPdfPage.has(slideNumber)) {
                return { available: false, displayedPageNumberToPdfPage, pdfPageToDisplayedPageNumber, displayedPageNumberToVideoTimestamp };
            }

            if (!displayedPageNumberToVideoTimestamp.has(slideNumber)) {
                displayedPageNumberToVideoTimestamp.set(slideNumber, segment.startTime);
            }
        }

        if (!hasTranscriptSlideNumber || displayedPageNumberToVideoTimestamp.size === 0) {
            return { available: false, displayedPageNumberToPdfPage, pdfPageToDisplayedPageNumber, displayedPageNumberToVideoTimestamp };
        }

        return { available: true, displayedPageNumberToPdfPage, pdfPageToDisplayedPageNumber, displayedPageNumberToVideoTimestamp };
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
