import { CommonModule } from '@angular/common';
import {
    Component,
    DestroyRef,
    ElementRef,
    HostListener,
    Injector,
    NgZone,
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
import Split from 'split.js';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import urlParser from 'js-video-url-parser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
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
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
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
import { LectureChatbotComponent } from 'app/iris/overview/lecture-chatbot/lecture-chatbot.component';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { LectureUnitFullscreenLayoutComponent } from 'app/lecture/shared/lecture-unit-fullscreen-layout/lecture-unit-fullscreen-layout.component';

type SplitterConfig = { direction: 'horizontal' | 'vertical'; sizes: [number, number]; minSizes: [number, number]; cursor: string; onDragEnd: (sizes: number[]) => void };

@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [
        CommonModule,
        LectureUnitComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TranslateDirective,
        SafeResourceUrlPipe,
        VideoPlayerComponent,
        PdfViewerComponent,
        MessageModule,
        LectureChatbotComponent,
        FaIconComponent,
        LectureUnitFullscreenLayoutComponent,
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
    private readonly ngZone = inject(NgZone);
    private readonly translateService = inject(TranslateService);
    private readonly themeService = inject(ThemeService);

    targetTimestamp = input<number | undefined>(undefined); // For video deeplinking
    targetPdfPage = input<number | undefined>(undefined); // For PDF deeplinking
    irisSettings = input<IrisCourseSettingsWithRateLimitDTO | undefined>(undefined);

    readonly lectureUnitCard = viewChild(LectureUnitComponent);
    readonly videoContainerElement = viewChild<ElementRef>('videoContainer');
    readonly pdfContainerElement = viewChild<ElementRef>('pdfContainer');

    private readonly _isFullscreen = signal<boolean>(false);
    private readonly _transcriptSegments = signal<TranscriptSegment[]>([]);
    private readonly _playlistUrl = signal<string | undefined>(undefined);
    private readonly _isLoading = signal<boolean>(false);
    private readonly _hasPdfFullscreen = signal<boolean>(false);

    readonly isFullscreen = this._isFullscreen.asReadonly();
    readonly transcriptSegments = this._transcriptSegments.asReadonly();
    readonly playlistUrl = this._playlistUrl.asReadonly();
    readonly isLoading = this._isLoading.asReadonly();
    readonly hasPdfFullscreen = this._hasPdfFullscreen.asReadonly();

    // Split panel sizes (percentage values)
    private readonly defaultSplitSizes: [number, number] = [50, 50];
    private readonly defaultThreePaneVerticalSplitSizes: [number, number] = [66.67, 33.33];
    private readonly _verticalSplitSizes = signal<[number, number]>([85, 15]); // [content, iris]
    private readonly _horizontalSplitSizes = signal<[number, number]>(this.defaultSplitSizes); // [video, pdf]
    readonly minVerticalSplitSizes: [number, number] = [120, 120];
    private readonly minHorizontalSplitSizes: [number, number] = [80, 80];

    readonly verticalSplitSizes = this._verticalSplitSizes.asReadonly();
    readonly horizontalSplitSizes = this._horizontalSplitSizes.asReadonly();

    private horizontalSplitInstance?: Split.Instance;
    private _previouslyFocusedElement: HTMLElement | null = null;

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

    readonly hasRenderableVideo = computed(() => !!this.videoUrl());

    readonly hasFullscreenContent = computed(() => this.hasRenderableVideo() || this.hasPdf());

    readonly lectureId = computed(() => this.lectureUnit().lecture?.id);

    readonly isCollapsed = computed(() => {
        const card = this.lectureUnitCard();
        return card ? card.isCollapsed() : true;
    });

    readonly showIrisSidebar = computed(() => this.isFullscreen() && this.shouldShowIrisSidebarInFullscreen());

    readonly needsHorizontalSplitter = computed(() => this.isFullscreen() && this.hasRenderableVideo() && this.hasPdf());

    readonly fullscreenAriaLabel = computed(() => {
        if (!this.isFullscreen()) {
            return undefined;
        }
        const unitName = this.lectureUnit().name ?? this.translateService.instant('artemisApp.lectureUnit.lectureUnit');
        return this.translateService.instant('artemisApp.lectureUnit.fullscreenView', { title: unitName });
    });

    // TODO: This must use a server configuration to make it compatible with deployments other than TUM
    private readonly videoUrlAllowList = [RegExp('^https://(?:live\\.rbg\\.tum\\.de|tum\\.live)/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1')];

    constructor() {
        super();

        // Update dark-mode class based on theme
        effect(() => {
            this.hostElement.nativeElement.classList.toggle('dark-mode', this.themeService.currentTheme() === Theme.DARK);
        });

        // Horizontal splitter lifecycle (video | pdf)
        effect(() => {
            const needs = this.needsHorizontalSplitter();
            const videoEl = this.videoContainerElement()?.nativeElement;
            const pdfEl = this.pdfContainerElement()?.nativeElement;

            untracked(() => {
                this.destroyHorizontalSplitter();

                if (needs && videoEl && pdfEl) {
                    this.ngZone.runOutsideAngular(() => {
                        this.initHorizontalSplitter([videoEl, pdfEl]);
                    });
                }
            });
        });
    }

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

    protected onPdfLoadError(event: { pdfUrl: string }): void {
        const failedUrl = event.pdfUrl;
        if (!this.matchesActivePdfUrl(failedUrl)) {
            return;
        }

        const activePdfUrl = this.pdfUrl()!;
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
        if (!this.matchesActivePdfUrl(event.pdfUrl)) {
            return;
        }

        this.isPdfLoading.set(false);
    }

    private matchesActivePdfUrl(candidateUrl: string | undefined): boolean {
        const activePdfUrl = this.pdfUrl();
        return !!candidateUrl && !!activePdfUrl && candidateUrl === activePdfUrl;
    }

    override toggleCollapse(isCollapsed: boolean): void {
        super.toggleCollapse(isCollapsed);

        if (!isCollapsed) {
            this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);
            this.triggerContentLoad();
        } else {
            this.cancelPdfLoad();
            this.isPdfLoading.set(false);
            this.clearPdfState();
        }
    }

    private triggerContentLoad(): void {
        // reset stale state
        this._transcriptSegments.set([]);
        this._playlistUrl.set(undefined);
        this._isLoading.set(true);

        const src = this.lectureUnit().videoSource;

        if (!src) {
            this._isLoading.set(false);
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
                        this._playlistUrl.set(resolvedUrl);
                        this.fetchTranscript();
                    }
                    this._isLoading.set(false);
                },
                error: () => {
                    // Failed to resolve playlist URL, will fall back to iframe
                    this._playlistUrl.set(undefined);
                    this._isLoading.set(false);
                },
            });
        if (this.hasPdf()) {
            this.loadPdf();
        }
    }

    private fetchTranscript(): void {
        const id = this.lectureUnit().id;
        if (id === undefined) {
            this._transcriptSegments.set([]);
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
                    this._transcriptSegments.set(segments);
                },
                error: () => {
                    // Failed to fetch transcript, video player will work without it
                    this._transcriptSegments.set([]);
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

    /**
     * Initializes a Split.js instance with shared defaults and stores size changes in signals.
     */
    private initSplitter(elements: HTMLElement[], config: SplitterConfig): Split.Instance {
        return Split(elements, {
            sizes: config.sizes,
            minSize: config.minSizes,
            gutterSize: 12,
            cursor: config.cursor,
            direction: config.direction,
            onDragEnd: (sizes) => {
                this.ngZone.run(() => {
                    config.onDragEnd(sizes);
                });
            },
            gutter: (_index, direction) => this.createSplitGutter(direction),
        });
    }

    private initHorizontalSplitter(elements: HTMLElement[]): void {
        this.horizontalSplitInstance = this.initSplitter(elements, {
            direction: 'vertical',
            sizes: this._horizontalSplitSizes(),
            minSizes: this.minHorizontalSplitSizes,
            cursor: 'row-resize',
            onDragEnd: (sizes) => {
                this._horizontalSplitSizes.set([sizes[0], sizes[1]]);
            },
        });
    }

    private createSplitGutter(direction: string): HTMLElement {
        const gutter = document.createElement('div');
        gutter.className = `gutter gutter-${direction}`;

        const handle = document.createElement('div');
        handle.className = 'split-gutter-handle';
        gutter.appendChild(handle);

        return gutter;
    }

    private destroyHorizontalSplitter(): void {
        this.horizontalSplitInstance?.destroy();
        this.horizontalSplitInstance = undefined;
    }

    ngOnDestroy(): void {
        this.destroyHorizontalSplitter();
        this.cancelPdfLoad();
        this.revokePdfUrl();
    }

    /**
     * Opens the lecture unit in fullscreen.
     * If the card is collapsed, it is expanded first so the fullscreen content can render.
     */
    openFullscreen(): void {
        if (!this.hasFullscreenContent() || this.isFullscreen()) {
            return;
        }

        // Capture currently focused element for restoration on close
        const activeEl = document.activeElement;
        if (activeEl instanceof HTMLElement) {
            this._previouslyFocusedElement = activeEl;
        }

        const card = this.lectureUnitCard();

        // Auto-expand if collapsed
        if (card && card.isCollapsed()) {
            // Trigger card expansion (this will automatically sync _isCollapsed via onCollapse event)
            card.toggleCollapse();

            // Wait for content to render before activating fullscreen
            afterNextRender(
                () => {
                    this.activateFullscreen();
                },
                { injector: this.injector },
            );
        } else {
            // Already expanded, show fullscreen immediately
            this.activateFullscreen();
        }
    }

    /**
     * Activates fullscreen state and moves focus into the fullscreen container.
     */
    private activateFullscreen(): void {
        this.resetSplitSizesForFullscreen();
        this._isFullscreen.set(true);
    }

    protected onVerticalSplitSizesChange(sizes: [number, number]): void {
        this._verticalSplitSizes.set(sizes);
    }

    private shouldShowIrisSidebarInFullscreen(): boolean {
        const settings = this.irisSettings();
        const lecId = this.lectureId();
        const isTutorial = this.lectureUnit().lecture?.isTutorialLecture;
        return !!settings?.settings?.enabled && lecId !== undefined && !isTutorial;
    }

    /**
     * Resets split panes to deterministic defaults when entering fullscreen.
     */
    private resetSplitSizesForFullscreen(): void {
        this._horizontalSplitSizes.set(this.defaultSplitSizes);

        const hasThreePaneLayout = this.shouldShowIrisSidebarInFullscreen() && this.hasRenderableVideo() && this.hasPdf();
        this._verticalSplitSizes.set(hasThreePaneLayout ? this.defaultThreePaneVerticalSplitSizes : this.defaultSplitSizes);
    }

    /**
     * Closes fullscreen and restores focus to the element that was focused before opening.
     */
    closeFullscreen(): void {
        if (!this.isFullscreen() || this.hasPdfFullscreen()) {
            return;
        }

        const elementToRestore = this._previouslyFocusedElement && document.contains(this._previouslyFocusedElement) ? this._previouslyFocusedElement : undefined;
        this._previouslyFocusedElement = null;
        this._isFullscreen.set(false);

        if (elementToRestore) {
            afterNextRender(
                () => {
                    elementToRestore.focus();
                },
                { injector: this.injector },
            );
        }
    }

    /**
     * Closes fullscreen on Escape unless the nested PDF viewer currently owns fullscreen.
     */
    @HostListener('document:keydown.escape', ['$event'])
    onEscapePressed(event: Event): void {
        if (!this.isFullscreen() || event.defaultPrevented || this.hasPdfFullscreen()) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        this.closeFullscreen();
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
        this._hasPdfFullscreen.set(isFullscreen);
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
    handleDownload(): void {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);
        this.downloadAttachment(this.getAttachmentLink());
    }

    private getAttachmentLink(): string | undefined {
        const attachment = this.lectureUnit().attachment;
        if (!attachment) {
            return undefined;
        }
        const link = attachment.studentVersion ?? (attachment.link ? this.fileService.createStudentLink(attachment.link) : undefined);
        return link ? addPublicFilePrefix(link) : undefined;
    }

    handleOriginalVersion(): void {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);
        const originalLink = this.lectureUnit().attachment?.link;
        this.downloadAttachment(originalLink ? addPublicFilePrefix(originalLink) : undefined);
    }

    private downloadAttachment(link: string | undefined): void {
        const attachmentName = this.lectureUnit().attachment?.name;
        if (!link || !attachmentName) {
            return;
        }
        this.fileService.downloadFileByAttachmentName(link, attachmentName);
        this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
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
