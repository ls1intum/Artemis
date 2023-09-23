import { Injectable } from '@angular/core';
import { SessionStorageService } from 'ngx-webstorage';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, lastValueFrom, of } from 'rxjs';
import { catchError, distinctUntilChanged, map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { setUser } from '@sentry/angular-ivy';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { TranslateService } from '@ngx-translate/core';

export interface IAccountService {
    save: (account: any) => Observable<HttpResponse<any>>;
    authenticate: (identity?: User) => void;
    hasAnyAuthority: (authorities: string[]) => Promise<boolean>;
    hasAnyAuthorityDirect: (authorities: string[]) => boolean;
    hasAuthority: (authority: string) => Promise<boolean>;
    identity: (force?: boolean) => Promise<User | undefined>;
    isAtLeastTutorInCourse: (course: Course) => boolean;
    isAtLeastTutorForExercise: (exercise?: Exercise) => boolean;
    isAtLeastEditorInCourse: (course: Course) => boolean;
    isAtLeastEditorForExercise: (exercise?: Exercise) => boolean;
    isAtLeastInstructorForExercise: (exercise?: Exercise) => boolean;
    isAtLeastInstructorInCourse: (course: Course) => boolean;
    isAuthenticated: () => boolean;
    getAuthenticationState: () => Observable<User | undefined>;
    getImageUrl: () => string | undefined;
}

@Injectable({ providedIn: 'root' })
export class AccountService implements IAccountService {
    private userIdentityValue?: User;
    private authenticated = false;
    private authenticationState = new BehaviorSubject<User | undefined>(undefined);
    private prefilledUsernameValue?: string;

    constructor(
        private translateService: TranslateService,
        private sessionStorage: SessionStorageService,
        private http: HttpClient,
        private websocketService: JhiWebsocketService,
        private featureToggleService: FeatureToggleService,
    ) {}

    get userIdentity() {
        return this.userIdentityValue;
    }

    set userIdentity(user: User | undefined) {
        this.userIdentityValue = user;
        this.authenticated = !!user;
        // Alert subscribers about user updates, that is when the user logs in or logs out (undefined).
        this.authenticationState.next(user);

        // We only subscribe the feature toggle updates when the user is logged in, otherwise we unsubscribe them.
        if (user) {
            this.websocketService.enableReconnect();
            this.featureToggleService.subscribeFeatureToggleUpdates();
        } else {
            this.websocketService.disableReconnect();
            this.featureToggleService.unsubscribeFeatureToggleUpdates();
        }
    }

    private fetch(): Observable<HttpResponse<User>> {
        return this.http.get<User>('api/public/account', { observe: 'response' });
    }

    save(user: User): Observable<HttpResponse<any>> {
        return this.http.put('api/account', user, { observe: 'response' });
    }

    authenticate(identity?: User) {
        this.userIdentity = identity;
    }

    syncGroups(groups: string[]) {
        this.userIdentity!.groups = groups;
    }

    hasAnyAuthority(authorities: string[]): Promise<boolean> {
        return Promise.resolve(this.hasAnyAuthorityDirect(authorities));
    }

    hasAnyAuthorityDirect(authorities: string[]): boolean {
        if (!this.authenticated || !this.userIdentity || !this.userIdentity.authorities) {
            return false;
        }

        for (let i = 0; i < authorities.length; i++) {
            if (this.userIdentity.authorities.includes(authorities[i])) {
                return true;
            }
        }

        return false;
    }

    hasAuthority(authority: string): Promise<boolean> {
        if (!this.authenticated) {
            return Promise.resolve(false);
        }

        return this.identity().then(
            (id) => {
                const authorities = id!.authorities!;
                return Promise.resolve(authorities && authorities.includes(authority));
            },
            () => {
                return Promise.resolve(false);
            },
        );
    }

    hasGroup(group?: string): boolean {
        if (!this.authenticated || !this.userIdentity || !this.userIdentity.authorities || !this.userIdentity.groups || !group) {
            return false;
        }

        return this.userIdentity.groups.some((userGroup: string) => userGroup === group);
    }

    identity(force?: boolean): Promise<User | undefined> {
        if (force) {
            this.userIdentity = undefined;
        }

        // check and see if we have retrieved the userIdentity data from the server.
        // if we have, reuse it by immediately resolving
        if (this.userIdentity) {
            return Promise.resolve(this.userIdentity);
        }

        // retrieve the userIdentity data from the server, update the identity object, and then resolve.
        return lastValueFrom(
            this.fetch().pipe(
                map((response: HttpResponse<User>) => {
                    const user = response.body!;
                    if (user) {
                        this.websocketService.connect();
                        this.userIdentity = user;

                        // improved error tracking in sentry
                        setUser({ username: user.login! });

                        // After retrieve the account info, the language will be changed to
                        // the user's preferred language configured in the account setting
                        const langKey = this.userIdentity.langKey || this.sessionStorage.retrieve('locale');
                        this.translateService.use(langKey);
                    } else {
                        this.userIdentity = undefined;
                    }
                    return this.userIdentity;
                }),
                catchError(() => {
                    // this will be called during logout
                    if (this.websocketService.isConnected()) {
                        this.websocketService.disconnect();
                    }
                    this.userIdentity = undefined;
                    return of(undefined);
                }),
            ),
        );
    }

    /**
     * checks if the currently logged-in user is at least tutor in the given course
     * @param course
     */
    isAtLeastTutorInCourse(course?: Course): boolean {
        return (
            this.hasGroup(course?.instructorGroupName) ||
            this.hasGroup(course?.editorGroupName) ||
            this.hasGroup(course?.teachingAssistantGroupName) ||
            this.hasAnyAuthorityDirect([Authority.ADMIN])
        );
    }

    /**
     * checks if the currently logged-in user is at least editor in the given course
     * @param course
     */
    isAtLeastEditorInCourse(course?: Course): boolean {
        return this.hasGroup(course?.instructorGroupName) || this.hasGroup(course?.editorGroupName) || this.hasAnyAuthorityDirect([Authority.ADMIN]);
    }

    /**
     * checks if the currently logged-in user is at least instructor in the given course
     * @param course
     */
    isAtLeastInstructorInCourse(course?: Course): boolean {
        return this.hasGroup(course?.instructorGroupName) || this.hasAnyAuthorityDirect([Authority.ADMIN]);
    }

    /**
     * checks if the currently logged-in user is at least tutor for the exercise (directly) in the course or the exercise in the exam in the course
     * @param exercise
     */
    isAtLeastTutorForExercise(exercise?: Exercise): boolean {
        return this.isAtLeastTutorInCourse(exercise?.course || exercise?.exerciseGroup?.exam?.course);
    }

    /**
     * checks if the currently logged-in user is at least editor for the exercise (directly) in the course or the exercise in the exam in the course
     * @param exercise
     */
    isAtLeastEditorForExercise(exercise?: Exercise): boolean {
        return this.isAtLeastEditorInCourse(exercise?.course || exercise?.exerciseGroup?.exam?.course);
    }

    /**
     * checks if the currently logged-in user is at least instructor for the exercise (directly) in the course or the exercise in the exam in the course
     * @param exercise
     */
    isAtLeastInstructorForExercise(exercise?: Exercise): boolean {
        return this.isAtLeastInstructorInCourse(exercise?.course || exercise?.exerciseGroup?.exam?.course);
    }

    isAdmin(): boolean {
        return this.hasAnyAuthorityDirect([Authority.ADMIN]);
    }

    isAuthenticated(): boolean {
        return this.authenticated;
    }

    getAuthenticationState(): Observable<User | undefined> {
        return this.authenticationState.asObservable().pipe(
            // We don't want to emit here e.g. [undefined, undefined] as it is still the same information [logged out, logged out].
            distinctUntilChanged(),
        );
    }

    setAccessRightsForExerciseAndReferencedCourse(exercise: Exercise) {
        this.setAccessRightsForExercise(exercise);
        if (exercise.course) {
            this.setAccessRightsForCourse(exercise.course);
        }
    }

    setAccessRightsForCourseAndReferencedExercises(course: Course) {
        this.setAccessRightsForCourse(course);
        if (course.exercises) {
            course.exercises.forEach((exercise: Exercise) => this.setAccessRightsForExercise(exercise));
        }
    }

    setAccessRightsForExercise(exercise: Exercise) {
        exercise.isAtLeastTutor = this.isAtLeastTutorForExercise(exercise);
        exercise.isAtLeastEditor = this.isAtLeastEditorForExercise(exercise);
        exercise.isAtLeastInstructor = this.isAtLeastInstructorForExercise(exercise);
    }

    setAccessRightsForCourse(course: Course) {
        course.isAtLeastTutor = this.isAtLeastTutorInCourse(course);
        course.isAtLeastEditor = this.isAtLeastEditorInCourse(course);
        course.isAtLeastInstructor = this.isAtLeastInstructorInCourse(course);
    }

    /**
     * Checks whether current user is owner of the participation or whether he is part of the team
     *
     * @param participation - Participation that is checked
     */
    isOwnerOfParticipation(participation: StudentParticipation): boolean {
        if (participation.student) {
            return this.userIdentity?.login === participation.student.login;
        } else if (participation.team?.students) {
            return participation.team.students.some((student) => this.userIdentity?.login === student.login);
        }
        throw new Error('Participation does not have any owners');
    }

    /**
     * Returns the image url of the user or undefined.
     *
     * Returns undefined if the user is not authenticated or the user does not have an image.
     */
    getImageUrl() {
        return this.isAuthenticated() && this.userIdentity ? this.userIdentity.imageUrl : undefined;
    }

    /**
     * Sets a new language key for the current user
     *
     * @param languageKey The new languageKey
     */
    updateLanguage(languageKey: string): Observable<void> {
        return this.http.post<void>('api/public/account/change-language', languageKey);
    }

    /**
     * Returns the current prefilled username and clears it. Necessary as we don't want to show the prefilled username after a later log out.
     *
     * @returns the prefilled username
     */
    getAndClearPrefilledUsername(): string | undefined {
        const prefilledUsername = this.prefilledUsernameValue;
        this.prefilledUsernameValue = undefined;
        return prefilledUsername;
    }

    /**
     * Sets the prefilled username
     *
     * @param prefilledUsername The new prefilled username
     */
    setPrefilledUsername(prefilledUsername: string) {
        this.prefilledUsernameValue = prefilledUsername;
    }
}
