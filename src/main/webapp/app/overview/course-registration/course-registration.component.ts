import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faCheckCircle, faSort } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC, SORT } from 'app/shared/constants/pagination.constants';
import { ActivatedRoute, Router } from '@angular/router';
import { UserFilter } from 'app/admin/user-management/user-management.component';
import { Subject, combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration.component.html',
})
export class CourseRegistrationComponent implements OnInit {
    search = new Subject<void>();
    coursesToSelect: Course[] = [];
    loading = false;
    faCheckCircle = faCheckCircle;
    faSort = faSort;
    predicate!: string;
    ascending!: boolean;
    searchTermString = '';
    filters: UserFilter = new UserFilter();
    filteredCoursesToSelect: Course[] = [];

    constructor(
        private accountService: AccountService,
        private courseService: CourseManagementService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.handleNavigation();
    }

    /**
     * Loads all courses that are available for self-registration by the logged-in user.
     * It sorts the courses based on the current value of the predicate and ascending properties.
     * It also initializes the filteredCoursesToSelect array with the sorted courses.
     */
    loadRegistrableCourses() {
        this.loading = true;
        this.courseService.findAllForRegistration().subscribe((res) => {
            if (this.predicate === 'defaultSort' || !this.predicate) {
                this.coursesToSelect = res.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
            } else {
                this.coursesToSelect = res.body!.sort((a, b) => {
                    let valueA: string;
                    let valueB: string;
                    switch (this.predicate) {
                        case 'title':
                            valueA = a.title ?? '';
                            valueB = b.title ?? '';
                            break;
                        case 'semester':
                            valueA = a.semester ?? '';
                            valueB = b.semester ?? '';
                            break;
                        default:
                            valueA = '';
                            valueB = '';
                    }
                    if (valueA < valueB) {
                        return this.ascending ? -1 : 1;
                    } else if (valueA > valueB) {
                        return this.ascending ? 1 : -1;
                    } else {
                        return 0;
                    }
                });
            }
            this.filteredCoursesToSelect = [...this.coursesToSelect];
            this.loading = false;
        });
    }

    /**
     * Removes a course from the list of courses that the user can register for
     * after the user has registered for the course
     * @param courseId the id of the course that the user has registered for
     */
    removeCourseFromList(courseId: number) {
        this.coursesToSelect = this.coursesToSelect.filter((course) => course.id !== courseId);
    }

    /**
     * Triggers a route transition, updating the URL with the current sorting parameters.
     */
    transition(): void {
        this.router.navigate(['/courses/enroll'], {
            relativeTo: this.activatedRoute.parent,
            queryParams: {
                sort: `${this.predicate},${this.ascending ? ASC : DESC}`,
            },
        });
    }

    /**
     * Handles navigation-related changes. It listens to route data and query parameters.
     * Based on the sort parameters in the URL, it updates the predicate and ascending properties
     * and then loads the registrable courses accordingly.
     */
    private handleNavigation(): void {
        combineLatest({
            data: this.activatedRoute.data,
            params: this.activatedRoute.queryParamMap,
        }).subscribe(({ data, params }) => {
            const sortParam = params.get(SORT) ?? data['defaultSort'] ?? 'title,asc';
            if (sortParam && typeof sortParam === 'string') {
                const sort = sortParam.split(',');
                if (sort.length === 2) {
                    this.predicate = sort[0];
                    this.ascending = sort[1] === ASC;
                }
            } else {
                this.predicate = 'title';
                this.ascending = true;
            }
            this.loadRegistrableCourses();
        });
    }
    /**
     * Filters the list of registrable courses based on the user's search term.
     * It updates the filteredCoursesToSelect array to only include courses
     * that match the search term entered by the user.
     */
    applySearch() {
        if (!this.searchTermString) {
            this.filteredCoursesToSelect = [...this.coursesToSelect];
        } else {
            this.filteredCoursesToSelect = this.coursesToSelect.filter((course) =>
                course.title ? course.title.toLowerCase().includes(this.searchTermString.toLowerCase()) : false,
            );
        }
    }
}
