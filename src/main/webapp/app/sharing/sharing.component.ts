import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { SortService } from 'app/shared/service/sort.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { SharingInfo, ShoppingBasket } from './sharing.model';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { faPlus, faSort } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExerciseSharingService } from 'app/programming/manage/services/programming-exercise-sharing.service';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgStyle } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { combineLatest, take } from 'rxjs';
import { finalize } from 'rxjs/operators';
/**
 * controls the import of an exercise from the sharing platform.
 */
@Component({
    selector: 'jhi-sharing',
    templateUrl: './sharing.component.html',
    styleUrls: ['./sharing.scss'],
    imports: [RouterLink, FormsModule, TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgStyle],
})
export class SharingComponent implements OnInit {
    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faSort = faSort;

    courses: Course[] = [];
    coursesLoading = true;

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    reverseSorting: boolean = false;
    sortColumn = 'id';

    shoppingBasket?: ShoppingBasket;
    /**
     * holder for all data needed to import the exercise
     */
    sharingInfo: SharingInfo = new SharingInfo();

    selectedCourse: Course;

    isInstructorOrEditor = false;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private userRouteAccessService = inject(UserRouteAccessService);
    private courseService = inject(CourseManagementService);
    private sortService = inject(SortService);
    private programmingExerciseSharingService = inject(ProgrammingExerciseSharingService);
    private alertService = inject(AlertService);

    getBasketTokenExpiryDate(): Date {
        if (this.shoppingBasket?.tokenValidUntil) {
            return new Date(this.shoppingBasket.tokenValidUntil);
        }
        return new Date();
    }

    /**
     * loads all courses from courseService
     */
    loadAll() {
        this.coursesLoading = true;
        this.courseService
            .getCourseOverview({})
            .pipe(finalize(() => (this.coursesLoading = false)))
            .subscribe({
                next: (res: HttpResponse<Course[]>) => {
                    this.courses = res.body!;
                    this.coursesLoading = false;
                },
                error: (error: HttpErrorResponse) => {
                    this.coursesLoading = false;
                    this.alertService.error('artemisApp.sharing.error.loadingCourses');
                },
            });
    }

    onCourseSelected(course: Course): void {
        this.selectedCourse = course;
    }

    courseId(): number {
        return this.selectedCourse?.id ?? 0;
    }

    onExerciseSelected(index: number): void {
        this.sharingInfo.selectedExercise = index;
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index - Index of a course in the collection
     * @param item - Current course
     */
    trackId(index: number, item: Course) {
        return item.id ?? index;
    }

    /**
     * sorts the course table
     */
    sortRows() {
        this.sortService.sortByProperty(this.courses, this.sortColumn, this.reverseSorting);
    }

    /**
     * finally navigates to the import page
     */
    navigateToImportFromSharing() {
        const importBaseRoute = ['/course-management', this.courseId(), 'programming-exercises'];
        importBaseRoute.push('import-from-sharing');

        // check for missing parameters (technically this could never happen, because the button is disabled then)
        if (
            !this.sharingInfo.basketToken ||
            !this.sharingInfo.apiBaseURL ||
            !this.sharingInfo.returnURL ||
            !this.sharingInfo.checksum ||
            this.sharingInfo.selectedExercise === undefined ||
            this.sharingInfo.selectedExercise === null
        ) {
            this.alertService.error('artemisApp.sharing.error.missingParameters');
            return;
        }
        this.router
            .navigate(importBaseRoute, {
                queryParams: {
                    basketToken: this.sharingInfo.basketToken,
                    apiBaseURL: this.sharingInfo.apiBaseURL,
                    returnURL: this.sharingInfo.returnURL,
                    selectedExercise: this.sharingInfo.selectedExercise,
                    checksum: this.sharingInfo.checksum,
                },
            })
            .then((success) => {
                if (!success) {
                    this.alertService.error('artemisApp.sharing.error.navigation');
                }
            })
            .catch((error) => {
                this.alertService.error('artemisApp.sharing.error.navigation');
            });
    }

    get formattedExpiryDate(): string {
        return this.getBasketTokenExpiryDate().toLocaleString();
    }

    /**
     * Initialises the sharing page for import
     */
    ngOnInit(): void {
        combineLatest(this.route.params.pipe(take(1)), this.route.queryParams.pipe(take(1))).subscribe(([params, qparams]: [Params, Params]) => {
            this.sharingInfo.basketToken = params['basketToken'];
            this.sharingInfo.returnURL = qparams['returnURL'];
            this.sharingInfo.apiBaseURL = qparams['apiBaseURL'];
            this.sharingInfo.checksum = qparams['checksum'];

            if (!this.sharingInfo.basketToken) {
                this.alertService.error('artemisApp.sharing.error.missingToken');
                return;
            }
            if (!this.sharingInfo.apiBaseURL || !this.sharingInfo.returnURL || !this.sharingInfo.checksum) {
                this.alertService.error('artemisApp.sharing.error.missingParameters');
                return;
            }

            this.userRouteAccessService.checkLogin([Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN], this.router.url).then((isLoggedIn) => {
                if (!isLoggedIn) {
                    this.alertService.error('artemisApp.sharing.error.atLeastEditorNeeded');
                    return;
                }
                this.isInstructorOrEditor = true;
                this.loadAll();
                this.loadSharedExercises();
            });
        });
    }

    /**
     * Loads the shared exercises from the sharing platform
     */
    private loadSharedExercises(): void {
        this.programmingExerciseSharingService.getSharedExercises(this.sharingInfo).subscribe({
            next: (res: ShoppingBasket) => {
                this.shoppingBasket = res;
            },
            error: () => {
                this.alertService.error('artemisApp.sharing.error.loadingBasket');
            },
        });
    }
}
