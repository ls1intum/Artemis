import { Component, DestroyRef, OnDestroy, OnInit, computed, effect, inject, signal, untracked, viewChildren } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MODULE_FEATURE_IRIS, addPublicFilePrefix } from 'app/app.constants';
import { downloadStream } from 'app/foundation/util/download.util';
import dayjs, { Dayjs } from 'dayjs/esm';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Attachment, attachmentIsPdf } from 'app/lecture/shared/entities/attachment.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { onError } from 'app/foundation/util/global.utils';
import { filter, finalize, tap } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';
import { faChalkboardTeacher, faComment, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { Subscription } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UpperCasePipe } from '@angular/common';
import { ExerciseUnitComponent } from '../exercise-unit/exercise-unit.component';
import { AttachmentVideoUnitComponent } from '../attachment-video-unit/attachment-video-unit.component';
import { TextUnitComponent } from '../text-unit/text-unit.component';
import { OnlineUnitComponent } from '../online-unit/online-unit.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';
import { FileService } from 'app/foundation/service/file.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { InformationBox, InformationBoxComponent, InformationBoxContent } from 'app/shared-ui/information-box/information-box.component';
import { IrisMessageContextDTO, IrisSlidesContextDTO, IrisVideoContextDTO, LectureContextsProvider } from 'app/iris/shared/entities/iris-message-context-dto.model';
import { LECTURE_DEEP_LINK_QUERY_PARAMS, LectureDeepLink, parseLectureDeepLink } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

export interface LectureUnitCompletionEvent {
    lectureUnit: LectureUnit;
    completed: boolean;
}

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../../../../course/overview/course-overview/course-overview.scss', '../../../shared/course-lectures/course-lectures.scss'],
    imports: [
        TranslateDirective,
        ExerciseUnitComponent,
        AttachmentVideoUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        FaIconComponent,
        CourseSidebarToggleButtonComponent,
        DiscussionSectionComponent,
        UpperCasePipe,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        MarkdownDirective,
        IrisBaseChatbotComponent,
        IrisLogoComponent,
        ResizablePanelsComponent,
        PanelDirective,
        InformationBoxComponent,
    ],
})
export class CourseLectureDetailsComponent implements OnInit, OnDestroy {
    private readonly alertService = inject(AlertService);
    private readonly lectureService = inject(LectureService);
    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly fileService = inject(FileService);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly scienceService = inject(ScienceService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly chatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);

    protected readonly LectureUnitType = LectureUnitType;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;
    protected readonly isMessagingEnabled = isMessagingEnabled;

    protected readonly faSpinner = faSpinner;
    protected readonly faChalkboardTeacher = faChalkboardTeacher;
    protected readonly faComment = faComment;
    protected readonly IrisLogoSize = IrisLogoSize;

    lectureId?: number;
    private readonly courseStorageService = inject(CourseStorageService);

    readonly courseId = signal<number | undefined>(undefined);

    /**
     * Whether to show the communication section beside the lecture, mirroring the exercise detail page.
     *
     * The course must come from the shell's store rather than `lecture.course`: the lecture details payload nests only
     * a stub of the course, without `courseInformationSharingConfiguration`, so reading the flag from there always
     * reported communication as disabled and the section never rendered.
     */
    readonly showDiscussion = computed(() => {
        const lecture = this.lecture();
        if (!lecture || lecture.isTutorialLecture) {
            return false;
        }
        const courseId = this.courseId();
        const course = (courseId !== undefined ? this.courseStorageService.getCourse(courseId) : undefined) ?? lecture.course;
        return !!course && (isCommunicationEnabled(course) || isMessagingEnabled(course));
    });
    /**
     * Whether to offer Iris beside the lecture, the same way the exercise page does. A tutorial lecture has no Iris
     * session of its own, so it keeps the content panel alone.
     */
    readonly showIris = computed(() => {
        const lecture = this.lecture();
        return !!lecture && !lecture.isTutorialLecture && !!this.irisSettings()?.settings?.enabled;
    });

    /**
     * Whether the Iris panel opens collapsed. A user who declined AI gets a chat that says only that, so leaving the
     * panel open would cost them lecture width on every visit. The exercise page makes the same call; it additionally
     * exempts pages that show an editor, of which a lecture has none.
     */
    readonly irisPanelStartsCollapsed = computed(() => this.showIris() && this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.NO_AI);

    readonly isLoading = signal(false);
    readonly lecture = signal<Lecture | undefined>(undefined);
    readonly isDownloadingLink = signal<string | undefined>(undefined);
    readonly lectureUnits = signal<LectureUnit[]>([]);
    readonly hasPdfLectureUnit = signal(false);
    readonly irisSettings = signal<IrisCourseSettingsWithRateLimitDTO | undefined>(undefined);
    paramsSubscription?: Subscription;
    courseParamsSubscription?: Subscription;
    irisEnabled = false;
    readonly informationBoxData = signal<InformationBox[]>([]);

    readonly isSidebarCollapsed = signal(false);
    private readonly sidebarToggle = signal<(() => void) | undefined>(undefined);
    readonly toggleSidebar = (): void => this.sidebarToggle()?.();

    private readonly deepLinkState = signal<LectureDeepLink | undefined>(undefined);
    /** The jump the page is currently executing (Iris citation, global search result, pasted link). */
    readonly deepLink = this.deepLinkState.asReadonly();
    /**
     * A deep link read from the URL that still waits for the lecture units, which decide what it may target, together
     * with the lecture it arrived for. Without that lecture the request would be nothing but a unit id and could be
     * executed against whichever lecture happens to load next.
     */
    private pendingDeepLink?: { readonly deepLink: LectureDeepLink; readonly lectureId: number };

    // ViewChildren to access all attachment/video unit components
    private readonly attachmentVideoUnits = viewChildren(AttachmentVideoUnitComponent);

    // Context provider for the chatbot
    readonly contextsProvider: LectureContextsProvider = {
        getVisibleContexts: () => this.collectVisibleContexts(),
    };

    /** Context provider function passed to the chat panel, so a question can be answered against the visible units */
    readonly contextProvider = computed<(() => IrisMessageContextDTO[]) | undefined>(() => () => this.collectVisibleContexts());

    constructor() {
        /*
         * The Iris panel renders whichever session the chat service currently holds, so the lecture's own session has
         * to be opened for it — the floating button this replaced did the same from the route parameters. It reuses an
         * existing session tagged with this lecture if there is one, and otherwise opens the course chat with the
         * lecture pre-selected as context.
         */
        effect(() => {
            const lectureId = this.lecture()?.id;
            if (this.showIris() && lectureId) {
                // untracked: the chat service's own state must not re-trigger this.
                untracked(() => this.chatService.openChat(ChatServiceMode.LECTURE, lectureId));
            }
        });
    }

    ngOnInit(): void {
        this.irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);

        // As defined in courses.route.ts, the courseId is in the grand parent route of the lectureId route.
        const grandParentRoute = this.activatedRoute.parent?.parent;
        if (grandParentRoute) {
            this.courseParamsSubscription = grandParentRoute.params.subscribe((params) => {
                // Note: if courseId is not found, sub components cannot navigate properly
                this.courseId.set(+params.courseId);
            });
        }

        this.paramsSubscription = this.activatedRoute.params.subscribe((params) => {
            const lectureId = +params.lectureId;
            if (lectureId !== this.lectureId) {
                // Both jumps belong to the lecture being left, and this component outlives it: Angular reuses it for a
                // new lectureId. Kept around, they would be executed against a lecture they were never meant for. The
                // one exception is a request that came in for the lecture being entered, which is why it is only
                // dropped when it names another one.
                this.deepLinkState.set(undefined);
                if (this.pendingDeepLink && this.pendingDeepLink.lectureId !== lectureId) {
                    this.pendingDeepLink = undefined;
                }
            }

            this.lectureId = lectureId;
            if (this.lectureId) {
                this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN, this.lectureId);
                this.loadData();
            }
        });

        this.activatedRoute.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const deepLink = parseLectureDeepLink(params);
            if (!deepLink) {
                // Also the emission caused by clearing the parameters; there is nothing to execute then.
                return;
            }

            // The snapshot, not `lectureId`: it is already advanced to the lecture the link arrived with, while the
            // field is only set once the route parameters are reported, which happens after the query parameters.
            this.pendingDeepLink = { deepLink, lectureId: Number(this.activatedRoute.snapshot.params['lectureId']) };
            this.publishDeepLink();
        });

        // The parameters are read while the router is still activating this page, so they can only be taken back out
        // once that navigation has finished: starting the next one from inside it cancels it half-way through.
        this.router.events
            .pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(() => this.clearDeepLinkQueryParams());
    }

    loadData() {
        this.isLoading.set(true);
        if (this.lectureId) {
            this.lectureService
                .findWithDetails(this.lectureId)
                .pipe(
                    finalize(() => {
                        this.isLoading.set(false);
                    }),
                )
                .subscribe({
                    next: (findLectureResult) => {
                        const lecture = findLectureResult.body!;
                        this.lecture.set(lecture);
                        lecture.attachments?.forEach((attachment) => {
                            if (attachment.link) {
                                attachment.linkUrl = addPublicFilePrefix(attachment.link);
                            }
                        });

                        this.lectureUnits.set(lecture.lectureUnits ?? []);
                        this.publishDeepLink();
                        this.hasPdfLectureUnit.set(
                            this.lectureUnits().some((unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO && attachmentIsPdf((unit as AttachmentVideoUnit).attachment)),
                        );
                        if (this.irisEnabled && lecture.course?.id) {
                            this.irisSettingsService.getCourseSettingsWithRateLimit(lecture.course.id).subscribe((response) => {
                                this.irisSettings.set(response);
                            });
                        }
                        const informationBoxData: InformationBox[] = [];
                        if (lecture.startDate) {
                            const startDateInfoBoxTitle = 'artemisApp.courseOverview.lectureDetails.startDate';
                            informationBoxData.push(this.createDateInfoBox(lecture.startDate, startDateInfoBoxTitle));
                        }
                        if (lecture.endDate) {
                            const endDateInfoBoxTitle = 'artemisApp.courseOverview.lectureDetails.endDate';
                            informationBoxData.push(this.createDateInfoBox(lecture.endDate, endDateInfoBoxTitle));
                        }
                        this.informationBoxData.set(informationBoxData);
                    },
                    error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                });
        }
    }

    setSidebarToggle(isCollapsed: boolean, toggleSidebar: () => void): void {
        this.isSidebarCollapsed.set(isCollapsed);
        this.sidebarToggle.set(toggleSidebar);
    }

    attachmentNotReleased(attachment: Attachment): boolean {
        return attachment.releaseDate != undefined && !dayjs(attachment.releaseDate).isBefore(dayjs());
    }

    attachmentExtension(attachment: Attachment): string {
        if (!attachment.link) {
            return 'N/A';
        }

        return attachment.link.split('.').pop()!;
    }

    downloadAttachment(downloadUrl?: string, downloadName?: string, version?: number): void {
        if (!this.isDownloadingLink() && downloadUrl && downloadName) {
            this.isDownloadingLink.set(downloadUrl);
            this.fileService.downloadFileByAttachmentName(downloadUrl, downloadName, version);
            this.isDownloadingLink.set(undefined);
        }
    }

    downloadMergedFiles(): void {
        if (this.lectureId) {
            this.fileService
                .downloadMergedFile(this.lectureId)
                .pipe(
                    tap((blob) => {
                        downloadStream(blob.body, 'application/pdf', this.lecture()?.title ?? 'Lecture');
                        this.loadData();
                    }),
                )
                .subscribe();
        }
    }

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        this.lectureUnitService.completeLectureUnit(this.lecture()!, event, () => {
            // Replace the unit with a new reference so the card's signal input reacts and the checkmark updates immediately.
            this.lectureUnits.update((units) => units.map((unit) => (unit.id === event.lectureUnit.id ? Object.assign({}, unit, { completed: event.completed }) : unit)));
        });
    }

    /**
     * Hands the pending deep link to the units, once they are known.
     *
     * Only the lecture the link arrived for may decide what it can target, and that lecture's units arrive later: the
     * route reports the link while the page is still loading, and a link into another lecture even arrives before the
     * switch. Until its own lecture is the loaded one the link waits and loadData publishes it. It is published at most
     * once, as executing the same request twice would jump twice.
     */
    private publishDeepLink(): void {
        const pending = this.pendingDeepLink;
        if (!pending || this.lecture()?.id !== pending.lectureId) {
            return;
        }

        this.pendingDeepLink = undefined;
        this.deepLinkState.set(this.dropUnreachableTargets(pending.deepLink));
    }

    /**
     * Drops the parts of a deep link the target unit cannot honour, so a stale link cannot make a unit look like it has
     * a video or slides. Returns undefined when the unit is gone from the lecture.
     *
     * Only an attachment/video unit has places to jump to inside it; every other unit is opened and scrolled to, which
     * is all a jump can mean there.
     */
    private dropUnreachableTargets(deepLink: LectureDeepLink): LectureDeepLink | undefined {
        const targetUnit = this.lectureUnits().find((unit) => unit.id === deepLink.unitId);
        if (!targetUnit) {
            return undefined;
        }

        if (targetUnit.type !== LectureUnitType.ATTACHMENT_VIDEO) {
            return { unitId: deepLink.unitId };
        }

        const attachmentUnit = targetUnit as AttachmentVideoUnit;
        const hasVideo = !!attachmentUnit.videoSource || !!attachmentUnit.youtubeVideoId;

        return {
            unitId: deepLink.unitId,
            timestamp: hasVideo ? deepLink.timestamp : undefined,
            page: attachmentIsPdf(attachmentUnit.attachment) ? deepLink.page : undefined,
        };
    }

    /**
     * Takes the deep link out of the URL, once the navigation that brought it in has finished.
     *
     * The parameters are a one-shot command, not a description of what is on screen. Left in place they go stale as soon
     * as the user collapses the unit or scrolls on, and worse: the next click on the same citation would navigate to the
     * URL that is already active, which the router reports as no change at all, so the jump would never arrive.
     *
     * Runs after every navigation and decides from the URL whether there is anything to take out, so the removal itself
     * passes through here without starting another one. Parameters that named no reachable unit are dropped as well:
     * they are as stale as the ones that were executed, and nothing acts on them either.
     */
    private clearDeepLinkQueryParams(): void {
        const queryParams = this.activatedRoute.snapshot.queryParams;
        if (!LECTURE_DEEP_LINK_QUERY_PARAMS.some((param) => queryParams[param] !== undefined)) {
            return;
        }

        void this.router.navigate([], {
            relativeTo: this.activatedRoute,
            // Angular removes a query parameter when it is merged in as null; other parameters on the page are kept.
            queryParams: Object.fromEntries(LECTURE_DEEP_LINK_QUERY_PARAMS.map((param) => [param, null])),
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
    }

    createDateInfoBox(date: Dayjs, contentStringName: string): InformationBox {
        const boxContentStartDate: InformationBoxContent = {
            type: 'dateTime',
            value: date,
        };
        return {
            title: contentStringName,
            content: boxContentStartDate,
            isContentComponent: true,
        };
    }

    ngOnDestroy() {
        this.paramsSubscription?.unsubscribe();
        this.courseParamsSubscription?.unsubscribe();
    }

    private isElementVisible(element: Element | null): boolean {
        if (!element) return false;
        const rect = element.getBoundingClientRect();
        const vh = window.innerHeight || document.documentElement.clientHeight;
        const vw = window.innerWidth || document.documentElement.clientWidth;
        return rect.top < vh && rect.bottom > 0 && rect.left < vw && rect.right > 0;
    }

    /**
     * Collects context from all visible and expanded attachment/video units.
     * Uses a snapshot-based approach: calculates visibility at the moment this method is called
     * (when the user sends a message) rather than continuously tracking visibility.
     * Returns a list of video and/or slides context objects.
     *
     * Visibility: Any part of the element visible in the viewport counts.
     */
    private collectVisibleContexts(): IrisMessageContextDTO[] {
        const units = this.attachmentVideoUnits();
        if (!units || units.length === 0) {
            return [];
        }

        const contexts: IrisMessageContextDTO[] = [];

        units.forEach((unitComponent) => {
            const unit = unitComponent.lectureUnit();
            const unitId = unit?.id;

            // Skip if no ID or unit is collapsed
            if (!unitId || unitComponent.isCollapsed()) {
                return;
            }

            // Snapshot: Calculate visibility NOW (when message is sent)
            const element = document.querySelector(`[data-unit-id="${unitId}"]`);
            if (!element) {
                return;
            }

            // Check if any part of unit is in viewport
            const isUnitVisible = this.isElementVisible(element);

            if (!isUnitVisible) {
                return; // Unit not visible → skip
            }

            // Unit is visible → check individual materials (PDF viewer, video player)
            const provider = unitComponent.contextProvider();
            if (!provider) {
                return;
            }

            // Check if PDF viewer is visible
            const pdfViewer = element.querySelector('jhi-pdf-viewer');
            if (pdfViewer) {
                const isPdfVisible = this.isElementVisible(pdfViewer);

                if (isPdfVisible) {
                    const pdfPage = provider.getCurrentPdfPage?.();
                    if (pdfPage != null) {
                        const slidesContext: IrisSlidesContextDTO = {
                            type: 'slides',
                            lectureUnitId: unitId,
                            page: pdfPage,
                        };
                        contexts.push(slidesContext);
                    }
                }
            }

            // Check if video player is visible
            const videoPlayer = element.querySelector('jhi-video-player, jhi-youtube-player');
            if (videoPlayer) {
                const isVideoVisible = this.isElementVisible(videoPlayer);

                if (isVideoVisible) {
                    // Only include video context if it has been played (not just showing thumbnail)
                    const hasBeenPlayed = provider.hasVideoBeenPlayed?.() ?? false;
                    if (hasBeenPlayed) {
                        const videoTimestamp = provider.getCurrentVideoTimestamp?.();
                        if (videoTimestamp != null) {
                            const videoContext: IrisVideoContextDTO = {
                                type: 'video',
                                lectureUnitId: unitId,
                                timestamp: videoTimestamp,
                            };
                            contexts.push(videoContext);
                        }
                    }
                }
            }
        });

        return contexts;
    }
}
