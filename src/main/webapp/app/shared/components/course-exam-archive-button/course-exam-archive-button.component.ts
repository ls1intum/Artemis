import { Component, Input, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { tap } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam/exam.model';
import dayjs from 'dayjs/esm';
import { ButtonSize } from '../button.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { faArchive, faCircleNotch, faDownload, faEraser } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

export type CourseExamArchiveState = {
    exportState: 'COMPLETED' | 'RUNNING' | 'COMPLETED_WITH_WARNINGS' | 'COMPLETED_WITH_ERRORS';
    message: string;
    subMessage?: string;
};

@Component({
    selector: 'jhi-course-exam-archive-button',
    templateUrl: './course-exam-archive-button.component.html',
    styleUrls: ['./course-exam-archive-button.component.scss'],
    styles: [':host {display: contents}'],
})
export class CourseExamArchiveButtonComponent implements OnInit, OnDestroy {
    ButtonSize = ButtonSize;
    ActionType = ActionType;
    readonly FeatureToggle = FeatureToggle;

    @Input()
    archiveMode: 'Exam' | 'Course' = 'Course';

    @Input()
    course: Course;

    @Input()
    exam?: Exam;

    @ViewChild('archiveCompleteWithWarningsModal', { static: false })
    archiveCompleteWithWarningsModal: TemplateRef<any>;

    @ViewChild('archiveConfirmModal', { static: false })
    archiveConfirmModal: TemplateRef<any>;

    isBeingArchived = false;
    archiveButtonText = '';
    archiveWarnings: string[] = [];
    displayDownloadArchiveButton = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faDownload = faDownload;
    faCircleNotch = faCircleNotch;
    faEraser = faEraser;
    faArchive = faArchive;

    constructor(
        private courseService: CourseManagementService,
        private examService: ExamManagementService,
        private alertService: AlertService,
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
        this.displayDownloadArchiveButton = this.canDownloadArchive();

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

    registerArchiveWebsocket() {
        const topic = this.getArchiveStateTopic();
        this.websocketService.subscribe(topic);
        this.websocketService
            .receive(topic)
            .pipe(tap((archiveState: CourseExamArchiveState) => this.handleArchiveStateChanges(archiveState)))
            .subscribe();
    }

    handleArchiveStateChanges(courseArchiveState: CourseExamArchiveState) {
        const { exportState, message, subMessage } = courseArchiveState;
        this.isBeingArchived = exportState === 'RUNNING';
        this.archiveButtonText = exportState === 'RUNNING' ? message : this.getArchiveButtonText();
        if (exportState === 'COMPLETED') {
            this.alertService.success(this.getArchiveSuccessText());
            this.reloadCourseOrExam();
        } else if (exportState === 'COMPLETED_WITH_WARNINGS') {
            this.archiveWarnings = message.split('\n');
            this.openModal(this.archiveCompleteWithWarningsModal);
            this.reloadCourseOrExam();
        } else if (exportState === 'COMPLETED_WITH_ERRORS') {
            this.alertService.error(this.getArchiveErrorText(subMessage!));
        }
    }

    reloadCourseOrExam() {
        if (this.archiveMode === 'Exam' && this.exam) {
            this.examService.find(this.course.id!, this.exam.id!).subscribe((res) => {
                this.exam = res.body!;
                this.displayDownloadArchiveButton = this.canDownloadArchive();
            });
        } else {
            this.courseService.find(this.course.id!).subscribe((res) => {
                this.course = res.body!;
                this.displayDownloadArchiveButton = this.canDownloadArchive();
            });
        }
    }

    getArchiveSuccessText() {
        if (this.archiveMode === 'Course') {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveCourseSuccess');
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExamSuccess');
        }
    }

    getArchiveErrorText(message: string) {
        if (this.archiveMode === 'Course') {
            return this.translateService.instant(`artemisApp.courseExamArchive.archiveCourseError.${message}`, { courseName: this.course.title });
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExamError', { examName: this.exam?.title });
        }
    }

    getArchiveButtonText() {
        if (this.archiveMode === 'Course') {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveCourse');
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExam');
        }
    }

    getArchiveStateTopic() {
        if (this.archiveMode === 'Exam' && this.exam) {
            return '/topic/exams/' + this.exam.id + '/export';
        } else {
            return '/topic/courses/' + this.course.id + '/export-course';
        }
    }

    canArchive() {
        let isOver: boolean;
        if (this.archiveMode === 'Exam' && this.exam) {
            isOver = !!this.exam.endDate?.isBefore(dayjs());
        } else {
            isOver = !!this.course.endDate?.isBefore(dayjs());
        }
        return this.accountService.isAtLeastInstructorInCourse(this.course) && isOver;
    }

    openModal(modalRef: TemplateRef<any>) {
        this.modalService.open(modalRef).result.then(
            (result: string) => {
                if (result === 'archive-confirm' && this.canDownloadArchive()) {
                    this.openModal(this.archiveConfirmModal);
                }
                if (result === 'archive' || !this.canDownloadArchive()) {
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
        let hasArchive: boolean;
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
            this.examService.downloadExamArchive(this.course.id!, this.exam.id!);
        } else {
            this.courseService.downloadCourseArchive(this.course.id!);
        }
    }

    canCleanup() {
        let hasBeenArchived: boolean;
        if (this.archiveMode === 'Exam' && this.exam) {
            hasBeenArchived = !!this.exam.examArchivePath && this.exam.examArchivePath.length > 0;
        } else {
            hasBeenArchived = !!this.course.courseArchivePath && this.course.courseArchivePath.length > 0;
        }
        // A course / exam can only be cleaned up if the course / exam has been archived.
        return this.accountService.isAtLeastInstructorInCourse(this.course) && hasBeenArchived;
    }

    cleanup() {
        if (this.archiveMode === 'Exam' && this.exam) {
            this.examService.cleanupExam(this.course.id!, this.exam.id!).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.programmingExercise.cleanup.successMessageCleanup');
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.dialogErrorSource.next(error.error.title);
                },
            });
        } else {
            this.courseService.cleanupCourse(this.course.id!).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.programmingExercise.cleanup.successMessageCleanup');
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.dialogErrorSource.next(error.error.title);
                },
            });
        }
    }
}
