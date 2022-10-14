import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { map, finalize } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
})
export class TutorialGroupsManagementComponent implements OnInit {
    courseId: number;
    course: Course;
    isAtLeastInstructor = false;

    isLoading = false;
    tutorialGroups: TutorialGroup[] = [];
    faPlus = faPlus;

    constructor(private tutorialGroupService: TutorialGroupsService, private router: Router, private activatedRoute: ActivatedRoute, private alertService: AlertService) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ course }) => {
            if (course) {
                this.courseId = course.id!;
                this.isAtLeastInstructor = course.isAtLeastInstructor;
                this.loadTutorialGroups();
            }
        });
    }

    onTutorialGroupSelected = (tutorialGroup: TutorialGroup) => {
        this.router.navigate(['/course-management', this.courseId, 'tutorial-groups-management', tutorialGroup.id]);
    };

    loadTutorialGroups() {
        this.isLoading = true;
        this.tutorialGroupService
            .getAllForCourse(this.courseId)
            .pipe(
                map((res: HttpResponse<TutorialGroup[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (tutorialGroups: TutorialGroup[]) => {
                    tutorialGroups.sort((a, b) => {
                        if (a.isUserTutor && !b.isUserTutor) {
                            return -1;
                        } else if (!a.isUserTutor && b.isUserTutor) {
                            return 1;
                        } else {
                            return a.title!.localeCompare(b.title!);
                        }
                    });
                    this.tutorialGroups = tutorialGroups;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }
}
