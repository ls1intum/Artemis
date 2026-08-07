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
import { NgTemplateOutlet } from '@angular/common';
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
import { IrisCombinedViewContextDTO, IrisSlidesContextDTO, IrisVideoContextDTO, LectureContextsProvider } from 'app/iris/shared/entities/iris-message-context-dto.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisPointOut, formatTimestamp } from 'app/iris/shared/entities/iris-point-out.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { LectureUnitFullscreenLayoutComponent } from 'app/lecture/shared/lecture-unit-fullscreen-layout/lecture-unit-fullscreen-layout.component';
import { FormsModule } from '@angular/forms';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

type SplitSizes = [number, number];

/** Tolerance the video player applies when matching a position to a transcript segment; mirrored where we have to
 * anticipate which segment a player will report for a timestamp. */
const SEGMENT_BOUNDARY_TOLERANCE = 0.3;

/** Sentinel in {@link Attachment.displayPageNumbers} meaning the slide has no detected display page number. */
const UNDETECTED_DISPLAY_PAGE_NUMBER = -1;

@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [
        NgTemplateOutlet,
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
    private readonly chatService = inject(IrisChatService);

    targetTimestamp = input<number | undefined>(undefined); // For video deeplinking
    targetPdfPage = input<number | undefined>(undefined); // For PDF deeplinking
    irisSettings = input<IrisCourseSettingsWithRateLimitDTO | undefined>(undefined);
    contextsProvider = input<LectureContextsProvider | undefined>(undefined); // For collecting context from visible units

    readonly lectureUnitCard = viewChild(LectureUnitComponent);
    readonly fullscreenLayout = viewChild(LectureUnitFullscreenLayoutComponent);
    readonly videoPlayer = viewChild(VideoPlayerComponent);
    readonly youtubePlayer = viewChild(YouTubePlayerComponent);
    readonly pdfViewer = viewChild(PdfViewerComponent);

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);
    /**
     * Whether the transcript request is still in flight. It is started just before the loading flag clears, so an empty
     * transcript only means "there is none" once this has settled too — see {@link isPointOutUnreachable}.
     */
    private readonly isTranscriptLoading = signal<boolean>(false);

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
    private isApplyingVideoSeek = false;

    // A point-out navigation target waiting to be applied once the combined view is open and the
    // relevant viewer (PDF / video) has rendered. Applied (and cleared) by an effect in the constructor.
    private readonly pendingPointOut = signal<IrisPointOut | undefined>(undefined);

    // The position a point-out turned synchronization off for, ready to be named in the notice that explains the
    // switched-off toggle. The page is the number printed on the slide, so the notice agrees with the chip in the chat.
    private readonly syncDisabledByPointOutState = signal<{ page: number; time: string } | undefined>(undefined);
    readonly syncDisabledByPointOut = this.syncDisabledByPointOutState.asReadonly();

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

    readonly contextProvider = computed(() => ({
        getCurrentPdfPage: () => {
            const viewer = this.pdfViewer();
            return viewer ? viewer.currentPageSignal() : undefined;
        },
        getCurrentVideoTimestamp: () => {
            const videoPlayer = this.videoPlayer();
            const youtubePlayer = this.youtubePlayer();
            return videoPlayer?.getCurrentTime() ?? youtubePlayer?.getCurrentTime();
        },
        hasVideoBeenPlayed: () => {
            const videoPlayer = this.videoPlayer();
            const youtubePlayer = this.youtubePlayer();
            return videoPlayer?.hasBeenPlayed() ?? youtubePlayer?.hasBeenPlayed() ?? false;
        },
    }));

    readonly ownContextsProvider = computed<LectureContextsProvider>(() => ({
        getVisibleContexts: () => {
            if (this.isCollapsed()) {
                return [];
            }

            const unitId = this.lectureUnit()?.id;
            if (!unitId) {
                return [];
            }

            // In the combined view, the slide and video context are nested on the combined view
            // context instead of being sent as separate top-level entries.
            const provider = this.contextProvider();
            const pdfPage = provider.getCurrentPdfPage?.();
            const videoTimestamp = provider.getCurrentVideoTimestamp?.();
            const hasVideoBeenPlayed = provider.hasVideoBeenPlayed?.() ?? false;

            const slides: IrisSlidesContextDTO | undefined = pdfPage != null ? { type: 'slides', lectureUnitId: unitId, page: pdfPage } : undefined;
            // Only include the video context if the video has been played (not just showing thumbnail)
            const video: IrisVideoContextDTO | undefined =
                videoTimestamp != null && hasVideoBeenPlayed ? { type: 'video', lectureUnitId: unitId, timestamp: videoTimestamp } : undefined;

            // The combined view only carries meaningful context when a slide or video is present.
            if (!slides && !video) {
                return [];
            }

            const combinedViewContext: IrisCombinedViewContextDTO = {
                type: 'combinedView',
                slides,
                video,
            };

            return [combinedViewContext];
        },
    }));

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

        // Iris points the student to a position in the combined view, either pushed by the server while the
        // pipeline waits or raised by a marker click in the chat history.
        this.chatService.pointOut$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((pointOut) => this.handlePointOut(pointOut));

        // Apply a pending point-out target once the combined view is open and the viewer it needs has rendered, or
        // drop it once that viewer turns out not to be coming. Reading the viewChild signals here re-runs this effect
        // as they become available.
        effect(() => {
            const pointOut = this.pendingPointOut();
            // A marker click opens the combined view first, so isFullscreen() is still false on the initial
            // run here and the target simply stays pending until the view (and its viewer) is up.
            if (!pointOut || !this.isFullscreen()) {
                return;
            }
            // Give up as soon as the viewer this target needs is known not to be coming. Without this the wait below
            // never ends, so nobody would answer and a waiting pipeline would sit out its full ack timeout. Evaluating
            // it here rather than in handlePointOut is what makes it safe: the effect re-runs as the viewers settle,
            // so a target is only dropped once "not there yet" has turned into "not coming".
            if (this.isPointOutUnreachable(pointOut)) {
                untracked(() => {
                    this.acknowledgeAsDropped(pointOut);
                    this.pendingPointOut.set(undefined);
                });
                return;
            }
            // A rendered viewer whose document is still loading reports 0 pages and would reject every target, so wait
            // for the page count as well — otherwise a perfectly valid point-out would be reported as not applied.
            const pdfReady = pointOut.page == undefined || (this.pdfViewer()?.getTotalPages() ?? 0) > 0;
            // Same for the YouTube player: Angular creates its wrapper component long before the iframe API hands the
            // real player over, and a seek in between is silently dropped. So wait for the player itself, not for the
            // component — reading its readiness signal here re-runs this effect once it flips.
            const videoReady = pointOut.timestamp == undefined || this.videoPlayer() !== undefined || (this.youtubePlayer()?.isPlayerReady() ?? false);
            if (!pdfReady || !videoReady) {
                return;
            }
            untracked(() => {
                const applied = this.applyPointOut(pointOut);
                // Acknowledge a waiting pipeline only now — once the view has really moved — so Iris learns
                // "applied" for the actual navigation, not merely because the combined view was open.
                if (pointOut.correlationId) {
                    this.chatService.sendCommandAck(pointOut.correlationId, applied);
                }
                this.pendingPointOut.set(undefined);
            });
        });
    }

    /**
     * Handles a point-out targeting this unit; requests for other units are ignored (the matching unit, or the
     * server-side timeout, handles them). If the combined view is closed, a marker click (forceOpen) opens it
     * first, while a server-pushed point-out is acknowledged straight away as not applied. The actual page jump
     * / video seek is deferred to the pendingPointOut effect, which waits until the relevant viewer is ready and then
     * acknowledges the outcome that viewer reported — or drops the target once that viewer turns out not to be coming.
     * @param pointOut the requested navigation target
     */
    private handlePointOut(pointOut: IrisPointOut): void {
        if (pointOut.lectureUnitId !== this.lectureUnit()?.id) {
            return;
        }
        if (!this.isFullscreen()) {
            if (!pointOut.forceOpen) {
                if (pointOut.correlationId) {
                    this.chatService.sendCommandAck(pointOut.correlationId, false);
                }
                return;
            }
            this.openFullscreen();
        }
        this.acknowledgeAsDropped(this.pendingPointOut());
        this.pendingPointOut.set(pointOut);
    }

    /**
     * Moves the viewers to a point-out's position, with both viewers ready.
     *
     * A point-out naming only a page (or only a timestamp) leaves the other pane to synchronization on purpose —
     * the video following Iris to the slide it named is what the toggle is for. A point-out naming both is applied
     * as given, and where that contradicts synchronization the toggle gives way first, so the seek cannot drag the
     * PDF back off the page Iris named.
     *
     * @param pointOut the navigation target to apply
     * @return whether the view really moved to it. Iris proposes the page, so it can name one the deck does not
     *         have (a slide's printed number rather than its index, say), which must be reported back as not
     *         applied: an unconditional success would leave Iris claiming a jump that never happened and put a
     *         dead point-out chip into the chat history.
     */
    private applyPointOut(pointOut: IrisPointOut): boolean {
        const page = pointOut.page;
        const timestamp = pointOut.timestamp;

        // A point-out supersedes the explanation left by an earlier one, whether or not it disables the toggle again.
        this.syncDisabledByPointOutState.set(undefined);
        if (page != undefined && timestamp != undefined && this.contradictsSynchronization(page, timestamp)) {
            this.synchronizeVideoAndSlides.set(false);
            this.clearSynchronizationTargets();
            this.syncDisabledByPointOutState.set({ page: pointOut.displayPage ?? page, time: formatTimestamp(timestamp) });
        }

        let applied = true;
        if (page != undefined) {
            applied = this.pdfViewer()?.goToPage(page) ?? false;
        }
        if (timestamp != undefined) {
            const videoPlayer = this.videoPlayer();
            if (videoPlayer) {
                videoPlayer.seekTo(timestamp, false);
            } else {
                // The YouTube player reports whether it was ready to take the seek; a dropped seek is not a navigation.
                applied = (this.youtubePlayer()?.seekTo(timestamp, false) ?? false) && applied;
            }
        }
        return applied;
    }

    /**
     * Whether a point-out asks for a position the two panes cannot hold while synchronized — the page it names is
     * not the one synchronization pairs with the slide its timestamp falls on. Left switched on, the toggle would
     * undo the point-out: the student's next scroll or the video playing on would snap one pane away from where
     * Iris pointed them, which reads as a glitch. So the point-out wins and the toggle is switched off, honestly
     * showing the mismatch and leaving it to the student to re-synchronize.
     *
     * That Iris contradicts synchronization does not make Iris wrong: synchronization pairs a slide with the
     * *first* transcript segment mentioning it and rests on the slide detection in the transcript, which makes it
     * the weaker of the two statements.
     *
     * @param page the slide page the point-out asks for
     * @param timestamp the video position the point-out asks for
     * @return whether synchronization has to give way for them
     */
    private contradictsSynchronization(page: number, timestamp: number): boolean {
        if (!this.synchronizeVideoAndSlides()) {
            return false;
        }
        // Each player resolves a timestamp to a segment itself, and at a shared boundary the two disagree: the video
        // player takes the earlier segment (it allows a 0.3s tolerance), the YouTube player the later one. Since Iris
        // tends to name exactly such boundary timestamps, every segment either of them could land on has to map back
        // to the page Iris named — judging by one player's rule would let the other's echo drag the PDF off it.
        const candidates = this.transcriptSegments().filter(
            (segment) => timestamp >= (segment.startTime ?? Infinity) - SEGMENT_BOUNDARY_TOLERANCE && timestamp <= (segment.endTime ?? -Infinity) + SEGMENT_BOUNDARY_TOLERANCE,
        );
        const pdfPages = this.synchronizationState().displayedPageNumberToPdfPage;
        // No segment at all, or a slide with no page of its own, has no synchronization partner either, so it is
        // just as little a position the toggle can hold.
        return candidates.length === 0 || candidates.some((segment) => segment.slideNumber == undefined || pdfPages.get(segment.slideNumber) !== page);
    }

    /**
     * Whether the viewer a point-out needs can no longer appear in this combined view, which makes its target
     * unreachable for good. Only settled facts count here — "still loading" is not an answer, since the effect that
     * asks re-runs once it becomes one, and giving up early would report a perfectly good point-out as not applied.
     *
     * Slides: the unit either has no PDF at all, or its PDF failed to load, in which case the viewer is replaced by an
     * error message for as long as the view stays open. That the URL has not arrived yet is not decisive.
     *
     * Video: only a seekable player can hold a timestamp, and anything else falls back to a bare iframe, which cannot.
     * The conditions below mirror the template's, since a player that is never rendered can never be seeked either:
     * the video player needs a resolved playlist *and* a transcript, while the YouTube player needs neither and renders
     * on the video id alone. A playlist without a transcript therefore ends in the iframe, not in a player — waiting on
     * it would block until the server-side ack timeout, which is the very thing this method exists to prevent.
     *
     * Both requests must have settled before their outcome counts. The transcript is requested just before the loading
     * flag clears and settles after it, so an empty transcript is only final once its own request has finished too;
     * judging by the loading flag alone would drop a perfectly good point-out in that window.
     * @param pointOut the pending navigation target
     * @return whether it can be given up on
     */
    private isPointOutUnreachable(pointOut: IrisPointOut): boolean {
        if (pointOut.page != undefined && (!this.hasPdf() || this.pdfLoadError())) {
            return true;
        }
        const hasSeekableVideo = (!!this.playlistUrl() && this.hasTranscript()) || (!!this.youtubeVideoId() && !this.youtubePlayerFailed());
        return pointOut.timestamp != undefined && !this.isLoading() && !this.isTranscriptLoading() && !hasSeekableVideo;
    }

    /**
     * Acknowledges a pending point-out that is being dropped as not applied. The client knows at that moment that the
     * target will never be reached, so reporting it right away releases a waiting Iris pipeline instead of making it
     * sit out the server-side ack timeout. Marker clicks carry no correlation id and have nobody waiting on them.
     * @param pointOut the point-out being dropped, if any
     */
    private acknowledgeAsDropped(pointOut: IrisPointOut | undefined): void {
        if (pointOut?.correlationId) {
            this.chatService.sendCommandAck(pointOut.correlationId, false);
        }
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

        if (failedUrl !== this.getVersionedAttachmentLink()) {
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
            this.isTranscriptLoading.set(false);

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

        this.isTranscriptLoading.set(true);
        this.lectureTranscriptionService
            .getTranscription(id)
            .pipe(
                map((dto) => dto?.segments?.filter((segment): segment is TranscriptSegment => this.isValidTranscriptSegment(segment)) ?? []),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (segments) => {
                    this.transcriptSegments.set(segments);
                    this.isTranscriptLoading.set(false);
                },
                error: () => {
                    // Failed to fetch transcript, video player will work without it
                    this.transcriptSegments.set([]);
                    this.isTranscriptLoading.set(false);
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

        const link = this.getVersionedAttachmentLink();

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

        const link = this.getVersionedAttachmentLink();
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
        if (!isFullscreen) {
            // The view was closed before a point-out could be applied, so its target is now unreachable.
            // Dropping it here (on the close transition, not on the closed state, which a marker click starts
            // out in) keeps it from being applied on a later, unrelated reopen.
            this.acknowledgeAsDropped(this.pendingPointOut());
            this.pendingPointOut.set(undefined);
            // The toggle it explains goes away with the view; a later reopen starts without a stale explanation.
            this.syncDisabledByPointOutState.set(undefined);
        }
    }

    protected onSynchronizationToggleChange(enabled: boolean): void {
        // The student is deciding about the toggle themselves, so the explanation for its state has served its purpose.
        this.syncDisabledByPointOutState.set(undefined);
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

        // A sync-initiated seek re-emits the active slide synchronously (both players call
        // updateCurrentSegment from within seekTo). Ignore that echo regardless of which slide it
        // resolved to, otherwise our own seek would drag the PDF back — see seekVideoToDisplayedPageNumber.
        if (this.isApplyingVideoSeek) {
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

        // Both players synchronously re-emit the active slide from inside seekTo. Guard the whole
        // call so that synchronous echo is suppressed in onVideoSlideNumberChange and cannot bounce
        // the PDF back. The flag is only ever set for the duration of this synchronous call.
        this.isApplyingVideoSeek = true;
        try {
            const videoPlayer = this.videoPlayer();
            if (videoPlayer) {
                videoPlayer.seekTo(timestamp, shouldResumePlayback);
                return;
            }

            this.youtubePlayer()?.seekTo(timestamp, shouldResumePlayback);
        } finally {
            this.isApplyingVideoSeek = false;
        }
    }

    private isVideoCurrentlyPlaying(): boolean {
        return this.videoPlayer()?.isPlaying() ?? this.youtubePlayer()?.isPlaying() ?? false;
    }

    private getActiveVideoSlideNumber(): number | undefined {
        return this.videoPlayer()?.getCurrentSlideNumber() ?? this.youtubePlayer()?.getCurrentSlideNumber();
    }

    private clearSynchronizationTargets(): void {
        this.pendingPdfTargetPage = undefined;
        this.isApplyingVideoSeek = false;
    }

    private computeSynchronizationState(): {
        available: boolean;
        displayedPageNumberToPdfPage: Map<number, number>;
        pdfPageToDisplayedPageNumber: Map<number, number>;
        displayedPageNumberToVideoTimestamp: Map<number, number>;
    } {
        const displayedPageNumbers = this.lectureUnit().attachment?.displayPageNumbers ?? [];
        const transcriptSegments = this.transcriptSegments();

        const displayedPageNumberToPdfPage = new Map<number, number>();
        const pdfPageToDisplayedPageNumber = new Map<number, number>();
        const displayedPageNumberToVideoTimestamp = new Map<number, number>();

        if (!this.hasPdf() || !this.hasTranscript() || !this.hasSyncCapableVideo() || displayedPageNumbers.length === 0) {
            return { available: false, displayedPageNumberToPdfPage, pdfPageToDisplayedPageNumber, displayedPageNumberToVideoTimestamp };
        }

        // displayPageNumbers is indexed in PDF page order: index 0 = PDF page 1, index 1 = PDF page 2, ...
        for (const [pdfIndex, displayedPageNumber] of displayedPageNumbers.entries()) {
            // A page with no detected display number (sentinel -1) has no sync partner. It still
            // occupies a PDF page, so the index keeps counting; we just skip the mapping.
            if (displayedPageNumber === UNDETECTED_DISPLAY_PAGE_NUMBER) {
                continue;
            }
            const pdfPage = pdfIndex + 1;
            // A display page number may appear on several PDF pages; keep the first occurrence as the sync target.
            if (!displayedPageNumberToPdfPage.has(displayedPageNumber)) {
                displayedPageNumberToPdfPage.set(displayedPageNumber, pdfPage);
            }
            pdfPageToDisplayedPageNumber.set(pdfPage, displayedPageNumber);
        }

        // Map each displayed page that exists in the PDF to the first transcript timestamp that mentions it.
        for (const segment of transcriptSegments) {
            const slideNumber = segment.slideNumber;
            if (slideNumber !== undefined && displayedPageNumberToPdfPage.has(slideNumber) && !displayedPageNumberToVideoTimestamp.has(slideNumber)) {
                displayedPageNumberToVideoTimestamp.set(slideNumber, segment.startTime);
            }
        }

        // Synchronization needs at least one page that exists in both the PDF and the video transcript.
        return {
            available: displayedPageNumberToVideoTimestamp.size > 0,
            displayedPageNumberToPdfPage,
            pdfPageToDisplayedPageNumber,
            displayedPageNumberToVideoTimestamp,
        };
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
        const attachment = this.lectureUnit().attachment;

        if (link && attachment) {
            if (attachment.studentVersion) {
                // The endpoint supplies the attachment's display name through Content-Disposition. Keep the unique student-version path as the browser cache key.
                this.fileService.downloadFile(link);
            } else {
                this.fileService.downloadFileByAttachmentName(link, attachment.name!, attachment.version);
            }
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

    private getVersionedAttachmentLink(): string | undefined {
        const link = this.getAttachmentLink();
        const attachment = this.lectureUnit().attachment;
        return link && attachment ? this.fileService.addAttachmentVersionToUrl(link, attachment.version) : undefined;
    }

    handleOriginalVersion() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        const link = addPublicFilePrefix(this.lectureUnit().attachment!.link);

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!, this.lectureUnit().attachment!.version);
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
