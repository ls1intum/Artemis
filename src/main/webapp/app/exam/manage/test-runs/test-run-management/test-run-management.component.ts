import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { SortService } from 'app/foundation/service/sort.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal/create-test-run-modal.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { Subject } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { onError } from 'app/foundation/util/global.utils';
import { faCheck, faPlus, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamDeleteDialogComponent } from 'app/exam/shared/delete-dialog/exam-delete-dialog.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { CreateTestRunDTO } from 'app/exam/manage/test-runs/create-test-run-dto.model';
import { StudentExamDTO } from 'app/exam/shared/entities/student-exam-dto.model';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import {
    TumAetUiButtonDirective,
    TumAetUiMessageComponent,
    TumAetUiTableDirective,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-test-run-management',
    templateUrl: './test-run-management.component.html',
    imports: [
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        ExamDeleteDialogComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleDirective,
        TumAetUiButtonDirective,
        TumAetUiMessageComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiTooltipDirective,
        CreateTestRunModalComponent,
    ],
})
export class TestRunManagementComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private accountService = inject(AccountService);
    private sortService = inject(SortService);

    readonly deleteDialogVisible = signal(false);
    readonly testRunToDelete = signal<StudentExamDTO | undefined>(undefined);

    course = signal<Course | undefined>(undefined);
    exam = signal<Exam | undefined>(undefined);
    isLoading = signal(false);
    createTestRunDialogVisible = signal(false);
    isExamStarted = computed(() => this.exam()?.started || false);
    testRuns = signal<StudentExamDTO[]>([]);
    instructor = signal<User | undefined>(undefined);
    predicate = signal<string>('id');
    ascending = signal<boolean>(true);
    // Determines if a test run has been submitted. Used to enable the assess test run button.
    testRunCanBeAssessed = computed(() => {
        const runs = this.testRuns();
        const instructor = this.instructor();
        return runs.some((testRun) => testRun.user?.id === instructor?.id && testRun.submitted);
    });
    // Determines if at least one exercise has been configured for the exam
    examContainsExercises = computed(() => {
        const exam = this.exam();
        return !!exam?.exerciseGroups && exam.exerciseGroups.some((exerciseGroup) => exerciseGroup.exercises && exerciseGroup.exercises.length > 0);
    });

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSpinner = faSpinner;
    faTimes = faTimes;
    faPlus = faPlus;
    faCheck = faCheck;

    ngOnInit(): void {
        this.examManagementService.find(Number(this.route.snapshot.paramMap.get('courseId')), Number(this.route.snapshot.paramMap.get('examId')), true).subscribe({
            next: (response: HttpResponse<Exam>) => {
                this.exam.set(response.body!);
                this.course.set(this.exam()!.course);
                const course = this.course()!;
                course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(course);
                this.examManagementService.findAllTestRunsForExam(course.id!, this.exam()!.id!).subscribe({
                    next: (res: HttpResponse<StudentExamDTO[]>) => {
                        this.testRuns.set(res.body!);
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
        void this.accountService.identity().then((user) => {
            if (user) {
                this.instructor.set(user);
            }
        });
    }

    /**
     * Open modal to configure a new test run
     */
    openCreateTestRunModal() {
        this.createTestRunDialogVisible.set(true);
    }

    /**
     * Creates the test run that was configured in the modal
     * @param testRunConfiguration the configuration chosen by the instructor
     */
    createTestRun(testRunConfiguration: CreateTestRunDTO) {
        this.examManagementService.createTestRun(this.course()!.id!, this.exam()!.id!, testRunConfiguration).subscribe({
            next: (response: HttpResponse<StudentExamDTO>) => {
                if (response.body != undefined) {
                    this.testRuns.update((current) => [...current, response.body!]);
                }
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
            },
        });
    }

    openDeleteDialog(testRun: StudentExamDTO) {
        this.testRunToDelete.set(testRun);
        this.deleteDialogVisible.set(true);
    }

    /**
     * Delete the test run with the given id.
     * @param testRunId
     */
    deleteTestRun(testRunId: number) {
        this.examManagementService.deleteTestRun(this.course()!.id!, this.exam()!.id!, testRunId).subscribe({
            next: () => {
                this.testRuns.update((currentTestRuns) => currentTestRuns.filter((testRun) => testRun.id !== testRunId));
                this.dialogErrorSource.next('');
            },
            error: (error) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Track the items on the testruns Table
     * @param _index
     * @param item
     */
    trackId(_index: number, item: StudentExamDTO) {
        return item.id;
    }

    onSortChange(event: TumAetUiTableSortEvent) {
        this.predicate.set(event.field);
        this.ascending.set(event.order > 0);
        this.sortRows();
    }

    sortRows() {
        this.testRuns.set([...this.sortService.sortByProperty(this.testRuns(), this.predicate(), this.ascending())]);
    }
}
