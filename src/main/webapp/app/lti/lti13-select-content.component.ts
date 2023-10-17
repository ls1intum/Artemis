import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { faExclamationTriangle, faPlayCircle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-select-exercise',
    templateUrl: './lti13-select-content.component.html',
})
export class Lti13SelectContentComponent implements OnInit {
    courseId: number;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    exercises: Exercise[];
    selectedExercise: Exercise;

    activeTab = 1;

    predicate = 'type';
    reverse = false;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;
    faFileImport = faPlayCircle;
    constructor(
        private route: ActivatedRoute,
        private sortService: SortService,
        private courseManagementService: CourseManagementService,
        private http: HttpClient,
        private accountService: AccountService,
        private router: Router,
    ) {}

    /**
     * Gets the configuration for the course encoded in the route and fetches the exercises
     */
    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.accountService.identity().then((user) => {
                if (user) {
                    this.courseManagementService.findWithExercises(this.courseId).subscribe((findWithExercisesResult) => {
                        if (findWithExercisesResult?.body?.exercises) {
                            this.exercises = findWithExercisesResult.body.exercises;
                        }
                    });
                } else {
                    this.redirectUserToLoginThenTargetLink(window.location.href);
                }
            });
        });
    }

    redirectUserToLoginThenTargetLink(currentLink: any): void {
        // Redirect the user to the login page
        this.router.navigate(['/']).then(() => {
            // After navigating to the login page, set up a listener for when the user logs in
            this.accountService.getAuthenticationState().subscribe((user) => {
                if (user) {
                    window.location.replace(currentLink);
                }
            });
        });
    }

    sortRows() {
        this.sortService.sortByProperty(this.exercises, this.predicate, this.reverse);
    }

    toggleExercise(exercise: Exercise) {
        this.selectedExercise = exercise;
    }

    isExerciseSelected(exercise: Exercise) {
        return this.selectedExercise === exercise;
    }

    sendDeepLinkRequest() {
        if (this.selectedExercise) {
            const httpParams = new HttpParams().set('exerciseId', this.selectedExercise.id!);
            this.http.post(`api/lti13/deep-linking/${this.courseId}`, null, { observe: 'response', params: httpParams }).subscribe({
                next: (response) => {
                    if (response.status === 200) {
                        if (response.body) {
                            const targetLink = response.body['targetLinkUri'];
                            window.location.replace(targetLink);
                        }
                    } else {
                        console.log('Unexpected response status:', response.status);
                    }
                },
                error: (error) => {
                    console.error('An error occurred:', error);
                },
            });
        }
    }
}
