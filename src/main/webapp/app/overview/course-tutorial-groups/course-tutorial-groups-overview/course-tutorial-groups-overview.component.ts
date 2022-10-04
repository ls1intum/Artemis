import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, map } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-course-tutorial-groups-overview',
    templateUrl: './course-tutorial-groups-overview.component.html',
    styleUrls: ['./course-tutorial-groups-overview.component.scss'],
})
export class CourseTutorialGroupsOverviewComponent implements OnInit {
    courseId: number;
    isLoading = false;
    tutorialGroups: TutorialGroup[] = [];

    constructor(private tutorialGroupService: TutorialGroupsService, private router: Router, private activatedRoute: ActivatedRoute, private alertService: AlertService) {}

    ngOnInit(): void {
        this.activatedRoute.parent?.parent?.paramMap.subscribe((parentParams) => {
            this.courseId = Number(parentParams.get('courseId'));
            if (this.courseId) {
                this.loadTutorialGroups();
            }
        });
    }

    public loadTutorialGroups() {
        this.isLoading = true;
        this.tutorialGroupService
            .getAllOfCourse(this.courseId)
            .pipe(
                map((res: HttpResponse<TutorialGroup[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (tutorialGroups: TutorialGroup[]) => {
                    this.tutorialGroups = tutorialGroups.map((tutorialGroup) => {
                        if (!tutorialGroup.registrations) {
                            tutorialGroup.registrations = [];
                        }
                        return tutorialGroup;
                    });
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    public onTutorialGroupSelected(tutorialGroupId: number) {
        this.router.navigate(['/courses', this.courseId, 'tutorial-groups', tutorialGroupId]);
    }
}
