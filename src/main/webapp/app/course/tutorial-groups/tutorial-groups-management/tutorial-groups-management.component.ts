import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { map, finalize } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
})
export class TutorialGroupsManagementComponent implements OnInit {
    courseId: number;
    isLoading = false;
    tutorialGroups: TutorialGroup[];

    faPlus = faPlus;

    constructor(private tutorialGroupService: TutorialGroupsService, private router: Router, private activatedRoute: ActivatedRoute, private alertService: AlertService) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.paramMap.subscribe((parentParams) => {
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
                next: (res: TutorialGroup[]) => {
                    this.tutorialGroups = res;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }
}
