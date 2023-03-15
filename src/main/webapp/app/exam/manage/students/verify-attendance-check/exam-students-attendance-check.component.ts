import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faCheck, faInfoCircle, faPlus, faSort, faUpload, faUserSlash, faXmark } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subject, Subscription } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/user.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ExamUserAttendanceCheckDTO } from 'app/entities/exam-users-attendance-check-dto.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-exam-students-attendance-check',
    templateUrl: './exam-students-attendance-check.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class ExamStudentsAttendanceCheckComponent implements OnInit, OnDestroy {
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly ActionType = ActionType;
    readonly SERVER_API_URL = SERVER_API_URL;
    readonly missingImage = '/content/images/missing_image.png';

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
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass?: string;

    // Icons
    faPlus = faPlus;
    faUserSlash = faUserSlash;
    faInfoCircle = faInfoCircle;
    faUpload = faUpload;
    faCheck = faCheck;
    faXmark = faXmark;
    faSort = faSort;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private eventManager: EventManager,
        private examManagementService: ExamManagementService,
        private userService: UserService,
        private accountService: AccountService,
        private sortService: SortService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.route.data.subscribe(({ exam }: { exam: Exam }) => {
            this.exam = exam;
            this.hasExamStarted = exam.startDate?.isBefore(dayjs()) || false;
            this.isTestExam = this.exam.testExam!;
            this.isLoading = false;
        });
        if (this.hasExamStarted) {
            this.examManagementService.verifyExamUserAttendance(this.courseId, this.exam.id!).subscribe({
                next: (res: HttpResponse<ExamUserAttendanceCheckDTO[]>) => {
                    this.allExamUsersAttendanceCheck = res.body!;
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
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
    }

    sortRows() {
        this.sortService.sortByProperty(this.allExamUsersAttendanceCheck, this.predicate, this.ascending);
    }
}
