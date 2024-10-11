import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router, RouterModule } from '@angular/router';
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
import { take } from 'rxjs';

/**
 * controls the import of an exercise from the sharing platform.
 */
@Component({
    selector: 'jhi-sharing',
    templateUrl: './sharing.component.html',
    styleUrls: ['./sharing.scss'],
    imports: [RouterModule, FormsModule, TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgStyle],
    standalone: true,
})
export class SharingComponent implements OnInit {
    /**
     * list of courses to import to
     */
    courses: Course[];

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    /** for sorting course table */
    reverse: boolean = false;
    /** for sorting course table */
    predicate: string;
    /**
     * the shopping basket imported from the sharing platform
     */
    shoppingBasket: ShoppingBasket;
    /**
     * holder for all data needed to import the exercise
     */
    sharingInfo: SharingInfo = new SharingInfo();
    /**
     * the selected course
     */
    selectedCourse: Course;
    /**
     * flag for instructor role test
     */
    isInstructor = false;

    // Icons
    faPlus = faPlus;
    faSort = faSort;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private userRouteAccessService: UserRouteAccessService,
        private courseService: CourseManagementService,
        private sortService: SortService,
        private programmingExerciseSharingService: ProgrammingExerciseSharingService,
        private alertService: AlertService,
    ) {
        this.predicate = 'id';
    }

    /**
     * @returns the expiration data of the shopping basket
     */
    getTokenExpiryDate(): Date {
        if (this.shoppingBasket?.tokenValidUntil) {
            return new Date(this.shoppingBasket.tokenValidUntil);
        }
        return new Date();
    }
    /**
     * loads all courses from courseService
     */
    loadAll() {
        this.courseService.getWithUserStats(false).subscribe({
            next: (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            error: (res: HttpErrorResponse) => this.alertService.error('artemisApp.sharing.error.loadingCourses'),
        });
    }

    /**
     * course selection event handler
     * @param course selected course
     */
    onCourseSelected(course: Course): void {
        this.selectedCourse = course;
    }

    /**
     * the id of the course
     * @returns the id of the course
     */
    courseId(): number {
        return this.selectedCourse?.id ?? 0;
    }

    /**
     * exercise selection event handler
     * @param index the index of the selected exercise in basket
     */
    onExerciseSelected(index: number): void {
        this.sharingInfo.selectedExercise = index;
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index - Index of a course in the collection
     * @param item - Current course
     */
    trackId(index: number, item: Course) {
        return item.id;
    }

    /**
     * sorts the course table
     */
    sortRows() {
        this.sortService.sortByProperty(this.courses, this.predicate, this.reverse);
    }

    /**
     * finally navigates to the import page
     */
    navigateToImportFromSharing() {
        const importBaseRoute = ['/course-management', this.courseId(), 'programming-exercises'];
        importBaseRoute.push('import-from-sharing');
        this.router
            .navigate(importBaseRoute, {
                queryParams: {
                    basketToken: this.sharingInfo.basketToken,
                    apiBaseUrl: this.sharingInfo.apiBaseURL,
                    returnUrl: this.sharingInfo.returnURL,
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
        return this.getTokenExpiryDate().toLocaleString();
    }

    /**
     * Initialises the sharing page for import
     */
    ngOnInit(): void {
        // Extract and validate parameters
        this.route.params.pipe(take(1)).subscribe((params) => {
            this.sharingInfo.basketToken = params['basketToken'];
            if (!this.sharingInfo.basketToken) {
                this.alertService.error('artemisApp.sharing.error.missingToken');
            }
        });

        this.route.queryParams.pipe(take(1)).subscribe((qparams: Params) => {
            this.sharingInfo.returnURL = qparams['returnURL'];
            this.sharingInfo.apiBaseURL = qparams['apiBaseURL'];
            this.sharingInfo.checksum = qparams['checksum'];

            if (this.sharingInfo.basketToken) {
                this.loadSharedExercises();
            }
        });
        this.userRouteAccessService.checkLogin([Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN], this.router.url).then((isLoggedIn) => {
            if (isLoggedIn) {
                this.isInstructor = true;
                this.loadAll();
            }
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
