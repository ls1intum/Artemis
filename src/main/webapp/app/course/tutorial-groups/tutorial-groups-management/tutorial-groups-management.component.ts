import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { map, finalize } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPencil, faPlus, faSort } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { Course, Language } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { switchMap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { getDayTranslationKey, weekDays } from '../shared/weekdays';

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
    getDayTranslationKey = getDayTranslationKey;
    sortingPredicate = 'title';
    ascending = true;

    constructor(
        private tutorialGroupService: TutorialGroupsService,
        private courseManagementService: CourseManagementService,
        private sortService: SortService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.paramMap.subscribe((parentParams) => {
            this.courseId = Number(parentParams.get('courseId'));
            if (this.courseId) {
                this.loadAll();
            }
        });
    }
    public loadAll() {
        this.isLoading = true;
        this.courseManagementService
            .find(this.courseId)
            .pipe(
                switchMap((res: HttpResponse<Course>) => {
                    this.course = res.body!;
                    return this.tutorialGroupService.getAllOfCourse(this.course.id!);
                }),
                map((res: HttpResponse<TutorialGroup[]>) => res.body!),
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
                    this.checkIfTutorialGroupsConfigured();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    private checkIfTutorialGroupsConfigured() {
        if (!this.course.tutorialGroupsConfiguration) {
            this.router.navigate(['configuration', 'create'], { relativeTo: this.activatedRoute });
        }
    }

    trackId(index: number, item: TutorialGroup) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.tutorialGroups, this.sortingPredicate, this.ascending);
    }
}
