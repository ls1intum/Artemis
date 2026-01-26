import { Component, OnDestroy, OnInit, TemplateRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
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
    private websocketRegistered = false;

    ButtonSize = ButtonSize;
    ActionType = ActionType;
    FeatureToggle = FeatureToggle;

    // signals
    archiveMode = input<'Exam' | 'Course'>('Course');
    course = input<Course | undefined>(undefined);
    currentCourse = signal<Course | undefined>(undefined);
    exam = input<Exam | undefined>(undefined);
    currentExam = signal<Exam | undefined>(undefined);
    currentLang = signal(this.translateService.getCurrentLang() ?? '');
    archiveState = signal<CourseExamArchiveState | null>(null);

    // Derived State / Computed
    isBeingArchived = computed(() => this.archiveState()?.exportState === 'RUNNING');
    archiveStateMessage = computed(() => this.archiveState()?.message ?? '');
    archiveWarnings = computed(() => (this.archiveState()?.exportState === 'COMPLETED_WITH_WARNINGS' ? (this.archiveState()?.message ?? '').split('\n') : []));
    displayDownloadArchiveButton = computed(() => this.canDownloadArchive());
    archiveButtonText = computed(() => {
        // recompute whenever language changes
        this.currentLang();
        if (this.isBeingArchived()) {
            // show the websocket-provided message while running
            return this.archiveStateMessage();
        }

        return this.archiveMode() === 'Course'
            ? this.translateService.instant('artemisApp.courseExamArchive.archiveCourse')
            : this.translateService.instant('artemisApp.courseExamArchive.archiveExam');
    });
    canArchive = computed(() => {
        const mode = this.archiveMode();
        const course = this.currentCourse();
        const exam = this.currentExam();
        let isOver = false;
        if (mode === 'Exam' && exam) {
            isOver = !!exam.endDate?.isBefore(dayjs());
        } else if (course) {
            isOver = !!course.endDate?.isBefore(dayjs());
        }
        return this.accountService.isAtLeastInstructorInCourse(course) && isOver;
    });
    canDownloadArchive = computed(() => {
        const mode = this.archiveMode();
        const exam = this.currentExam();
        const course = this.currentCourse();

        let hasArchive = false;

        if (mode === 'Exam' && exam) {
            hasArchive = (exam.examArchivePath?.length ?? 0) > 0;
        } else if (course) {
            hasArchive = (course.courseArchivePath?.length ?? 0) > 0;
        }
        // You can only download one if the path to the archive is present
        return this.accountService.isAtLeastInstructorInCourse(course) && hasArchive;
    });
    canCleanup = computed(() => {
        const mode = this.archiveMode();
        const exam = this.currentExam();
        const course = this.currentCourse();

        let hasBeenArchived = false;

        if (mode === 'Exam' && exam) {
            hasBeenArchived = !!exam.examArchivePath && exam.examArchivePath.length > 0;
        } else if (course) {
            hasBeenArchived = !!course.courseArchivePath && course.courseArchivePath.length > 0;
        }

        // A course / exam can only be cleaned up if the course / exam has been archived.
        return this.accountService.isAtLeastInstructorInCourse(course) && hasBeenArchived;
    });

    archiveCompleteWithWarningsModal = viewChild.required<TemplateRef<any>>('archiveCompleteWithWarningsModal');

    archiveConfirmModal = viewChild.required<TemplateRef<any>>('archiveConfirmModal');

    // Subscriptions
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private archiveSubscription?: Subscription;
    private changeLangSubscription?: Subscription;

    // Icons
    faDownload = faDownload;
    faCircleNotch = faCircleNotch;
    faEraser = faEraser;
    faArchive = faArchive;

    /**
     * Since course and exam are input signals, their state should not be owned by this component, instead we use
     * writeable signals (currentCourse, currentExam) for each of these input signals which controls the internal
     * state of this component.
     */
    constructor() {
        effect(() => {
            const courseFromParent = this.course();
            const courseId = courseFromParent?.id ?? null;
            const currentCourseId = this.currentCourse()?.id ?? null;

            // only set value from parent if course has not been fetched in this component or refetched course id differs for some reason
            if (currentCourseId === null || courseId !== currentCourseId) {
                // current Course is registered in effect, we do not track it to prevent feedback loop
                untracked(() => this.currentCourse.set(courseFromParent));
            }
        });

        effect(() => {
            const examFromParent = this.exam();
            const examId = examFromParent?.id ?? null;
            const currentExamId = this.currentExam()?.id ?? null;

            // only set value from parent if exam has not been fetched in this component or refetched exam id differs for some reason
            if (currentExamId === null || examId !== currentExamId) {
                // current Exam is registered in effect, we do not track it to prevent feedback loop
                untracked(() => this.currentExam.set(examFromParent));
            }
        });

        effect(() => {
            if (this.websocketRegistered) return;

            const topic = this.getArchiveStateTopic();
            if (!topic) return;

            this.websocketRegistered = true;
            this.registerArchiveWebsocket(topic);
        });
    }

    ngOnInit() {
        // Our current state is the writable signal
        if (!this.currentCourse() && !this.currentExam()) {
            // Component isn't initialized
            return;
        }

        // Store subscription to unsubscribe and prevent memory leaks
        this.changeLangSubscription = this.translateService.onLangChange.subscribe((selectedLang) => {
            this.currentLang.set(selectedLang.lang);
        });
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        this.archiveSubscription?.unsubscribe();
        this.dialogErrorSource.unsubscribe();
        this.changeLangSubscription?.unsubscribe();
    }

    registerArchiveWebsocket(topic: string) {
        if (topic) {
            this.archiveSubscription = this.websocketService
                .subscribe<CourseExamArchiveState>(topic)
                .pipe(tap((archiveState: CourseExamArchiveState) => this.handleArchiveStateChanges(archiveState)))
                .subscribe();
        }
    }

    handleArchiveStateChanges(courseArchiveState: CourseExamArchiveState) {
        this.archiveState.set(courseArchiveState);
        const { exportState, subMessage } = courseArchiveState;

        if (exportState === 'COMPLETED') {
            this.alertService.success(this.getArchiveSuccessText());
            this.reloadCourseOrExam();
        } else if (exportState === 'COMPLETED_WITH_WARNINGS') {
            this.openModal(this.archiveCompleteWithWarningsModal());
            this.reloadCourseOrExam();
        } else if (exportState === 'COMPLETED_WITH_ERRORS') {
            this.alertService.error(this.getArchiveErrorText(subMessage!));
        }
    }

    reloadCourseOrExam() {
        const course = this.currentCourse();
        if (!course?.id) return;
        const exam = this.currentExam();

        if (this.archiveMode() === 'Exam' && exam?.id) {
            this.examService.find(course.id, exam.id).subscribe((res) => {
                this.currentExam.set(res.body ?? undefined);
            });
        } else {
            this.courseService.find(course.id).subscribe((res) => {
                this.currentCourse.set(res.body ?? undefined);
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
            return this.translateService.instant(`artemisApp.courseExamArchive.archiveCourseError.${message}`, { courseName: this.currentCourse()?.title });
        } else {
            return this.translateService.instant('artemisApp.courseExamArchive.archiveExamError', { examName: this.currentExam()?.title });
        }
    }

    getArchiveStateTopic() {
        const exam = this.currentExam();
        const course = this.currentCourse();
        if (this.archiveMode() === 'Exam' && exam) {
            return '/topic/exams/' + exam.id + '/export';
        } else if (course) {
            return '/topic/courses/' + course.id + '/export-course';
        } else return null;
    }

    openModal(modalRef: TemplateRef<any>) {
        this.modalService.open(modalRef).result.then(
            (result: string) => {
                if (result === 'archive-confirm' && this.canDownloadArchive()) {
                    this.openModal(this.archiveConfirmModal());
                }
                if (result === 'archive' || !this.canDownloadArchive()) {
                    this.archive();
                }
            },
            () => {},
        );
    }

    archive() {
        const exam = this.currentExam();
        const course = this.currentCourse();
        if (!course?.id) {
            return;
        }
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.archiveExam(course.id, exam.id!).subscribe();
        } else {
            this.courseService.archiveCourse(course.id!).subscribe();
        }
    }

    downloadArchive() {
        const exam = this.currentExam();
        const course = this.currentCourse();
        if (!course?.id) {
            return;
        }
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.downloadExamArchive(course.id, exam.id!);
        } else {
            this.courseService.downloadCourseArchive(course.id);
        }
    }

    cleanup() {
        const exam = this.currentExam();
        if (this.archiveMode() === 'Exam' && exam) {
            this.examService.cleanupExam(this.currentCourse()?.id!, exam.id!).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.programmingExercise.cleanup.successMessageCleanup');
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.dialogErrorSource.next(error.error.title);
                },
            });
        } else {
            this.courseService.cleanupCourse(this.currentCourse()?.id!).subscribe({
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
