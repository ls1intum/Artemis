import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { faExclamationTriangle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { SessionStorageService } from 'ngx-webstorage';

@Component({
    selector: 'jhi-deep-linking',
    templateUrl: './lti13-deep-linking.component.html',
})
export class Lti13DeepLinkingComponent implements OnInit {
    route = inject(ActivatedRoute);
    private sortService = inject(SortService);
    private courseManagementService = inject(CourseManagementService);
    private http = inject(HttpClient);
    private accountService = inject(AccountService);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private sessionStorageService = inject(SessionStorageService);

    courseId: number;
    exercises: Exercise[];
    selectedExercises?: Set<number> = new Set();
    course: Course;

    predicate = 'type';
    reverse = false;
    isLinking = true;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;

    /**
     * Initializes the component.
     * - Retrieves the course ID from the route parameters.
     * - Fetches the user's identity.
     * - Retrieves the course details and exercises.
     * - Redirects unauthenticated users to the login page.
     */
    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);

            if (!this.courseId) {
                this.isLinking = false;
                return;
            }
            if (!this.isLinking) {
                return;
            }

            this.accountService.identity().then((user) => {
                if (user) {
                    this.courseManagementService.findWithExercises(this.courseId).subscribe((findWithExercisesResult) => {
                        if (findWithExercisesResult?.body?.exercises) {
                            this.course = findWithExercisesResult.body;
                            this.exercises = findWithExercisesResult.body.exercises;
                        }
                    });
                } else {
                    this.redirectUserToLoginThenTargetLink(window.location.href);
                }
            });
        });
    }

    /**
     * Redirects the user to the login page and sets up a listener for user login.
     * After login, redirects the user back to the original link.
     *
     * @param currentLink The current URL to return to after login.
     */
    redirectUserToLoginThenTargetLink(currentLink: string): void {
        this.router.navigate(['/']).then(() => {
            this.accountService.getAuthenticationState().subscribe((user) => {
                if (user) {
                    window.location.replace(currentLink);
                }
            });
        });
    }

    /**
     * Sorts the list of exercises based on the selected predicate and order.
     */
    sortRows() {
        this.sortService.sortByProperty(this.exercises, this.predicate, this.reverse);
    }

    /**
     * Toggles an exercise's selection based on its ID.
     *
     * Adds the ID to selectedExercises if not present, removes it otherwise.
     * Does nothing if the ID is undefined.
     *
     * @param exerciseId The exercise ID to toggle.
     */
    selectExercise(exerciseId: number | undefined) {
        if (exerciseId !== undefined) {
            if (this.selectedExercises?.has(exerciseId)) {
                this.selectedExercises?.delete(exerciseId);
            } else {
                this.selectedExercises?.add(exerciseId);
            }
        }
    }

    /**
     * Checks if the given exercise is currently selected.
     *
     * @param exercise The exercise to check.
     * @returns True if the exercise is selected, false otherwise.
     */
    isExerciseSelected(exerciseId: number | undefined) {
        return exerciseId !== undefined && this.selectedExercises?.has(exerciseId);
    }

    /**
     * Sends a deep link request for the selected exercise.
     * If an exercise is selected, it sends a POST request to initiate deep linking.
     */
    sendDeepLinkRequest() {
        if (this.selectedExercises?.size) {
            const ltiIdToken = this.sessionStorageService.retrieve('ltiIdToken') ?? '';
            const clientRegistrationId = this.sessionStorageService.retrieve('clientRegistrationId') ?? '';
            const exerciseIds = Array.from(this.selectedExercises).join(',');

            const httpParams = new HttpParams().set('exerciseIds', exerciseIds).set('ltiIdToken', ltiIdToken!).set('clientRegistrationId', clientRegistrationId!);

            type DeepLinkingResponse = {
                targetLinkUri: string;
            };
            this.http.post<DeepLinkingResponse>(`api/lti13/deep-linking/${this.courseId}`, null, { observe: 'response', params: httpParams }).subscribe({
                next: (response) => {
                    if (response.status === 200) {
                        if (response.body) {
                            const targetLink = response.body.targetLinkUri;
                            window.location.replace(targetLink);
                        }
                    } else {
                        this.isLinking = false;
                        this.alertService.error('artemisApp.lti13.deepLinking.unknownError');
                    }
                },
                error: (error) => {
                    this.isLinking = false;
                    onError(this.alertService, error);
                },
            });
        } else {
            this.alertService.error('artemisApp.lti13.deepLinking.selectToLink');
        }
    }
}
