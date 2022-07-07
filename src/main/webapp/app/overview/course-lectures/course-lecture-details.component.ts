import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { FileService } from 'app/shared/http/file.service';
import { Attachment } from 'app/entities/attachment.model';
import { LectureService } from 'app/lecture/lecture.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';

export interface LectureUnitCompletionEvent {
    lectureUnit: LectureUnit;
    completed: boolean;
}

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../course-overview.scss', './course-lectures.scss'],
})
export class CourseLectureDetailsComponent implements OnInit {
    lectureId?: number;
    isLoading = false;
    lecture?: Lecture;
    isDownloadingLink?: string;
    lectureUnits: LectureUnit[] = [];
    discussionComponent?: DiscussionSectionComponent;
    hasPdfLectureUnit: boolean;

    readonly LectureUnitType = LectureUnitType;

    // Icons
    faSpinner = faSpinner;

    constructor(
        private alertService: AlertService,
        private lectureService: LectureService,
        private lectureUnitService: LectureUnitService,
        private activatedRoute: ActivatedRoute,
        private fileService: FileService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params['lectureId'];
            if (this.lectureId) {
                this.loadData();
            }
        });
    }

    loadData() {
        this.isLoading = true;
        this.lectureService
            .findWithDetails(this.lectureId!)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (findLectureResult) => {
                    this.lecture = findLectureResult.body!;
                    if (this.lecture?.lectureUnits) {
                        this.lectureUnits = this.lecture.lectureUnits;

                        // Check if PDF attachments exist in lecture units
                        this.hasPdfLectureUnit =
                            (<AttachmentUnit[]>this.lectureUnits.filter((unit) => unit.type === LectureUnitType.ATTACHMENT)).filter(
                                (unit) => unit.attachment?.link?.split('.').pop()!.toLocaleLowerCase() === 'pdf',
                            ).length > 0;
                    }
                    if (this.discussionComponent) {
                        // We need to manually update the lecture property of the student questions component
                        this.discussionComponent.lecture = this.lecture;
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
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
            this.fileService.downloadFileWithAccessToken(downloadUrl);
            this.isDownloadingLink = undefined;
        }
    }

    downloadMergedFiles(): void {
        if (this.lecture?.course?.id && this.lectureId) {
            this.fileService.downloadMergedFileWithAccessToken(this.lecture.course.id, this.lectureId);
        }
    }

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        if (this.lecture && event.lectureUnit.visibleToStudents && event.lectureUnit.completed !== event.completed) {
            this.lectureUnitService.setCompletion(event.lectureUnit.id!, this.lecture.id!, event.completed).subscribe({
                next: () => {
                    event.lectureUnit.completed = event.completed;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        }
    }

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the DiscussionComponent
     * @param instance The component instance
     */
    onChildActivate(instance: DiscussionSectionComponent) {
        this.discussionComponent = instance; // save the reference to the component instance
        if (this.lecture) {
            instance.lecture = this.lecture;
        }
    }
}
