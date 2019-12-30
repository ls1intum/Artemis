import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { HttpResponse } from '@angular/common/http';
import { Lecture, LectureService } from 'app/entities/lecture';
import * as moment from 'moment';
import { Attachment, AttachmentService } from 'app/entities/attachment';
import { FileService } from 'app/shared';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../course-overview.scss', './course-lectures.scss'],
})
export class CourseLectureDetailsComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    public lecture: Lecture | null;
    public isDownloadingLink: string | null;

    constructor(
        private $location: Location,
        private jhiWebsocketService: JhiWebsocketService,
        private lectureService: LectureService,
        private attachmentService: AttachmentService,
        private authServerProvider: AuthServerProvider,
        private route: ActivatedRoute,
        private router: Router,
        private fileService: FileService,
    ) {
        const navigation = this.router.getCurrentNavigation();
        if (navigation && navigation.extras.state) {
            const stateLecture = navigation.extras.state.lecture as Lecture;
            if (stateLecture && stateLecture.startDate) {
                stateLecture.startDate = moment(stateLecture.startDate);
            }
            if (stateLecture && stateLecture.endDate) {
                stateLecture.endDate = moment(stateLecture.endDate);
            }
            this.lecture = stateLecture;
        }
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe(params => {
            if (!this.lecture || this.lecture.id !== params.lectureId) {
                this.lecture = null;
                this.lectureService.find(params.lectureId).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                    this.lecture = lectureResponse.body;
                });
            }
        });
    }

    ngOnDestroy(): void {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }

    backToCourse(): void {
        this.$location.back();
    }

    attachmentNotReleased(attachment: Attachment): boolean {
        return attachment.releaseDate != null && !moment(attachment.releaseDate).isBefore(moment())!;
    }

    attachmentExtension(attachment: Attachment): string {
        if (!attachment.link) {
            return 'N/A';
        }

        return attachment.link.split('.').pop()!;
    }

    downloadAttachment(downloadUrl: string): void {
        if (!this.isDownloadingLink) {
            this.isDownloadingLink = downloadUrl;
            this.fileService.downloadAttachment(downloadUrl);
            this.isDownloadingLink = null;
        }
    }
}
