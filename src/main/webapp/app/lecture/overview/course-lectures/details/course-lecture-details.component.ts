import { Component, DestroyRef, OnDestroy, OnInit, inject, signal, viewChildren } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MODULE_FEATURE_IRIS, addPublicFilePrefix } from 'app/app.constants';
import { downloadStream } from 'app/foundation/util/download.util';
import dayjs, { Dayjs } from 'dayjs/esm';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { onError } from 'app/foundation/util/global.utils';
import { finalize, tap } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';
import { faChalkboardTeacher, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { Subscription } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UpperCasePipe } from '@angular/common';
import { ExerciseUnitComponent } from '../exercise-unit/exercise-unit.component';
import { AttachmentVideoUnitComponent } from '../attachment-video-unit/attachment-video-unit.component';
import { TextUnitComponent } from '../text-unit/text-unit.component';
import { OnlineUnitComponent } from '../online-unit/online-unit.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { FileService } from 'app/foundation/service/file.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { InformationBox, InformationBoxComponent, InformationBoxContent } from 'app/shared-ui/information-box/information-box.component';
import { IrisMessageContextDTO, IrisSlidesContextDTO, IrisVideoContextDTO, LectureContextsProvider } from 'app/iris/shared/entities/iris-message-context-dto.model';

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
        DiscussionSectionComponent,
        UpperCasePipe,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
        IrisExerciseChatbotButtonComponent,
        InformationBoxComponent,
    ],
})
export class CourseLectureDetailsComponent implements OnInit, OnDestroy {
    private readonly alertService = inject(AlertService);
    private readonly lectureService = inject(LectureService);
    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly fileService = inject(FileService);
    private readonly router = inject(Router);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly scienceService = inject(ScienceService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly LectureUnitType = LectureUnitType;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;
    protected readonly isMessagingEnabled = isMessagingEnabled;
    protected readonly ChatServiceMode = ChatServiceMode;

    protected readonly faSpinner = faSpinner;
    protected readonly faChalkboardTeacher = faChalkboardTeacher;

    lectureId?: number;
    courseId?: number;
    isLoading = false;
    lecture?: Lecture;
    isDownloadingLink?: string;
    lectureUnits: LectureUnit[] = [];
    hasPdfLectureUnit: boolean;
    irisSettings?: IrisCourseSettingsWithRateLimitDTO;
    paramsSubscription: Subscription;
    courseParamsSubscription: Subscription;
    irisEnabled = false;
    informationBoxData: InformationBox[] = [];

    readonly targetUnitId = signal<number | undefined>(undefined);
    readonly targetVideoTimestamp = signal<number | undefined>(undefined);
    readonly targetPdfPage = signal<number | undefined>(undefined);

    // Signal to track when lecture units change (used in effect below)

    private readonly lectureUnitsSignal = signal<LectureUnit[]>([]);

    // ViewChildren to access all attachment/video unit components
    private readonly attachmentVideoUnits = viewChildren(AttachmentVideoUnitComponent);

    // Context provider for the chatbot
    readonly contextsProvider: LectureContextsProvider = {
        getVisibleContexts: () => this.collectVisibleContexts(),
    };

    /** Builds the context provider function for the chatbot button */
    protected getContextProvider(): (() => IrisMessageContextDTO[]) | undefined {
        return () => this.collectVisibleContexts();
    }

    ngOnInit(): void {
        this.irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);

        // As defined in courses.route.ts, the courseId is in the grand parent route of the lectureId route.
        const grandParentRoute = this.activatedRoute.parent?.parent;
        if (grandParentRoute) {
            this.courseParamsSubscription = grandParentRoute.params.subscribe((params) => {
                // Note: if courseId is not found, sub components cannot navigate properly
                this.courseId = +params.courseId;
            });
        }

        this.paramsSubscription = this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params.lectureId;
            if (this.lectureId) {
                this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN, this.lectureId);
                this.loadData();
            }
        });

        this.activatedRoute.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const unitId = Number(params['unit']);
            if (Number.isInteger(unitId) && unitId > 0) {
                this.targetUnitId.set(unitId);
                const timestamp = Number(params['timestamp']);
                this.targetVideoTimestamp.set(Number.isFinite(timestamp) && timestamp >= 0 ? timestamp : undefined);
                const pageNum = Number(params['page']);
                this.targetPdfPage.set(Number.isInteger(pageNum) && pageNum > 0 ? pageNum : undefined);
            } else {
                this.targetUnitId.set(undefined);
                this.targetVideoTimestamp.set(undefined);
                this.targetPdfPage.set(undefined);
            }

            if (this.lectureUnits.length > 0) {
                this.ensureValidDeepLinkTargets();
            }
        });
    }

    loadData() {
        this.isLoading = true;
        if (this.lectureId) {
            this.lectureService
                .findWithDetails(this.lectureId)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe({
                    next: (findLectureResult) => {
                        this.lecture = findLectureResult.body!;
                        this.lecture?.attachments?.forEach((attachment) => {
                            if (attachment.link) {
                                attachment.linkUrl = addPublicFilePrefix(attachment.link);
                            }
                        });

                        this.lectureUnits = this.lecture?.lectureUnits ?? [];
                        this.lectureUnitsSignal.set(this.lectureUnits); // Trigger effect
                        this.ensureValidDeepLinkTargets();
                        this.hasPdfLectureUnit = this.lectureUnits.some(
                            (unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO && (unit as AttachmentVideoUnit).attachment?.link?.toLowerCase().endsWith('.pdf'),
                        );
                        if (this.irisEnabled && this.lecture?.course?.id) {
                            this.irisSettingsService.getCourseSettingsWithRateLimit(this.lecture.course.id).subscribe((response) => {
                                this.irisSettings = response;
                            });
                        }
                        this.informationBoxData = [];
                        if (this.lecture?.startDate) {
                            const startDateInfoBoxTitle = 'artemisApp.courseOverview.lectureDetails.startDate';
                            const infoBoxStartDate = this.createDateInfoBox(this.lecture!.startDate, startDateInfoBoxTitle);
                            this.informationBoxData.push(infoBoxStartDate);
                        }
                        if (this.lecture?.endDate) {
                            const endDateInfoBoxTitle = 'artemisApp.courseOverview.lectureDetails.endDate';
                            const infoBoxEndDate = this.createDateInfoBox(this.lecture!.endDate, endDateInfoBoxTitle);
                            this.informationBoxData.push(infoBoxEndDate);
                        }
                    },
                    error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                });
        }
    }

    redirectToLectureManagement(): void {
        this.router.navigate(['course-management', this.lecture?.course?.id, 'lectures', this.lecture?.id]);
    }

    attachmentNotReleased(attachment: Attachment): boolean {
        return attachment.releaseDate != undefined && !dayjs(attachment.releaseDate).isBefore(dayjs())!;
    }

    attachmentExtension(attachment: Attachment): string {
        if (!attachment.link) {
            return 'N/A';
        }

        return attachment.link.split('.').pop()!;
    }

    downloadAttachment(downloadUrl?: string, downloadName?: string): void {
        if (!this.isDownloadingLink && downloadUrl && downloadName) {
            this.isDownloadingLink = downloadUrl;
            this.fileService.downloadFileByAttachmentName(downloadUrl, downloadName);
            this.isDownloadingLink = undefined;
        }
    }

    downloadMergedFiles(): void {
        if (this.lectureId) {
            this.fileService
                .downloadMergedFile(this.lectureId)
                .pipe(
                    tap((blob) => {
                        downloadStream(blob.body, 'application/pdf', this.lecture?.title ?? 'Lecture');
                        this.loadData();
                    }),
                )
                .subscribe();
        }
    }

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        this.lectureUnitService.completeLectureUnit(this.lecture!, event);
    }

    private ensureValidDeepLinkTargets(): void {
        const targetUnitId = this.targetUnitId();
        if (!targetUnitId) {
            return;
        }

        const targetUnit = this.lectureUnits.find((unit) => unit.id === targetUnitId);
        if (!targetUnit) {
            this.targetUnitId.set(undefined);
            this.targetVideoTimestamp.set(undefined);
            this.targetPdfPage.set(undefined);
            return;
        }

        if (targetUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            const attachmentUnit = targetUnit as AttachmentVideoUnit;
            const hasVideo = !!attachmentUnit.videoSource || !!attachmentUnit.youtubeVideoId;
            const isPdf = attachmentUnit.attachment?.link?.toLowerCase().endsWith('.pdf');
            // Clear timestamp only if unit has NO video source
            if (!hasVideo) {
                this.targetVideoTimestamp.set(undefined);
            }
            // Clear PDF page only if unit has NO PDF attachment
            if (!isPdf) {
                this.targetPdfPage.set(undefined);
            }
        } else {
            this.targetVideoTimestamp.set(undefined);
            this.targetPdfPage.set(undefined);
        }
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

            const rect = element.getBoundingClientRect();
            const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
            const viewportWidth = window.innerWidth || document.documentElement.clientWidth;

            // Check if any part of unit is in viewport
            const isUnitVisible = rect.top < viewportHeight && rect.bottom > 0 && rect.left < viewportWidth && rect.right > 0;

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
                const pdfRect = pdfViewer.getBoundingClientRect();
                const isPdfVisible = pdfRect.top < viewportHeight && pdfRect.bottom > 0;

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
                const videoRect = videoPlayer.getBoundingClientRect();
                const isVideoVisible = videoRect.top < viewportHeight && videoRect.bottom > 0;

                if (isVideoVisible) {
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
        });

        return contexts;
    }
}
