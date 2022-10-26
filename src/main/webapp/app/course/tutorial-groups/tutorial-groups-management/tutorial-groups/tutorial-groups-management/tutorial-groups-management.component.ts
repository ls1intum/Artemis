import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, combineLatest } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPencil, faPlus, faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';

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
    faPencil = faPencil;
    faUmbrellaBeach = faUmbrellaBeach;

    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    constructor(
        private tutorialGroupService: TutorialGroupsService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.courseId = course.id!;
                this.isAtLeastInstructor = course.isAtLeastInstructor;
                this.loadTutorialGroups();
            }
        });
    }

    onTutorialGroupSelected = (tutorialGroup: TutorialGroup) => {
        this.router.navigate(['/course-management', this.courseId, 'tutorial-groups', tutorialGroup.id]);
    };

    loadTutorialGroups() {
        this.isLoading = true;

        combineLatest([this.tutorialGroupService.getAllForCourse(this.courseId), this.tutorialGroupsConfigurationService.getOneOfCourse(this.course.id!)])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([tutorialGroupsRes, configurationRes]) => {
                    const tutorialGroups = tutorialGroupsRes.body!;
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

                    const configuration = configurationRes.body!;
                    if (configuration.tutorialGroupFreePeriods) {
                        this.tutorialGroupFreeDays = configuration.tutorialGroupFreePeriods;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
