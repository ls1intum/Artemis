import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject, input } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { tap } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject, Subscription } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { faArchive, faCircleNotch, faDownload, faEraser } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggleDirective } from '../../../feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from '../../../delete-dialog/directive/delete-button.directive';

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
    imports: [TranslateDirective, FeatureToggleDirective, FaIconComponent, DeleteButtonDirective],
})
export class CourseExamArchiveButtonComponent implements OnInit, OnDestroy {
    private courseService = inject(CourseManagementService);
    private examService = inject(ExamManagementService);
    private alertService = inject(AlertService);
    private websocketService = inject(WebsocketService);
    private translateService = inject(TranslateService);
    private modalService = inject(NgbModal);
    private accountService = inject(AccountService);

    ButtonSize = ButtonSize;
    ActionType = ActionType;
    readonly FeatureToggle = FeatureToggle;

    readonly archiveMode = input<'Exam' | 'Course'>('Course');

    readonly course = input<Course>(undefined!);

    readonly exam = input<Exam>();

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
    private archiveSubscription?: Subscription;

    // Icons
    faDownload = faDownload;
    faCircleNotch = faCircleNotch;
    faEraser = faEraser;
    faArchive = faArchive;

    ngOnInit() {
        if (!this.course() && !this.exam()) {
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
        this.archiveSubscription?.unsubscribe();
        this.dialogErrorSource.unsubscribe();
    }

    registerArchiveWebsocket() {
        const topic = this.getArchiveStateTopic();
        this.archiveSubscription = this.websocketService
            .subscribe<CourseExamArchiveState>(topic)
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
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.find(this.course().id!, exam.id!).subscribe((res) => {
                this.exam = res.body!;
                this.displayDownloadArchiveButton = this.canDownloadArchive();
            });
        } else {
            this.courseService.find(this.course().id!).subscribe((res) => {
                this.course = res.body!;
                this.displayDownloadArchiveButton = this.canDownloadArchive();
            });
        }
    }

    getArchiveSuccessText() {
        if (this.archiveMode() === 'Course') {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveCourseSuccess');
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExamSuccess');
        }
    }

    getArchiveErrorText(message: string) {
        if (this.archiveMode() === 'Course') {
            return this.translateService.instant(`artemisApp.courseExamArchive.archiveCourseError.${message}`, { courseName: this.course().title });
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExamError', { examName: this.exam()?.title });
        }
    }

    getArchiveButtonText() {
        if (this.archiveMode() === 'Course') {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveCourse');
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExam');
        }
    }

    getArchiveStateTopic() {
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            return '/topic/exams/' + exam.id + '/export';
        } else {
            return '/topic/courses/' + this.course().id + '/export-course';
        }
    }

    canArchive() {
        let isOver: boolean;
        const course = this.course();
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            isOver = !!exam.endDate?.isBefore(dayjs());
        } else {
            isOver = !!course.endDate?.isBefore(dayjs());
        }
        return this.accountService.isAtLeastInstructorInCourse(course) && isOver;
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
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.archiveExam(this.course().id!, exam.id!).subscribe();
        } else {
            this.courseService.archiveCourse(this.course().id!).subscribe();
        }
    }

    canDownloadArchive() {
        let hasArchive: boolean;
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            hasArchive = (exam.examArchivePath?.length ?? 0) > 0;
        } else {
            hasArchive = (this.course().courseArchivePath?.length ?? 0) > 0;
        }
        // You can only download one if the path to the archive is present
        return this.accountService.isAtLeastInstructorInCourse(this.course()) && hasArchive;
    }

    downloadArchive() {
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.downloadExamArchive(this.course().id!, exam.id!);
        } else {
            this.courseService.downloadCourseArchive(this.course().id!);
        }
    }

    canCleanup() {
        let hasBeenArchived: boolean;
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            hasBeenArchived = !!exam.examArchivePath && exam.examArchivePath.length > 0;
        } else {
            const course = this.course();
            hasBeenArchived = !!course.courseArchivePath && course.courseArchivePath.length > 0;
        }
        // A course / exam can only be cleaned up if the course / exam has been archived.
        return this.accountService.isAtLeastInstructorInCourse(this.course()) && hasBeenArchived;
    }

    cleanup() {
        const exam = this.exam();
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.cleanupExam(this.course().id!, exam.id!).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.programmingExercise.cleanup.successMessageCleanup');
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.dialogErrorSource.next(error.error.title);
                },
            });
        } else {
            this.courseService.cleanupCourse(this.course().id!).subscribe({
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
