import { Component, OnInit, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faCheckCircle, faSort } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC, SORT } from 'app/shared/constants/pagination.constants';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration.component.html',
})
export class CourseRegistrationComponent implements OnInit {
    private accountService = inject(AccountService);
    private courseService = inject(CourseManagementService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private sortService = inject(SortService);

    coursesToSelect: Course[] = [];
    loading = false;
    predicate!: string;
    ascending!: boolean;
    searchTermString = '';
    filteredCoursesToSelect: Course[] = [];
    // Icons
    faCheckCircle = faCheckCircle;
    faSort = faSort;

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
            const courses = res.body!;
            if (this.predicate === 'defaultSort' || !this.predicate) {
                this.coursesToSelect = this.sortService.sortByProperty(courses, 'title', true);
            } else {
                this.coursesToSelect = this.sortService.sortByProperty(courses, this.predicate, this.ascending);
            }
            this.applySearch(); // Call this here to re-apply the search term after sorting
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
                search: this.searchTermString, // Add the search term to the query parameters
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
            this.searchTermString = params.get('search') || '';
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
