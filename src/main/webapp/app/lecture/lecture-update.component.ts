import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from './lecture.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { Course } from 'app/entities/course.model';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
    styleUrls: ['./lecture-update.component.scss'],
})
export class LectureUpdateComponent implements OnInit {
    EditorMode = EditorMode;
    lecture: Lecture;
    isSaving: boolean;

    courses: Course[];
    startDate: string;
    endDate: string;

    domainCommandsDescription = [new KatexCommand()];

    // Icons
    faSave = faSave;
    faBan = faBan;

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.parent!.data.subscribe((data) => {
            // Create a new lecture to use unless we fetch an existing lecture
            const lecture = data['lecture'];
            this.lecture = lecture ?? new Lecture();

            const course = data['course'];
            if (course) {
                this.lecture.course = course;
            }
        });
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing lecture
     * Returns to the overview page if there is no previous state and we created a new lecture
     */
    previousState() {
        this.navigationUtilService.navigateBackWithOptional(['course-management', this.lecture.course!.id!.toString(), 'lectures'], this.lecture.id?.toString());
    }

    /**
     * Save the changes on a lecture
     * This function is called by pressing save after creating or editing a lecture
     */
    save() {
        this.isSaving = true;
        if (this.lecture.id !== undefined) {
            this.subscribeToSaveResponse(this.lectureService.update(this.lecture));
        } else {
            this.subscribeToSaveResponse(this.lectureService.create(this.lecture));
        }
    }

    /**
     * @callback Callback function after saving a lecture, handles appropriate action in case of error
     * @param result The Http response from the server
     */
    protected subscribeToSaveResponse(result: Observable<HttpResponse<Lecture>>) {
        result.subscribe({
            next: (response: HttpResponse<Lecture>) => this.onSaveSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onSaveError(error),
        });
    }

    /**
     * Action on successful lecture creation or edit
     */
    protected onSaveSuccess(lecture: Lecture) {
        this.isSaving = false;
        this.router.navigate(['course-management', lecture.course!.id, 'lectures', lecture.id]);
    }

    /**
     * Action on unsuccessful lecture creation or edit
     * @param error the error handed to the alert service
     */
    protected onSaveError(error: HttpErrorResponse) {
        this.isSaving = false;
        onError(this.alertService, error);
    }
}
