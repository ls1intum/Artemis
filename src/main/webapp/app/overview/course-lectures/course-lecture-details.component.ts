import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Lecture } from 'app/entities/lecture.model';
import { FileService } from 'app/shared/http/file.service';
import { Attachment } from 'app/entities/attachment.model';
import { LectureService } from 'app/lecture/lecture.service';
import { AttachmentService } from 'app/lecture/attachment.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { StudentQuestionsComponent } from 'app/overview/student-questions/student-questions.component';

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../course-overview.scss', './course-lectures.scss'],
})
export class CourseLectureDetailsComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    public lecture: Lecture | null;
    public isDownloadingLink: string | null;
    public lectureUnits: LectureUnit[] = [];
    readonly LectureUnitType = LectureUnitType;
    private studentQuestions?: StudentQuestionsComponent;

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
        this.subscription = this.route.params.subscribe((params) => {
            if (!this.lecture || this.lecture.id !== params.lectureId) {
                this.lecture = null;
                this.lectureService.find(params.lectureId).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                    this.lecture = lectureResponse.body;
                    if (this.lecture?.lectureUnits) {
                        this.lectureUnits = this.lecture.lectureUnits;
                    }
                    if (this.studentQuestions && this.lecture) {
                        // We need to manually update the lecture property of the student questions component
                        this.studentQuestions.lecture = this.lecture;
                        this.studentQuestions.loadQuestions(); // reload the student questions
                    }
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
            this.fileService.downloadFileWithAccessToken(downloadUrl);
            this.isDownloadingLink = null;
        }
    }

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the StudentQuestionsComponent
     * @param instance The component instance
     */
    onChildActivate(instance: StudentQuestionsComponent) {
        this.studentQuestions = instance; // save the reference to the component instance
        if (this.lecture) {
            instance.lecture = this.lecture;
            instance.loadQuestions(); // reload the student questions
        }
    }
}
