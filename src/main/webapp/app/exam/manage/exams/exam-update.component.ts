import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/alert/alert.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
})
export class ExamUpdateComponent implements OnInit {
    exam: Exam;
    course: Course;
    isSaving: boolean;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private jhiAlertService: AlertService,
        private courseManagementService: CourseManagementService,
    ) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.courseManagementService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe((response: HttpResponse<Course>) => {
                this.exam.course = response.body!;
                this.course = response.body!;
            });
        });
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     */
    previousState() {
        window.history.back();
    }

    /**
     * Save the changes on a exam
     * This function is called by pressing save after creating or editing a lecture
     */
    save() {
        this.isSaving = true;
        if (this.exam.id !== undefined) {
            this.subscribeToSaveResponse(this.examManagementService.update(this.course.id, this.exam));
        } else {
            this.subscribeToSaveResponse(this.examManagementService.create(this.course.id, this.exam));
        }
    }

    /**
     * @callback Callback function after saving a lecture, handles appropriate action in case of error
     * @param result The Http response from the server
     */
    protected subscribeToSaveResponse(result: Observable<HttpResponse<Exam>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
            () => this.onSaveError(),
        );
    }

    /**
     * Action on successful lecture creation or edit
     */
    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    /**
     * Action on unsuccessful lecture creation or edit
     */
    protected onSaveError() {
        this.isSaving = false;
        // TODO: No feedback given to user
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
