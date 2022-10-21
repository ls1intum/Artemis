import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { mockedActivatedRouteSnapshot } from '../helpers/mocks/activated-route/mock-activated-route-snapshot';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { Mutable } from '../helpers/mutable';
import { ArtemisTestModule } from '../test.module';

describe('UserRouteAccessService', () => {
    const routeStateMock: any = { snapshot: {}, url: '/courses/20/exercises/4512?jwt=testToken' };
    const route = 'courses/:courseId/exercises/:exerciseId';
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;
    let service: UserRouteAccessService;

    let accountService: AccountService;
    let storageService: StateStorageService;
    let router: Router;

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    const url = 'test';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                HttpClientTestingModule,
                RouterTestingModule.withRoutes([
                    {
                        path: route,
                        component: CourseExerciseDetailsComponent,
                    },
                ]),
            ],
            declarations: [CourseExerciseDetailsComponent],
            providers: [
                mockedActivatedRouteSnapshot(route),
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(StateStorageService),
            ],
        })
            .overrideTemplate(CourseExerciseDetailsComponent, '')
            .compileComponents()
            .then(() => {
                service = TestBed.inject(UserRouteAccessService);
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                accountService = TestBed.inject(AccountService);
                storageService = TestBed.inject(StateStorageService);
                router = TestBed.inject(Router);
            });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should store the JWT token and create alert for existing LTI users', () => {
        alertServiceStub = jest.spyOn(alertService, 'success');

        const snapshot = fixture.debugElement.injector.get(ActivatedRouteSnapshot) as Mutable<ActivatedRouteSnapshot>;
        const routeConfig = snapshot.routeConfig as Route;
        routeConfig.path = route;
        snapshot.queryParams = { ['jwt']: 'testToken', ['ltiSuccessLoginRequired']: '' };
        snapshot.data = { authorities: [Authority.USER] };

        service.canActivate(snapshot, routeStateMock);
        expect(MockSyncStorage.retrieve('authenticationToken')).toBe('testToken');
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should store the JWT token and not create alert for new LTI users', () => {
        alertServiceStub = jest.spyOn(alertService, 'success');

        const snapshot = fixture.debugElement.injector.get(ActivatedRouteSnapshot) as Mutable<ActivatedRouteSnapshot>;
        const routeConfig = snapshot.routeConfig as Route;
        routeConfig.path = route;
        snapshot.queryParams = { ['jwt']: 'testToken' };
        snapshot.data = { authorities: [Authority.USER] };

        service.canActivate(snapshot, routeStateMock);
        expect(MockSyncStorage.retrieve('authenticationToken')).toBe('testToken');
        expect(alertServiceStub).toHaveBeenCalledTimes(0);
    });

    it('should return true if authorities are omitted', async () => {
        await expect(service.checkLogin([], url)).resolves.toBeTrue();
    });

    it('should store url if identity is undefined', async () => {
        jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));
        const storeSpy = jest.spyOn(storageService, 'storeUrl');
        const navigateMock = jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));

        await expect(service.checkLogin([Authority.EDITOR], url)).resolves.toBeFalse();
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(url);
        expect(navigateMock).toHaveBeenCalledTimes(2);
        expect(navigateMock.mock.calls[0][0]).toEqual(['accessdenied']);
        expect(navigateMock.mock.calls[1][0]).toEqual(['/']);
    });
});
