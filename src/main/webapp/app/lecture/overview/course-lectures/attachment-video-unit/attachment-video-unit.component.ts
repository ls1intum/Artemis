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

    targetTimestamp = input<number | undefined>(undefined); // For video deeplinking
    targetPdfPage = input<number | undefined>(undefined); // For PDF deeplinking
    irisSettings = input<IrisCourseSettingsWithRateLimitDTO | undefined>(undefined);

    readonly contentContainer = viewChild<ElementRef>('contentContainer');
    readonly lectureUnitCard = viewChild(LectureUnitComponent);
    readonly mainContentElement = viewChild<ElementRef>('mainContent');
    readonly irisSidebarElement = viewChild<ElementRef>('irisSidebar');
    readonly videoContainerElement = viewChild<ElementRef>('videoContainer');
    readonly pdfContainerElement = viewChild<ElementRef>('pdfContainer');

    readonly isFullscreen = signal<boolean>(false);
    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    // Split panel sizes (percentage values)
    private readonly _verticalSplitSizes = signal<[number, number]>([85, 15]); // [content, iris]
    private readonly _horizontalSplitSizes = signal<[number, number]>([50, 50]); // [video, pdf]
    private readonly defaultTwoPaneSplitSizes: [number, number] = [50, 50];
    private readonly defaultThreePaneVerticalSplitSizes: [number, number] = [66.67, 33.33];
    private readonly defaultHorizontalSplitSizes: [number, number] = [50, 50];
    private readonly minVerticalSplitSizes: [number, number] = [120, 120];
    private readonly minHorizontalSplitSizes: [number, number] = [80, 80];
    private readonly fullscreenBodyClass = 'lecture-combined-view-fullscreen-active';
    private readonly focusableSelector = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    readonly verticalSplitSizes = this._verticalSplitSizes.asReadonly();
    readonly horizontalSplitSizes = this._horizontalSplitSizes.asReadonly();

    private verticalSplitInstance?: Split.Instance;
    private horizontalSplitInstance?: Split.Instance;
    private _previouslyFocusedElement: HTMLElement | null = null;
    private _focusTrapHandler?: (event: KeyboardEvent) => void;
    private _inertElements = new Map<HTMLElement, { hadInert: boolean; previousAriaHidden: string | null }>();

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

    readonly hasFullscreenContent = computed(() => (this.hasRenderableVideo() || this.hasPdf()) && this.shouldShowIrisSidebarInFullscreen());

    readonly lectureId = computed(() => this.lectureUnit().lecture?.id);

    readonly isCollapsed = computed(() => {
        const card = this.lectureUnitCard();
        return card ? card.isCollapsed() : true;
    });

    readonly showIrisSidebar = computed(() => {
        return this.isFullscreen() && this.shouldShowIrisSidebarInFullscreen();
    });

    readonly needsVerticalSplitter = computed(() => this.isFullscreen() && this.showIrisSidebar());

    readonly needsHorizontalSplitter = computed(() => this.isFullscreen() && this.hasRenderableVideo() && this.hasPdf());

    readonly contentContainerClasses = computed(() => ({
        'content-container--hidden': this.isCollapsed() && !this.isFullscreen(),
        'content-container--embedded': !this.isCollapsed() && !this.isFullscreen(),
        'content-container--fullscreen': this.isFullscreen(),
        'content-container--with-sidebar': this.isFullscreen() && this.showIrisSidebar(),
    }));

    readonly fullscreenAriaLabel = computed(() => {
        if (!this.isFullscreen()) {
            return undefined;
        }
        const unitName = this.lectureUnit().name ?? this.translateService.instant('artemisApp.lectureUnit.lectureUnit');
        return `${this.translateService.instant('artemisApp.lectureUnit.fullscreenView', { title: unitName })}`;
    });

    // TODO: This must use a server configuration to make it compatible with deployments other than TUM
    private readonly videoUrlAllowList = [RegExp('^https://(?:live\\.rbg\\.tum\\.de|tum\\.live)/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1')];

    constructor() {
        super();

        // Vertical splitter lifecycle (content | iris)
        effect(() => {
            const needs = this.needsVerticalSplitter();
            const mainEl = this.mainContentElement()?.nativeElement;
            const irisEl = this.irisSidebarElement()?.nativeElement;

            untracked(() => {
                this.destroyVerticalSplitter();

                if (needs && mainEl && irisEl) {
                    this.ngZone.runOutsideAngular(() => {
                        this.initVerticalSplitter([mainEl, irisEl]);
                    });
                }
            });
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

        // Keep lecture fullscreen above drawer overlays without touching global.scss.
        // Also handle accessibility setup/cleanup.
        effect(() => {
            const fullscreen = this.isFullscreen();

            untracked(() => {
                if (fullscreen) {
                    this.updateFullscreenTopOffset();
                    this.setGlobalFullscreenState(true);
                } else {
                    this.clearFullscreenTopOffset();
                    this.setGlobalFullscreenState(false);
                    this.cleanupFullscreenAccessibility();
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
            this.triggerContentLoad();
        } else {
            this.cancelPdfLoad();
            this.isPdfLoading.set(false);
            this.clearPdfState();
        }
    }

    private triggerContentLoad(): void {
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

    private initVerticalSplitter(elements: HTMLElement[]): void {
        this.verticalSplitInstance = Split(elements, {
            sizes: this._verticalSplitSizes(),
            minSize: this.minVerticalSplitSizes,
            gutterSize: 12,
            cursor: 'col-resize',
            direction: 'horizontal',
            onDragEnd: (sizes) => {
                this.ngZone.run(() => {
                    this._verticalSplitSizes.set([sizes[0], sizes[1]]);
                });
            },
            gutter: (_index, direction) => this.createSplitGutter(direction),
        });
    }

    private initHorizontalSplitter(elements: HTMLElement[]): void {
        this.horizontalSplitInstance = Split(elements, {
            sizes: this._horizontalSplitSizes(),
            minSize: this.minHorizontalSplitSizes,
            gutterSize: 12,
            cursor: 'row-resize',
            direction: 'vertical',
            onDragEnd: (sizes) => {
                this.ngZone.run(() => {
                    this._horizontalSplitSizes.set([sizes[0], sizes[1]]);
                });
            },
            gutter: (_index, direction) => this.createSplitGutter(direction),
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

    private destroyVerticalSplitter(): void {
        this.verticalSplitInstance?.destroy();
        this.verticalSplitInstance = undefined;
    }

    private destroyHorizontalSplitter(): void {
        this.horizontalSplitInstance?.destroy();
        this.horizontalSplitInstance = undefined;
    }

    private updateFullscreenTopOffset(): void {
        const topOffset = this.getNavbarHeight();
        this.hostElement.nativeElement.style.setProperty('--lecture-combined-view-top', `${topOffset}px`);
    }

    private clearFullscreenTopOffset(): void {
        this.hostElement.nativeElement.style.removeProperty('--lecture-combined-view-top');
    }

    private getNavbarHeight(): number {
        const navbar = document.querySelector('nav.navbar.jh-navbar');
        return navbar instanceof HTMLElement ? navbar.getBoundingClientRect().height : 0;
    }

    private setGlobalFullscreenState(isFullscreen: boolean): void {
        document.body.classList.toggle(this.fullscreenBodyClass, isFullscreen);
    }

    ngOnDestroy(): void {
        this.destroyVerticalSplitter();
        this.destroyHorizontalSplitter();
        this.clearFullscreenTopOffset();
        this.setGlobalFullscreenState(false);
        this.cleanupFullscreenAccessibility();
        this.cancelPdfLoad();
        this.revokePdfUrl();
    }

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

            // Wait for content to render before activating fullscreen using proper render cycle
            afterNextRender(
                () => {
                    afterNextRender(
                        () => {
                            this.activateFullscreen();
                        },
                        { injector: this.injector },
                    );
                },
                { injector: this.injector },
            );
        } else {
            // Already expanded, show fullscreen immediately
            this.activateFullscreen();
        }
    }

    private activateFullscreen(): void {
        this.resetSplitSizesForFullscreen();
        afterNextRender(
            () => {
                this.isFullscreen.set(true);
                // Move focus into fullscreen container after it's rendered
                afterNextRender(
                    () => {
                        this.focusFullscreenContainer();
                    },
                    { injector: this.injector },
                );
            },
            { injector: this.injector },
        );
    }

    private focusFullscreenContainer(): void {
        const container = this.contentContainer()?.nativeElement;
        if (!container) {
            return;
        }

        this.setupFocusTrap(container);
        this.setBackgroundInert(true);

        const focusableElements = container.querySelectorAll(this.focusableSelector);
        (focusableElements.length > 0 ? (focusableElements[0] as HTMLElement) : container).focus();
    }

    private setupFocusTrap(container: HTMLElement): void {
        this._focusTrapHandler = (event: KeyboardEvent) => {
            if (event.key !== 'Tab') {
                return;
            }

            const elements = Array.from(container.querySelectorAll<HTMLElement>(this.focusableSelector)).filter((el) => el.offsetParent !== null);
            if (elements.length === 0) {
                return event.preventDefault();
            }

            const active = document.activeElement;
            const isAtBoundary = event.shiftKey ? active === elements[0] || active === container : active === elements[elements.length - 1];

            if (isAtBoundary) {
                event.preventDefault();
                (event.shiftKey ? elements[elements.length - 1] : elements[0]).focus();
            }
        };

        // Listener is removed in cleanupFullscreenAccessibility() called from ngOnDestroy()
        container.addEventListener('keydown', this._focusTrapHandler);
    }

    private setBackgroundInert(inert: boolean): void {
        if (inert) {
            let current: HTMLElement | null = this.hostElement.nativeElement;
            while (current && current !== document.body) {
                const parent = current.parentElement;
                if (!parent) {
                    break;
                }
                Array.from(parent.children).forEach((sibling) => {
                    if (!(sibling instanceof HTMLElement) || sibling === current || this._inertElements.has(sibling)) {
                        return;
                    }
                    this._inertElements.set(sibling, {
                        hadInert: sibling.hasAttribute('inert'),
                        previousAriaHidden: sibling.getAttribute('aria-hidden'),
                    });
                    sibling.setAttribute('inert', '');
                    sibling.setAttribute('aria-hidden', 'true');
                });
                current = parent;
            }
        } else {
            this._inertElements.forEach((state, el) => {
                if (!state.hadInert) {
                    el.removeAttribute('inert');
                }
                if (state.previousAriaHidden === null) {
                    el.removeAttribute('aria-hidden');
                } else {
                    el.setAttribute('aria-hidden', state.previousAriaHidden);
                }
            });
            this._inertElements.clear();
        }
    }

    private cleanupFullscreenAccessibility(): void {
        const container = this.contentContainer()?.nativeElement;
        if (container && this._focusTrapHandler) {
            container.removeEventListener('keydown', this._focusTrapHandler);
            this._focusTrapHandler = undefined;
        }
        this.setBackgroundInert(false);
    }

    private shouldShowIrisSidebarInFullscreen(): boolean {
        const settings = this.irisSettings();
        const lecId = this.lectureId();
        const isTutorial = this.lectureUnit().lecture?.isTutorialLecture;
        return !!settings?.settings?.enabled && lecId !== undefined && !isTutorial;
    }

    private resetSplitSizesForFullscreen(): void {
        this._horizontalSplitSizes.set(this.defaultHorizontalSplitSizes);

        const hasThreePaneLayout = this.shouldShowIrisSidebarInFullscreen() && this.hasRenderableVideo() && this.hasPdf();
        this._verticalSplitSizes.set(hasThreePaneLayout ? this.defaultThreePaneVerticalSplitSizes : this.defaultTwoPaneSplitSizes);
    }

    closeFullscreen(): void {
        if (!this.isFullscreen() || this.hasOpenPdfFullscreen()) {
            return;
        }

        const elementToRestore = this._previouslyFocusedElement && document.contains(this._previouslyFocusedElement) ? this._previouslyFocusedElement : undefined;
        this._previouslyFocusedElement = null;
        this.isFullscreen.set(false);

        if (elementToRestore) {
            afterNextRender(
                () => {
                    elementToRestore.focus();
                },
                { injector: this.injector },
            );
        }
    }

    @HostListener('document:keydown.escape', ['$event'])
    onEscapePressed(event: Event): void {
        if (!this.isFullscreen() || event.defaultPrevented || this.hasOpenPdfFullscreen()) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        this.closeFullscreen();
    }

    @HostListener('window:resize')
    onResize(): void {
        if (this.isFullscreen()) {
            this.updateFullscreenTopOffset();
        }
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

    private hasOpenPdfFullscreen(): boolean {
        return this.hostElement.nativeElement.querySelector('.pdf-fullscreen-window') instanceof HTMLElement;
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
