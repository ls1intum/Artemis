import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Course } from 'app/core/shared/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam/exam.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { Subject } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { onError } from 'app/shared/util/global.utils';
import { faSort, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-test-run-management',
    templateUrl: './test-run-management.component.html',
    imports: [
        TranslateDirective,
        NgbTooltip,
        RouterLink,
        SortDirective,
        SortByDirective,
        FaIconComponent,
        DeleteButtonDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
    ],
})
export class TestRunManagementComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private accountService = inject(AccountService);
    private sortService = inject(SortService);
    private modalService = inject(NgbModal);

    course = signal<Course | undefined>(undefined);
    exam = signal<Exam | undefined>(undefined);
    isLoading = signal(false);
    isExamStarted = computed(() => this.exam()?.started || false);
    testRuns = signal<StudentExam[]>([]);
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
    faSort = faSort;
    faTimes = faTimes;

    ngOnInit(): void {
        this.examManagementService.find(Number(this.route.snapshot.paramMap.get('courseId')), Number(this.route.snapshot.paramMap.get('examId')), false, true).subscribe({
            next: (response: HttpResponse<Exam>) => {
                this.exam.set(response.body!);
                this.course.set(this.exam()!.course!);
                const course = this.course()!;
                course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(course);
                this.examManagementService.findAllTestRunsForExam(course.id!, this.exam()!.id!).subscribe({
                    next: (res: HttpResponse<StudentExam[]>) => {
                        this.testRuns.set(res.body!);
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
        this.accountService.identity().then((user) => {
            if (user) {
                this.instructor.set(user);
            }
        });
    }

    /**
     * Open modal to configure a new test run
     */
    openCreateTestRunModal() {
        const modalRef: NgbModalRef = this.modalService.open(CreateTestRunModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exam = this.exam();
        modalRef.result
            .then((testRunConfiguration: StudentExam) => {
                this.examManagementService.createTestRun(this.course()!.id!, this.exam()!.id!, testRunConfiguration).subscribe({
                    next: (response: HttpResponse<StudentExam>) => {
                        if (response.body != undefined) {
                            this.testRuns.update((current) => [...current, response.body!]);
                        }
                    },
                    error: (error: HttpErrorResponse) => {
                        onError(this.alertService, error);
                    },
                });
            })
            .catch(() => {});
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
    trackId(_index: number, item: StudentExam) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.testRuns(), this.predicate(), this.ascending());
    }
}
