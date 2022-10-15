import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { map, finalize, combineLatest } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPencil, faPlus, faSort, faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { Course, Language } from 'app/entities/course.model';
import { switchMap, take } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { getDayTranslationKey } from '../../../shared/weekdays';

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
    styleUrls: ['./tutorial-groups.management.component.scss'],
})
export class TutorialGroupsManagementComponent implements OnInit {
    courseId: number;
    course: Course;
    isLoading = false;
    tutorialGroups: TutorialGroup[];
    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;
    faSort = faSort;
    faPlus = faPlus;
    faPencil = faPencil;
    faUmbrellaBeach = faUmbrellaBeach;
    getDayTranslationKey = getDayTranslationKey;
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
        this.loadAll();
    }

    public loadAll() {
        this.isLoading = true;
        combineLatest([this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([data]) => {
                    this.course = data['course'];
                    this.courseId = this.course.id!;
                    return this.tutorialGroupService.getAllOfCourse(this.course.id!).pipe(finalize(() => (this.isLoading = false)));
                }),
                map((res: HttpResponse<TutorialGroup[]>) => {
                    return res.body;
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
                    this.checkIfTutorialGroupsConfigured();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    private checkIfTutorialGroupsConfigured() {
        if (!this.course.tutorialGroupsConfiguration) {
            this.router.navigate(['/course-management', this.courseId, 'tutorial-groups-management', 'configuration', 'create']);
        }
    }

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorialGroups, this.sortingPredicate, this.ascending);
    }
}
