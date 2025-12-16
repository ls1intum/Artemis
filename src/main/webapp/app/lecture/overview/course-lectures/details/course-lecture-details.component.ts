import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PROFILE_IRIS, addPublicFilePrefix } from 'app/app.constants';
import { downloadStream } from 'app/shared/util/download.util';
import dayjs, { Dayjs } from 'dayjs/esm';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { finalize, tap } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import { faChalkboardTeacher, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { ScienceEventType } from 'app/shared/science/science.model';
import { Subscription } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UpperCasePipe } from '@angular/common';
import { ExerciseUnitComponent } from '../exercise-unit/exercise-unit.component';
import { AttachmentVideoUnitComponent } from '../attachment-video-unit/attachment-video-unit.component';
import { TextUnitComponent } from '../text-unit/text-unit.component';
import { OnlineUnitComponent } from '../online-unit/online-unit.component';
import { CompetenciesPopoverComponent } from 'app/atlas/shared/competencies-popover/competencies-popover.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { InformationBox, InformationBoxComponent, InformationBoxContent } from 'app/shared/information-box/information-box.component';

export interface LectureUnitCompletionEvent {
    lectureUnit: LectureUnit;
    completed: boolean;
}

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../../../../core/course/overview/course-overview/course-overview.scss', '../../../shared/course-lectures/course-lectures.scss'],
    imports: [
        TranslateDirective,
        ExerciseUnitComponent,
        AttachmentVideoUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        CompetenciesPopoverComponent,
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

    ngOnInit(): void {
        this.irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);

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
                        if (this.lectureUnits?.length) {
                            // Check if PDF attachments exist in lecture units
                            this.hasPdfLectureUnit =
                                (<AttachmentVideoUnit[]>this.lectureUnits.filter((unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO)).filter(
                                    (unit) => unit.attachment?.link?.split('.').pop()!.toLocaleLowerCase() === 'pdf',
                                ).length > 0;
                        }
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
}
