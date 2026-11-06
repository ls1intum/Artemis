import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamUserAttendanceCheckDTO } from 'app/exam/shared/entities/exam-users-attendance-check-dto.model';
import { SortService } from 'app/foundation/service/sort.service';
import { Subject, Subscription } from 'rxjs';
import { ActivatedRoute, Data } from '@angular/router';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { faCheck, faSort, faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TumAetUiProgressSpinnerComponent, TumAetUiTableDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-students-attendance-check',
    templateUrl: './exam-students-attendance-check.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ArtemisTranslatePipe,
        FaIconComponent,
        SortDirective,
        SortByDirective,
        CourseTitleBarTitleDirective,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTableDirective,
        TumAetUiTagComponent,
    ],
})
export class ExamStudentsAttendanceCheckComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private sortService = inject(SortService);
    private serverDateService = inject(ArtemisServerDateService);

    readonly MISSING_IMAGE = '/content/images/missing_image.png';

    courseId!: number;
    exam!: Exam;
    predicate = signal<string>('id');
    ascending = signal<boolean>(true);
    readonly isTestExam = signal(false);
    allExamUsersAttendanceCheck = signal<ExamUserAttendanceCheckDTO[]>([]);
    readonly uncheckedCount = computed(() => this.allExamUsersAttendanceCheck().filter((user) => user.started && !user.attendanceChecked).length);
    readonly savingAttendance = signal<number | undefined>(undefined);
    filteredUsersSize = 0;
    paramSub?: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading = signal(false);
    isSearching = signal(false);
    hasExamStarted = signal(false);
    hasExamEnded = signal(false);
    searchFailed = signal(false);
    searchNoResults = signal(false);
    isTransitioning = signal(false);
    rowClass?: string;
    private attendanceDeadlineTimer?: ReturnType<typeof setTimeout>;

    // Icons
    faCheck = faCheck;
    faXmark = faXmark;
    faSort = faSort;

    ngOnInit() {
        this.isLoading.set(true);
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.paramSub = this.route.data.subscribe((data: Data) => {
            const exam: Exam = data.exam;
            this.exam = exam;
            this.hasExamStarted.set(exam.startDate?.isBefore(this.serverDateService.now()) || false);
            this.scheduleAttendanceDeadline();
            this.isTestExam.set(this.exam.testExam ?? false);
        });
        if (!this.hasExamStarted()) {
            // Nothing can be verified before the start, and the page has to say so instead of loading forever.
            this.isLoading.set(false);
            return;
        }
        this.examManagementService.verifyExamUserAttendance(this.courseId, this.exam.id!, true).subscribe({
            next: (res: HttpResponse<ExamUserAttendanceCheckDTO[]>) => {
                this.allExamUsersAttendanceCheck.set(res.body!);
                this.isLoading.set(false);
            },
            error: (error: HttpErrorResponse) => this.onError(error.message),
        });
    }

    ngOnDestroy() {
        clearTimeout(this.attendanceDeadlineTimer);
        this.paramSub?.unsubscribe();
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(error);
        this.isTransitioning.set(false);
        this.isLoading.set(false);
    }

    private scheduleAttendanceDeadline() {
        clearTimeout(this.attendanceDeadlineTimer);
        const remaining = this.exam.endDate?.diff(this.serverDateService.now());
        this.hasExamEnded.set(remaining !== undefined && remaining <= 0);
        if (remaining !== undefined && remaining > 0) {
            this.attendanceDeadlineTimer = setTimeout(() => this.scheduleAttendanceDeadline(), Math.min(remaining, 2_147_483_647));
        }
    }

    setAttendance(examUser: ExamUserAttendanceCheckDTO, value: string) {
        if (!this.hasExamEnded() || this.isTestExam() || !examUser.id || this.savingAttendance() !== undefined) {
            return;
        }
        const attended = value === '' ? undefined : value === 'true';
        this.savingAttendance.set(examUser.id);
        this.examManagementService.updateAttendance(this.courseId, this.exam.id!, examUser.id, attended).subscribe({
            next: () => {
                // Reload the server-derived verification status as well, especially when restoring automatic attendance.
                this.examManagementService.verifyExamUserAttendance(this.courseId, this.exam.id!, true).subscribe({
                    next: (res) => {
                        this.allExamUsersAttendanceCheck.set(res.body ?? []);
                        this.savingAttendance.set(undefined);
                    },
                    error: (error: HttpErrorResponse) => {
                        this.savingAttendance.set(undefined);
                        this.onError(error.message);
                    },
                });
            },
            error: (error: HttpErrorResponse) => {
                this.savingAttendance.set(undefined);
                // Re-render the select with the persisted decision when the request failed.
                this.allExamUsersAttendanceCheck.update((users) => users.map((user) => cloneWith(user, {})));
                this.onError(error.message);
            },
        });
    }

    sortRows() {
        this.sortService.sortByProperty(this.allExamUsersAttendanceCheck(), this.predicate(), this.ascending());
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
