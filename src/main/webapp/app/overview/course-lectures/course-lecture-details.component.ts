import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Lecture } from 'app/entities/lecture.model';
import { FileService } from 'app/shared/http/file.service';
import { Attachment } from 'app/entities/attachment.model';
import { LectureService } from 'app/lecture/lecture.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { StudentQuestionsComponent } from 'app/overview/student-questions/student-questions.component';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';

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
    studentQuestions?: StudentQuestionsComponent;

    readonly LectureUnitType = LectureUnitType;

    constructor(private alertService: JhiAlertService, private lectureService: LectureService, private activatedRoute: ActivatedRoute, private fileService: FileService) {}

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
            .find(this.lectureId!)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (findLectureResult) => {
                    this.lecture = findLectureResult.body!;
                    if (this.lecture?.lectureUnits) {
                        this.lectureUnits = this.lecture.lectureUnits;
                    }
                    if (this.studentQuestions) {
                        // We need to manually update the lecture property of the student questions component
                        this.studentQuestions.lecture = this.lecture;
                        this.studentQuestions.loadQuestions(); // reload the student questions
                    }
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
    attachmentNotReleased(attachment: Attachment): boolean {
        return attachment.releaseDate != undefined && !moment(attachment.releaseDate).isBefore(moment())!;
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
            this.isDownloadingLink = undefined;
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
