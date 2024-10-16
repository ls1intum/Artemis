import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { downloadStream } from 'app/shared/util/download.util';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { FileService } from 'app/shared/http/file.service';
import { Attachment } from 'app/entities/attachment.model';
import { LectureService } from 'app/lecture/lecture.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { finalize, tap } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceEventType } from 'app/shared/science/science.model';
import { Subscription } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

export interface LectureUnitCompletionEvent {
    lectureUnit: LectureUnit;
    completed: boolean;
}

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../course-overview.scss', './course-lectures.scss'],
})
export class CourseLectureDetailsComponent extends AbstractScienceComponent implements OnInit, OnDestroy {
    private alertService = inject(AlertService);
    private lectureService = inject(LectureService);
    private lectureUnitService = inject(LectureUnitService);
    private activatedRoute = inject(ActivatedRoute);
    private fileService = inject(FileService);
    private router = inject(Router);
    private profileService = inject(ProfileService);

    lectureId?: number;
    courseId?: number;
    isLoading = false;
    lecture?: Lecture;
    isDownloadingLink?: string;
    lectureUnits: LectureUnit[] = [];
    hasPdfLectureUnit: boolean;

    paramsSubscription: Subscription;
    profileSubscription?: Subscription;
    isProduction = true;
    isTestServer = false;
    endsSameDay = false;

    readonly LectureUnitType = LectureUnitType;
    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    // Icons
    faSpinner = faSpinner;

    constructor() {
        super(ScienceEventType.LECTURE__OPEN);
    }

    ngOnInit(): void {
        this.paramsSubscription = this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params.lectureId;
            if (this.lectureId) {
                // science logging
                this.setResourceId(this.lectureId);
                this.logEvent();

                this.loadData();
            }
        });

        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
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
                        this.lectureUnits = this.lecture?.lectureUnits ?? [];
                        if (this.lectureUnits?.length) {
                            // Check if PDF attachments exist in lecture units
                            this.hasPdfLectureUnit =
                                (<AttachmentUnit[]>this.lectureUnits.filter((unit) => unit.type === LectureUnitType.ATTACHMENT)).filter(
                                    (unit) => unit.attachment?.link?.split('.').pop()!.toLocaleLowerCase() === 'pdf',
                                ).length > 0;
                        }
                        this.endsSameDay = !!this.lecture?.startDate && !!this.lecture.endDate && dayjs(this.lecture.startDate).isSame(this.lecture.endDate, 'day');
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

    downloadAttachment(downloadUrl?: string): void {
        if (!this.isDownloadingLink && downloadUrl) {
            this.isDownloadingLink = downloadUrl;
            this.fileService.downloadFile(downloadUrl);
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

    ngOnDestroy() {
        this.paramsSubscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
    }
}
