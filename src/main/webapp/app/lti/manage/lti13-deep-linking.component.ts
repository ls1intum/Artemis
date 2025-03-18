import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { faExclamationTriangle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { SessionStorageService } from 'ngx-webstorage';
import { TranslateDirective } from '../../shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { Lecture } from 'app/entities/lecture.model';
import { DeepLinkingType } from 'app/lti/lti.constants';

@Component({
    selector: 'jhi-deep-linking',
    templateUrl: './lti13-deep-linking.component.html',
    imports: [
        TranslateDirective,
        FaIconComponent,
        FormsModule,
        HelpIconComponent,
        ArtemisTranslatePipe,
        SortByDirective,
        SortDirective,
        ArtemisDatePipe,
        // NOTE: this is actually used in the html template, otherwise *jhiHasAnyAuthority would not work
        HasAnyAuthorityDirective,
    ],
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
    lectures: Lecture[];
    selectedExercises?: Set<number> = new Set();
    selectedLectures?: Set<number> = new Set();
    isCompetencySelected = false;
    isLearningPathSelected = false;
    isIrisSelected = false;
    course: Course;

    predicate = 'type';
    reverse = false;
    isLinking = true;

    //dropdowns
    isExerciseDropdownOpen = false;
    isLectureDropdownOpen = false;

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
                    this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(this.courseId).subscribe((findWithExercisesResult) => {
                        if (findWithExercisesResult?.body) {
                            this.course = findWithExercisesResult.body;
                            if (findWithExercisesResult?.body?.exercises) {
                                this.exercises = findWithExercisesResult.body.exercises;
                            }
                            if (findWithExercisesResult?.body?.lectures) {
                                this.lectures = findWithExercisesResult.body.lectures;
                            }
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
     * @returns True if the exercise is selected, false otherwise.
     * @param exerciseId
     */
    isExerciseSelected(exerciseId: number | undefined) {
        return exerciseId !== undefined && this.selectedExercises?.has(exerciseId);
    }

    selectLecture(lectureId: number | undefined) {
        if (lectureId !== undefined) {
            if (this.selectedLectures?.has(lectureId)) {
                this.selectedLectures?.delete(lectureId);
            } else {
                this.selectedLectures?.add(lectureId);
            }
        }
    }

    isLectureSelected(lectureId: number | undefined) {
        return lectureId !== undefined && this.selectedLectures?.has(lectureId);
    }

    enableCompetency() {
        if (!this.isCompetencySelected) {
            this.isCompetencySelected = true;
            this.isIrisSelected = false;
            this.isLearningPathSelected = false;
        }
    }

    enableLearningPath() {
        if (!this.isLearningPathSelected) {
            this.isLearningPathSelected = true;
            this.isIrisSelected = false;
            this.isCompetencySelected = false;
        }
    }

    enableIris() {
        if (!this.isIrisSelected) {
            this.isIrisSelected = true;
            this.isLearningPathSelected = false;
            this.isCompetencySelected = false;
        }
    }

    /**
     * Sends a deep link request for the selected exercise.
     * If an exercise, lecture, competency, learning path or Iris is selected, it sends a POST request to initiate deep linking.
     */
    sendDeepLinkRequest() {
        if (this.selectedExercises?.size || this.selectedLectures?.size || this.isCompetencySelected || this.isLearningPathSelected || this.isIrisSelected) {
            const ltiIdToken = this.sessionStorageService.retrieve('ltiIdToken') ?? '';
            const clientRegistrationId = this.sessionStorageService.retrieve('clientRegistrationId') ?? '';

            type DeepLinkingResponse = {
                targetLinkUri: string;
            };

            let resourceType: DeepLinkingType;
            let contentIds: string | null = null;

            if (this.selectedExercises?.size) {
                resourceType = DeepLinkingType.EXERCISE;
                contentIds = Array.from(this.selectedExercises).join(',');
            } else if (this.selectedLectures?.size) {
                resourceType = DeepLinkingType.LECTURE;
                contentIds = Array.from(this.selectedLectures).join(',');
            } else if (this.isCompetencySelected) {
                resourceType = DeepLinkingType.COMPETENCY;
            } else if (this.isLearningPathSelected) {
                resourceType = DeepLinkingType.LEARNING_PATH;
            } else if (this.isIrisSelected) {
                resourceType = DeepLinkingType.IRIS;
            } else {
                this.alertService.error('artemisApp.lti13.deepLinking.selectToLink');
                return;
            }

            const httpParams = new HttpParams()
                .set('resourceType', resourceType)
                .set('ltiIdToken', ltiIdToken)
                .set('clientRegistrationId', clientRegistrationId)
                // Set contentIds only if it exists
                .set('contentIds', contentIds || '');

            this.http
                .post<DeepLinkingResponse>(`api/lti/lti13/deep-linking/${this.courseId}`, null, {
                    observe: 'response',
                    params: httpParams,
                })
                .subscribe({
                    next: (response) => {
                        if (response.status === 200 && response.body) {
                            window.location.replace(response.body.targetLinkUri);
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
