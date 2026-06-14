import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, take } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faExclamationTriangle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/foundation/service/sort.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { DeepLinkingType } from 'app/lti/manage/lti13-deep-linking/lti.constants';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/foundation/constants/authority.constants';

@Component({
    selector: 'jhi-deep-linking',
    templateUrl: './lti13-deep-linking.component.html',
    imports: [
        TranslateDirective,
        FaIconComponent,
        FormsModule,
        HelpIconComponent,
        SortByDirective,
        SortDirective,
        // NOTE: this is actually used in the html template, otherwise *jhiHasAnyAuthority would not work
        HasAnyAuthorityDirective,
    ],
})
export class Lti13DeepLinkingComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly sortService = inject(SortService);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly http = inject(HttpClient);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly alertService = inject(AlertService);
    private readonly sessionStorageService = inject(SessionStorageService);

    protected readonly IS_AT_LEAST_INSTRUCTOR = IS_AT_LEAST_INSTRUCTOR;

    protected readonly faSort = faSort;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faWrench = faWrench;

    courseId: number;
    readonly exercises = signal<Exercise[]>([]);
    readonly lectures = signal<Lecture[]>([]);
    selectedExercises?: Set<number> = new Set();
    selectedLectures?: Set<number> = new Set();
    readonly isCompetencySelected = signal(false);
    readonly isLearningPathSelected = signal(false);
    readonly isIrisSelected = signal(false);
    readonly course = signal<Course | undefined>(undefined);

    predicate = 'type';
    reverse = false;
    readonly isLinking = signal(true);

    //grouping
    readonly isExerciseGroupingActive = signal(false);
    readonly isLectureGroupingActive = signal(false);

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
                this.isLinking.set(false);
                return;
            }
            if (!this.isLinking()) {
                return;
            }

            this.accountService.identity().then((user) => {
                if (user) {
                    this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(this.courseId).subscribe((findWithExercisesResult) => {
                        if (findWithExercisesResult?.body) {
                            this.course.set(findWithExercisesResult.body);
                            if (findWithExercisesResult?.body?.exercises) {
                                this.exercises.set(findWithExercisesResult.body.exercises);
                            }
                            if (findWithExercisesResult?.body?.lectures) {
                                this.lectures.set(findWithExercisesResult.body.lectures);
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
        this.router.navigate(['/sign-in']).then(() => {
            this.accountService
                .getAuthenticationState()
                .pipe(filter(Boolean), take(1))
                .subscribe(() => {
                    window.location.replace(currentLink);
                });
        });
    }

    /**
     * Sorts the list of exercises based on the selected predicate and order.
     */
    sortRows() {
        const sortedExercises = [...this.exercises()];
        this.sortService.sortByProperty(sortedExercises, this.predicate, this.reverse);
        this.exercises.set(sortedExercises);
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

    activateExerciseGrouping() {
        this.isExerciseGroupingActive.set(true);
    }

    activateLectureGrouping() {
        this.isLectureGroupingActive.set(true);
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
        if (!this.isCompetencySelected()) {
            this.isCompetencySelected.set(true);
            this.isIrisSelected.set(false);
            this.isLearningPathSelected.set(false);
        }
    }

    enableLearningPath() {
        if (!this.isLearningPathSelected()) {
            this.isLearningPathSelected.set(true);
            this.isIrisSelected.set(false);
            this.isCompetencySelected.set(false);
        }
    }

    enableIris() {
        if (!this.isIrisSelected()) {
            this.isIrisSelected.set(true);
            this.isLearningPathSelected.set(false);
            this.isCompetencySelected.set(false);
        }
    }

    /**
     * Sends a deep link request for the selected exercise.
     * If an exercise, lecture, competency, learning path or Iris is selected, it sends a POST request to initiate deep linking.
     */
    sendDeepLinkRequest() {
        if (this.selectedExercises?.size || this.selectedLectures?.size || this.isCompetencySelected() || this.isLearningPathSelected() || this.isIrisSelected()) {
            const ltiIdToken = this.sessionStorageService.retrieve<string>('ltiIdToken') ?? '';
            const clientRegistrationId = this.sessionStorageService.retrieve<string>('clientRegistrationId') ?? '';

            type DeepLinkingResponse = {
                targetLinkUri: string;
            };

            let resourceType: DeepLinkingType;
            let contentIds: string | undefined = undefined;

            if (this.selectedExercises?.size) {
                if (this.isExerciseGroupingActive()) {
                    resourceType = DeepLinkingType.GROUPED_EXERCISE;
                } else {
                    resourceType = DeepLinkingType.EXERCISE;
                }
                contentIds = Array.from(this.selectedExercises).join(',');
            } else if (this.selectedLectures?.size) {
                if (this.isLectureGroupingActive()) {
                    resourceType = DeepLinkingType.GROUPED_LECTURE;
                } else {
                    resourceType = DeepLinkingType.LECTURE;
                }
                contentIds = Array.from(this.selectedLectures).join(',');
            } else if (this.isCompetencySelected()) {
                resourceType = DeepLinkingType.COMPETENCY;
            } else if (this.isLearningPathSelected()) {
                resourceType = DeepLinkingType.LEARNING_PATH;
            } else if (this.isIrisSelected()) {
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
                .post<DeepLinkingResponse>(`api/lti/lti13/courses/${this.courseId}/deep-linking`, null, {
                    observe: 'response',
                    params: httpParams,
                })
                .subscribe({
                    next: (response) => {
                        if (response.status === 200 && response.body) {
                            window.location.replace(response.body.targetLinkUri);
                        } else {
                            this.isLinking.set(false);
                            this.alertService.error('artemisApp.lti13.deepLinking.unknownError');
                        }
                    },
                    error: (error) => {
                        this.isLinking.set(false);
                        onError(this.alertService, error);
                    },
                });
        } else {
            this.alertService.error('artemisApp.lti13.deepLinking.selectToLink');
        }
    }
}
