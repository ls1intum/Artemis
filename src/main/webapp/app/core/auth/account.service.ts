import { Injectable } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { SessionStorageService } from 'ngx-webstorage';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, distinctUntilChanged, map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';

import { Course } from 'app/entities/course/course.model';
import { User } from 'app/core/user/user.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';

export interface IAccountService {
    fetch: () => Observable<HttpResponse<User>>;
    save: (account: any) => Observable<HttpResponse<any>>;
    authenticate: (identity: User | null) => void;
    hasAnyAuthority: (authorities: string[]) => Promise<boolean>;
    hasAnyAuthorityDirect: (authorities: string[]) => boolean;
    hasAuthority: (authority: string) => Promise<boolean>;
    identity: (force?: boolean) => Promise<User | null>;
    isAtLeastTutorInCourse: (course: Course) => boolean;
    isAtLeastInstructorInCourse: (course: Course) => boolean;
    isAuthenticated: () => boolean;
    getAuthenticationState: () => Observable<User | null>;
    getImageUrl: () => string | null;
}

@Injectable({ providedIn: 'root' })
export class AccountService implements IAccountService {
    private userIdentityValue: User | null = null;
    private authenticated = false;
    private authenticationState = new BehaviorSubject<User | null>(null);

    constructor(
        private languageService: JhiLanguageService,
        private sessionStorage: SessionStorageService,
        private http: HttpClient,
        private websocketService: JhiWebsocketService,
        private featureToggleService: FeatureToggleService,
    ) {}

    get userIdentity() {
        return this.userIdentityValue;
    }

    set userIdentity(user: User | null) {
        this.userIdentityValue = user;
        this.authenticated = !!user;
        // Alert subscribers about user updates, that is when the user logs in or logs out (null).
        this.authenticationState.next(user);

        // We only subscribe the feature toggle updates when the user is logged in, otherwise we unsubscribe them.
        if (user) {
            this.featureToggleService.subscribeFeatureToggleUpdates();
        } else {
            this.featureToggleService.unsubscribeFeatureToggleUpdates();
        }
    }

    fetch(): Observable<HttpResponse<User>> {
        return this.http.get<User>(SERVER_API_URL + 'api/account', { observe: 'response' });
    }

    save(account: any): Observable<HttpResponse<any>> {
        return this.http.post(SERVER_API_URL + 'api/account', account, { observe: 'response' });
    }

    authenticate(identity: User | null) {
        this.userIdentity = identity;
    }

    syncGroups(identity: User) {
        this.userIdentity!.groups = identity.groups;
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
            id => {
                const authorities = id!.authorities!;
                return Promise.resolve(authorities && authorities.includes(authority));
            },
            () => {
                return Promise.resolve(false);
            },
        );
    }

    hasGroup(group: string): boolean {
        if (!this.authenticated || !this.userIdentity || !this.userIdentity.authorities || !this.userIdentity.groups) {
            return false;
        }

        return this.userIdentity.groups.some((userGroup: string) => userGroup === group);
    }

    identity(force?: boolean): Promise<User | null> {
        if (force === true) {
            this.userIdentity = null;
        }

        // check and see if we have retrieved the userIdentity data from the server.
        // if we have, reuse it by immediately resolving
        if (this.userIdentity) {
            return Promise.resolve(this.userIdentity);
        }

        // retrieve the userIdentity data from the server, update the identity object, and then resolve.
        return this.fetch()
            .pipe(
                map((response: HttpResponse<User>) => {
                    const user = response.body!;
                    if (user) {
                        this.websocketService.connect();
                        this.userIdentity = user;

                        // After retrieve the account info, the language will be changed to
                        // the user's preferred language configured in the account setting
                        const langKey = this.sessionStorage.retrieve('locale') || this.userIdentity.langKey;
                        this.languageService.changeLanguage(langKey);
                    } else {
                        this.userIdentity = null;
                    }
                    return this.userIdentity;
                }),
                catchError(() => {
                    if (this.websocketService.stompClient && this.websocketService.stompClient.connected) {
                        this.websocketService.disconnect();
                    }
                    this.userIdentity = null;
                    return of(null);
                }),
            )
            .toPromise();
    }

    isAtLeastTutorInCourse(course: Course): boolean {
        return this.hasGroup(course.instructorGroupName) || this.hasGroup(course.teachingAssistantGroupName) || this.hasAnyAuthorityDirect(['ROLE_ADMIN']);
    }

    isAtLeastInstructorInCourse(course: Course) {
        return this.hasGroup(course.instructorGroupName) || this.hasAnyAuthorityDirect(['ROLE_ADMIN']);
    }

    isAuthenticated(): boolean {
        return this.authenticated;
    }

    getAuthenticationState(): Observable<User | null> {
        return this.authenticationState.asObservable().pipe(
            // We don't want to emit here e.g. [null, null] as it is still the same information [logged out, logged out].
            distinctUntilChanged(),
        );
    }

    /**
     * Returns the image url of the user or null.
     *
     * Returns null if the user is not authenticated or the user does not have an image.
     */
    getImageUrl(): string | null {
        return this.isAuthenticated() && this.userIdentity ? this.userIdentity.imageUrl : null;
    }
}
