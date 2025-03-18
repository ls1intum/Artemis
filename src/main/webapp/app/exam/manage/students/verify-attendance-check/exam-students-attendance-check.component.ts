import { Component, OnDestroy, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamUserAttendanceCheckDTO } from 'app/entities/exam/exam-users-attendance-check-dto.model';
import { SortService } from 'app/shared/service/sort.service';
import { Subject, Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/entities/exam/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/shared/service/alert.service';
import { faCheck, faInfoCircle, faPlus, faSort, faTimes, faUpload, faXmark } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-exam-students-attendance-check',
    templateUrl: './exam-students-attendance-check.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, FaIconComponent, SortDirective, SortByDirective],
})
export class ExamStudentsAttendanceCheckComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private sortService = inject(SortService);

    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly ActionType = ActionType;
    readonly MISSING_IMAGE = '/content/images/missing_image.png';

    courseId: number;
    exam: Exam;
    predicate = 'id';
    ascending = true;
    isTestExam: boolean;
    allExamUsersAttendanceCheck: ExamUserAttendanceCheckDTO[] = [];
    filteredUsersSize = 0;
    paramSub: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading = false;
    isSearching = false;
    hasExamStarted = false;
    hasExamEnded = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass?: string;

    // Icons
    faPlus = faPlus;
    faInfoCircle = faInfoCircle;
    faUpload = faUpload;
    faCheck = faCheck;
    faTimes = faTimes;
    faXmark = faXmark;
    faSort = faSort;

    ngOnInit() {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.route.data.subscribe(({ exam }: { exam: Exam }) => {
            this.exam = exam;
            this.hasExamStarted = exam.startDate?.isBefore(dayjs()) || false;
            this.hasExamEnded = exam.endDate?.isBefore(dayjs()) || false;
            this.isTestExam = this.exam.testExam!;
        });
        if (this.hasExamStarted) {
            this.examManagementService.verifyExamUserAttendance(this.courseId, this.exam.id!).subscribe({
                next: (res: HttpResponse<ExamUserAttendanceCheckDTO[]>) => {
                    this.allExamUsersAttendanceCheck = res.body!;
                    this.isLoading = false;
                },
                error: (error: HttpErrorResponse) => this.onError(error.message),
            });
        }
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(error);
        this.isTransitioning = false;
        this.isLoading = false;
    }

    sortRows() {
        this.sortService.sortByProperty(this.allExamUsersAttendanceCheck, this.predicate, this.ascending);
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
