import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Subscription } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learningPathService.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-learning-path-management',
    templateUrl: './learning-path-management.component.html',
})
export class LearningPathManagementComponent implements OnInit, OnDestroy {
    isLoading = false;

    courseId: number;
    course: Course;

    courseSub: Subscription;

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private learningPathService: LearningPathService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    private loadData() {
        this.isLoading = true;

        this.courseSub = this.courseManagementService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
        });

        if (this.course?.learningPathEnabled) {
            // TODO: load learning paths of students
        }

        this.isLoading = false;
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        if (this.courseSub) {
            this.courseSub.unsubscribe();
        }
    }

    enableLearningPaths() {
        this.isLoading = true;
        this.learningPathService
            .enableLearningPaths(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
