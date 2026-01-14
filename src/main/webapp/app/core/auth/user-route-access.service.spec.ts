import { TestBed } from '@angular/core/testing';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ActivatedRouteSnapshot, Route, Router, RouterModule } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Mutable } from 'test/helpers/mutable';
import { mockedActivatedRouteSnapshot } from 'test/helpers/mocks/activated-route/mock-activated-route-snapshot';
import { CourseExerciseDetailsComponent } from 'app/core/course/overview/exercise-details/course-exercise-details.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('UserRouteAccessService', () => {
    const routeStateMock: any = { snapshot: {}, url: '/courses/20/exercises/4512' };
    const route = 'courses/:courseId/exercises/:exerciseId';
    let service: UserRouteAccessService;

    let accountService: AccountService;
    let accountServiceStub: jest.SpyInstance;

    let sessionStorageService: SessionStorageService;
    let router: Router;

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    const url = 'test';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([
                    {
                        path: route,
                        component: CourseExerciseDetailsComponent,
                    },
                ]),
            ],
            providers: [
                mockedActivatedRouteSnapshot(route),
                { provide: AccountService, useClass: MockAccountService },
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(CourseExerciseDetailsComponent, '')
            .compileComponents()
            .then(() => {
                service = TestBed.inject(UserRouteAccessService);
                TestBed.createComponent(CourseExerciseDetailsComponent);
                accountService = TestBed.inject(AccountService);
                sessionStorageService = TestBed.inject(SessionStorageService);
                router = TestBed.inject(Router);
            });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should create alert and prefill username for existing LTI users', () => {
        alertServiceStub = jest.spyOn(alertService, 'success');
        accountServiceStub = jest.spyOn(accountService, 'setPrefilledUsername');

        const snapshot = TestBed.inject(ActivatedRouteSnapshot) as Mutable<ActivatedRouteSnapshot>;
        const routeConfig = snapshot.routeConfig as Route;
        routeConfig.path = route;
        snapshot.queryParams = { ['ltiSuccessLoginRequired']: 'username' };
        snapshot.data = { authorities: [Authority.STUDENT] };

        service.canActivate(snapshot, routeStateMock);
        expect(alertServiceStub).toHaveBeenCalledOnce();
        expect(accountServiceStub).toHaveBeenCalledOnce();
        expect(accountServiceStub).toHaveBeenCalledWith('username');
    });

    it('should not create alert and not prefill username for new LTI users', () => {
        alertServiceStub = jest.spyOn(alertService, 'success');
        accountServiceStub = jest.spyOn(accountService, 'setPrefilledUsername');

        const snapshot = TestBed.inject(ActivatedRouteSnapshot) as Mutable<ActivatedRouteSnapshot>;
        const routeConfig = snapshot.routeConfig as Route;
        routeConfig.path = route;
        snapshot.queryParams = {};
        snapshot.data = { authorities: [Authority.STUDENT] };

        service.canActivate(snapshot, routeStateMock);
        expect(alertServiceStub).not.toHaveBeenCalled();
        expect(accountServiceStub).not.toHaveBeenCalled();
    });

    it('should return true if authorities are omitted', async () => {
        await expect(service.checkLogin([], url)).resolves.toBeTrue();
    });

    it('should return false if it does not have authority', async () => {
        jest.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(false));
        const storeSpy = jest.spyOn(sessionStorageService, 'store');

        const result = await service.checkLogin([Authority.EDITOR], url);

        expect(result).toBeFalse();
        expect(storeSpy).not.toHaveBeenCalled();
    });

    it('should store url if identity is undefined', async () => {
        jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));
        const storeSpy = jest.spyOn(sessionStorageService, 'store');
        const navigateMock = jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));

        await expect(service.checkLogin([Authority.EDITOR], url)).resolves.toBeFalse();
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith('previousUrl', url);
        expect(navigateMock).toHaveBeenCalledTimes(2);
        expect(navigateMock.mock.calls[0][0]).toEqual(['accessdenied']);
        expect(navigateMock.mock.calls[1][0]).toEqual(['/']);
    });
});
