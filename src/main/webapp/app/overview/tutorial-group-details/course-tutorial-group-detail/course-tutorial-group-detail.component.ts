import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, forkJoin, switchMap, take } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-course-tutorial-group-detail',
    templateUrl: './course-tutorial-group-detail.component.html',
    styleUrls: ['./course-tutorial-group-detail.component.scss'],
})
export class CourseTutorialGroupDetailComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    course: Course;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
        private courseManagementService: CourseManagementService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    const courseId = Number(params.get('courseId'));
                    return forkJoin({
                        courseResult: this.courseManagementService.find(courseId),
                        tutorialGroupResult: this.tutorialGroupService.getOneOfCourse(courseId, tutorialGroupId),
                    });
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ({ courseResult, tutorialGroupResult }) => {
                    this.tutorialGroup = tutorialGroupResult.body!;
                    this.course = courseResult.body!;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    onCourseClicked = () => {
        this.router.navigate(['/courses', this.course.id!]);
    };
}
