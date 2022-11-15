import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest, finalize, switchMap, take } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-tutorial-group-management-detail',
    templateUrl: './tutorial-group-management-detail.component.html',
})
export class TutorialGroupManagementDetailComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    course: Course;
    tutorialGroupId: number;
    isAtLeastInstructor = false;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, { course }]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    if (course) {
                        this.course = course;
                        this.isAtLeastInstructor = course.isAtLeastInstructor;
                    }
                    return this.tutorialGroupService.getOneOfCourse(this.course.id!, this.tutorialGroupId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    this.tutorialGroup = tutorialGroupResult.body!;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    onCourseClicked = () => {
        this.router.navigate(['/course-management', this.course.id!]);
    };

    onRegistrationsClicked = () => {
        this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups', this.tutorialGroupId, 'registered-students']);
    };

    onTutorialGroupDeleted = () => {
        this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
    };
}
