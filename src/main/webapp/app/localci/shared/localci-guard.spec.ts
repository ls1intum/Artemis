import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { SpyLocation, provideLocationMocks } from '@angular/common/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AlertService } from 'app/foundation/service/alert.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Authority, IS_AT_LEAST_INSTRUCTOR } from 'app/foundation/constants/authority.constants';
import { User } from 'app/account/user/user.model';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalCIGuard } from 'app/localci/shared/localci-guard.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

@Component({ template: '' })
class DummyComponent {}

describe('LocalCIGuard', () => {
    let guard: LocalCIGuard;
    let router: Router;
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LocalCIGuard, { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(LocalCIGuard);
        router = TestBed.inject(Router);
        profileService = TestBed.inject(ProfileService);
    });

    it('should allow access if PROFILE_LOCALCI is active', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        expect(guard.canActivate()).toBe(true);
    });

    it('should not allow access if PROFILE_LOCALCI is not active', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [] } as unknown as ProfileInfo);
        const result = guard.canActivate();
        expect(result).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(result as UrlTree)).toBe('/courses');
    });
});

// The course build overview lists both guards in one canActivate array: [UserRouteAccessService, LocalCIGuard]. The
// router applies the first result that is not true, in array order, so the redirect only applies to a user who has
// passed the authority check. These cases run on an instance without the local CI profile.
describe('LocalCIGuard after UserRouteAccessService in one canActivate array', () => {
    let router: Router;
    let location: SpyLocation;

    const buildOverview = '/course-management/1/build-overview';

    /** Lets the promise chains of both guards, and any navigation they start, run to completion. */
    const settle = () => new Promise((resolve) => setTimeout(resolve));

    const configureFor = (authorities: Authority[]) => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                provideLocationMocks(),
                provideRouter([
                    {
                        path: 'course-management/:courseId/build-overview',
                        component: DummyComponent,
                        data: { authorities: IS_AT_LEAST_INSTRUCTOR },
                        canActivate: [UserRouteAccessService, LocalCIGuard],
                    },
                    { path: '**', component: DummyComponent },
                ]),
                MockProvider(AlertService),
                MockProvider(SessionStorageService),
                MockProvider(ProfileService, { isProfileActive: () => false }),
                MockProvider(AccountService, {
                    identity: () => Promise.resolve({ id: 1, login: 'user', authorities } as User),
                    hasAnyAuthority: (required: readonly Authority[]) => Promise.resolve(required.some((authority) => authorities.includes(authority))),
                }),
            ],
        });
        router = TestBed.inject(Router);
        location = TestBed.inject(Location) as SpyLocation;
    };

    it('redirects an instructor to the course overview', async () => {
        configureFor([Authority.INSTRUCTOR]);
        await router.navigate(['/course-management', 1]);

        const navigated = await router.navigate([buildOverview]);

        expect(navigated).toBe(true);
        expect(router.url).toBe('/courses');
    });

    it('rejects a tutor, who lacks the authority, instead of redirecting', async () => {
        configureFor([Authority.TUTOR]);
        await router.navigate(['/course-management', 1]);

        const navigated = await router.navigate([buildOverview]);
        await settle();

        expect(navigated).toBe(false);
        expect(router.url).toBe('/course-management/1');
    });

    // A redirect keeps the replaceUrl of the navigation it replaces, and the first navigation after a page load runs
    // with replaceUrl. A guarded URL opened directly is therefore replaced by the redirect target, so Back does not
    // return to it and redirect forward again.
    it('replaces a guarded URL opened directly instead of adding the redirect target after it', async () => {
        configureFor([Authority.INSTRUCTOR]);
        location.setInitialPath(buildOverview);

        router.initialNavigation();
        await settle();

        expect(location.path()).toBe('/courses');
        expect(location.urlChanges).toEqual(['replace: /courses']);
    });

    it('adds the redirect target to the history for a navigation within Artemis', async () => {
        configureFor([Authority.INSTRUCTOR]);
        location.setInitialPath('/course-management/1');
        router.initialNavigation();
        await settle();

        await router.navigate([buildOverview]);

        expect(location.path()).toBe('/courses');
        expect(location.urlChanges).toEqual(['/courses']);
    });
});
