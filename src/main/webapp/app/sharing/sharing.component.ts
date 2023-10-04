import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { SortService } from 'app/shared/service/sort.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { SharingInfo, ShoppingBasket } from './sharing.model';
import { ProgrammingExerciseSharingService } from 'app/exercises/programming/manage/services/programming-exercise-sharing.service';
import { LoginService } from 'app/core/login/login.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { faPlus, faSort } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-sharing',
    templateUrl: './sharing.component.html',
    styleUrls: ['./sharing.scss'],
})
export class SharingComponent implements OnInit {
    courses: Course[];

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    reverse: boolean;
    predicate: string;
    shoppingBasket: ShoppingBasket;
    sharingInfo: SharingInfo = new SharingInfo();
    selectedCourse: Course;
    isInstructor = false;

    // Icons
    faPlus = faPlus;
    faSort = faSort;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private authServerProvider: AuthServerProvider,
        private accountService: AccountService,
        private userRouteAccessService: UserRouteAccessService,
        private loginService: LoginService,
        private stateStorageService: StateStorageService,
        private courseService: CourseManagementService,
        private sortService: SortService,
        private programmingExerciseSharingService: ProgrammingExerciseSharingService,
    ) {
        this.route.params.subscribe((params) => {
            console.log('###DEBUG', params);
            this.sharingInfo.basketToken = params['basketToken'];
        });
        this.route.queryParams.subscribe((qparams) => {
            this.sharingInfo.returnURL = qparams['returnURL'];
            this.sharingInfo.apiBaseURL = qparams['apiBaseURL'];
            this.programmingExerciseSharingService.getSharedExercises(this.sharingInfo).subscribe((res: ShoppingBasket) => {
                this.shoppingBasket = res;
            });
        });
        this.predicate = 'id';
    }

    getTokenExpiryDate(): Date {
        if (this.shoppingBasket) {
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
            error: (res: HttpErrorResponse) => alert('Cannot load courses: [' + res.message + ']'),
        });
    }

    onCourseSelected(course: Course): void {
        this.selectedCourse = course;
    }

    courseId(): number {
        if (this.selectedCourse && this.selectedCourse.id) {
            return this.selectedCourse.id;
        }
        return 0;
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
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.courses, this.predicate, this.reverse);
    }

    /**
     * Initialises the sharing page for import
     */
    ngOnInit(): void {
        this.userRouteAccessService.checkLogin([Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN], this.router.url).then((isLoggedIn) => {
            if (isLoggedIn) {
                this.isInstructor = true;
                this.loadAll();
            }
        });
    }
}
