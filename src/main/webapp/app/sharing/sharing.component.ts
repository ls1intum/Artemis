import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { SortService } from 'app/shared/service/sort.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { SharingInfo, ShoppingBasket } from './sharing.model';
import { ProgrammingExerciseSharingService } from 'app/exercises/programming/manage/services/programming-exercise-sharing.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { faPlus, faSort } from '@fortawesome/free-solid-svg-icons';
import { NotificationService } from 'app/shared/notification/notification.service';

@Component({
    selector: 'jhi-sharing',
    templateUrl: './sharing.component.html',
    styleUrls: ['./sharing.scss'],
})
export class SharingComponent implements OnInit {
    courses: Course[];

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    reverse: boolean = false;
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
        private userRouteAccessService: UserRouteAccessService,
        private courseService: CourseManagementService,
        private sortService: SortService,
        private programmingExerciseSharingService: ProgrammingExerciseSharingService,
        private notificationService: NotificationService,
    ) {
        this.route.params.subscribe((params) => {
            this.sharingInfo.basketToken = params['basketToken'];
        });
        this.route.queryParams.subscribe((qparams: Params) => {
            this.sharingInfo.returnURL = qparams['returnURL'];
            this.sharingInfo.apiBaseURL = qparams['apiBaseURL'];
            this.sharingInfo.checksum = qparams['checksum'];
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
            error: (res: HttpErrorResponse) => this.notificationService.showError('Cannot load courses: ' + res.message),
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
