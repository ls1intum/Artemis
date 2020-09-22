import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { Subject } from 'rxjs';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import * as moment from 'moment';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
})
export class ExamManagementComponent implements OnInit, OnDestroy {
    course: Course;
    exams: Exam[];
    isAtLeastInstructor = false;
    isAtLeastTutor = false;
    examIdToExamInformation: Map<number, ExamInformationDTO> = new Map();
    predicate: string;
    ascending: boolean;
    eventSubscriber: Subscription;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private examManagementService: ExamManagementService,
        private eventManager: JhiEventManager,
        private accountService: AccountService,
        private jhiAlertService: AlertService,
        private sortService: SortService,
    ) {
        this.predicate = 'id';
        this.ascending = true;
    }

    /**
     * Initialize the course and all exams when this view is initialised.
     * Subscribes to 'examListModification' event.
     * @see registerChangeInExams
     */
    ngOnInit(): void {
        this.courseService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body!;
                this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);
                this.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
                this.loadAllExamsForCourse();
                this.registerChangeInExams();
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
        );
    }

    /**
     * unsubscribe on component destruction
     */
    ngOnDestroy() {
        if (!this.eventSubscriber === undefined) {
            this.eventManager.destroy(this.eventSubscriber);
        }
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load all exams for a course.
     */
    loadAllExamsForCourse() {
        this.examManagementService.findAllExamsForCourse(this.course.id!).subscribe(
            (res: HttpResponse<Exam[]>) => {
                this.exams = res.body!;
                this.exams.forEach((exam) => {
                    this.examManagementService
                        .getLatestIndividualEndDateOfExam(this.course.id!, exam.id!)
                        .subscribe((examInformationDTORes: HttpResponse<ExamInformationDTO>) => this.examIdToExamInformation.set(exam.id!, examInformationDTORes.body!));
                });
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
        );
    }

    /**
     * Subscribes to 'examListModification' events
     */
    registerChangeInExams() {
        this.eventSubscriber = this.eventManager.subscribe('examListModification', () => {
            this.loadAllExamsForCourse();
        });
    }

    /**
     * Function is called when the delete button is pressed for an exam
     * @param examId Id to be deleted
     */
    deleteExam(examId: number) {
        this.examManagementService.delete(this.course.id!, examId).subscribe(
            () => {
                this.dialogErrorSource.next('');
                this.exams = this.exams.filter((exam) => exam.id !== examId);
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Track the items on the Exams Table
     * @param index {number}
     * @param item {Exam}
     */
    trackId(index: number, item: Exam) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.exams, this.predicate, this.ascending);
    }

    examHasFinished(examId: number): boolean {
        if (this.examIdToExamInformation.has(examId)) {
            return this.examIdToExamInformation.get(examId)!.latestIndividualEndDate.isBefore(moment());
        }
        return true;
    }
}
