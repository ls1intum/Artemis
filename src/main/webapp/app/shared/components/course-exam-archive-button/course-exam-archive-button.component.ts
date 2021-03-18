import { Component, Input, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { tap } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { ButtonSize } from '../button.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';

type CourseExamArchiveState = {
    exportState: 'COMPLETED' | 'RUNNING';
    progress: string;
};

@Component({
    selector: 'jhi-course-exam-archive-button',
    templateUrl: './course-exam-archive-button.component.html',
    styles: [],
})
export class CourseExamArchiveButtonComponent implements OnInit, OnDestroy {
    ButtonSize = ButtonSize;
    ActionType = ActionType;

    @Input()
    archiveMode: 'Exam' | 'Course' = 'Course';

    @Input()
    course: Course;

    @Input()
    exam?: Exam;

    isBeingArchived = false;
    archiveButtonText = '';

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private courseService: CourseManagementService,
        private examService: ExamManagementService,
        private jhiAlertService: JhiAlertService,
        private websocketService: JhiWebsocketService,
        private translateService: TranslateService,
        private modalService: NgbModal,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        if (!this.course && !this.exam) {
            // Component isn't initialized
            return;
        }

        this.registerArchiveWebsocket();
        this.archiveButtonText = this.getArchiveButtonText();

        // update the span title on each language change
        this.translateService.onLangChange.subscribe(() => {
            if (!this.isBeingArchived) {
                this.archiveButtonText = this.getArchiveButtonText();
            }
        });
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        this.websocketService.unsubscribe(this.getArchiveStateTopic());
        this.dialogErrorSource.unsubscribe();
    }

    private registerArchiveWebsocket() {
        const topic = this.getArchiveStateTopic();
        this.websocketService.subscribe(topic);
        this.websocketService
            .receive(topic)
            .pipe(tap((archiveState: CourseExamArchiveState) => this.handleArchiveStateChanges(archiveState)))
            .subscribe();
    }

    private handleArchiveStateChanges(courseArchiveState: CourseExamArchiveState) {
        const { exportState, progress } = courseArchiveState;
        this.isBeingArchived = exportState === 'RUNNING';
        this.archiveButtonText = exportState === 'RUNNING' ? progress : this.getArchiveButtonText();

        if (exportState === 'COMPLETED') {
            this.jhiAlertService.success(this.getArchiveSuccessText());
            this.reloadCourseOrExam();
        }
    }

    private reloadCourseOrExam() {
        if (this.archiveMode === 'Exam' && this.exam) {
            this.examService.find(this.course.id!, this.exam.id!).subscribe((res) => {
                this.exam = res.body!;
            });
        } else {
            this.courseService.find(this.course.id!).subscribe((res) => {
                this.course = res.body!;
            });
        }
    }

    private getArchiveSuccessText() {
        if (this.archiveMode === 'Course') {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveCourseSuccess');
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExamSuccess');
        }
    }

    private getArchiveButtonText() {
        if (this.archiveMode === 'Course') {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveCourse');
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExam');
        }
    }

    private getArchiveStateTopic() {
        if (this.archiveMode === 'Exam' && this.exam) {
            return '/topic/exams/' + this.exam.id + '/export';
        } else {
            return '/topic/courses/' + this.course.id + '/export-course';
        }
    }

    canArchive() {
        let isOver: boolean;
        if (this.archiveMode === 'Exam' && this.exam) {
            isOver = !!this.exam.endDate?.isBefore(moment());
        } else {
            isOver = !!this.course.endDate?.isBefore(moment());
        }
        return this.accountService.isAtLeastInstructorInCourse(this.course) && isOver;
    }

    /**
     *
     * @param archiveCourseWarningPopup
     */
    openArchiveWarningModal(archiveCourseWarningPopup: TemplateRef<any>) {
        this.modalService.open(archiveCourseWarningPopup).result.then(
            (result: string) => {
                if (result === 'archive') {
                    this.archive();
                }
            },
            () => {},
        );
    }

    archive() {
        if (this.archiveMode === 'Exam' && this.exam) {
            this.examService.archiveExam(this.course.id!, this.exam.id!).subscribe();
        } else {
            this.courseService.archiveCourse(this.course.id!).subscribe();
        }
    }

    canDownloadArchive() {
        let hasArchive = false;
        if (this.archiveMode === 'Exam' && this.exam) {
            hasArchive = (this.exam.examArchivePath?.length ?? 0) > 0;
        } else {
            hasArchive = (this.course.courseArchivePath?.length ?? 0) > 0;
        }

        // You can only download one if the path to the archive is present
        return this.accountService.isAtLeastInstructorInCourse(this.course) && hasArchive;
    }

    downloadArchive() {
        if (this.archiveMode === 'Exam' && this.exam) {
            this.examService.downloadExamArchive(this.course.id!, this.exam.id!).subscribe(
                (response) => downloadZipFileFromResponse(response),
                () => this.jhiAlertService.error('artemisApp.courseExamArchive.archiveDownloadError'),
            );
        } else {
            this.courseService.downloadCourseArchive(this.course.id!).subscribe(
                (response) => downloadZipFileFromResponse(response),
                () => this.jhiAlertService.error('artemisApp.courseExamArchive.archiveDownloadError'),
            );
        }
    }

    canCleanupCourse() {
        if (this.archiveMode !== 'Course') {
            return;
        }

        // A course can only be cleaned up if the course has been archived.
        const canCleanup = !!this.course.courseArchivePath && this.course.courseArchivePath.length > 0;
        return this.accountService.isAtLeastInstructorInCourse(this.course) && canCleanup;
    }

    cleanupCourse() {
        if (this.archiveMode !== 'Course') {
            return;
        }

        this.courseService.cleanupCourse(this.course.id!).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessage');
                this.dialogErrorSource.next('');
            },
            (error) => {
                this.dialogErrorSource.next(error.error.title);
            },
        );
    }
}
