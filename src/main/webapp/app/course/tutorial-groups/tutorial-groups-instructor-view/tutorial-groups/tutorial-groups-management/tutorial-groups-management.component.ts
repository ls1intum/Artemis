import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { finalize, map } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPlus, faSort } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { Language } from 'app/entities/course.model';

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
    styleUrls: ['./tutorial-groups.management.component.scss'],
})
export class TutorialGroupsManagementComponent implements OnInit {
    courseId: number;
    isLoading = false;
    tutorialGroups: TutorialGroup[];
    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;
    faSort = faSort;
    faPlus = faPlus;
    sortingPredicate = 'title';
    ascending = true;

    constructor(
        private tutorialGroupService: TutorialGroupsService,
        private sortService: SortService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
    ) {}

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

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorialGroups, this.sortingPredicate, this.ascending);
    }
}
