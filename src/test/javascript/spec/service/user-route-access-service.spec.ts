import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ActivatedRouteSnapshot, Route } from '@angular/router';
import { ArtemisTestModule } from '../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../mocks/mock-translate.service';
import { MockSyncStorage } from '../mocks/mock-sync.storage';
import { MockCookieService } from '../mocks/mock-cookie.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../mocks/mock-account.service';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie-service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Mutable } from '../helpers/mutable';
import { mockedActivatedRouteSnapshot } from '../helpers/mock-activated-route-snapshot';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('UserRouteAccessService', () => {
    let comp: CourseExerciseDetailsComponent;
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;
    let service: UserRouteAccessService;
    let routeStateMock: any = { snapshot: {}, url: '/' };
    let route = 'courses/:courseId/exercises/:exerciseId';

    beforeEach(async(() => {
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
                { provide: CookieService, useClass: MockCookieService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: DeviceDetectorService },
            ],
        })
            .overrideTemplate(CourseExerciseDetailsComponent, '')
            .compileComponents()
            .then(() => {
                service = TestBed.inject(UserRouteAccessService);
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                comp = fixture.componentInstance;
            });
    }));

    it('should store the JWT token for LTI users', () => {
        const snapshot = fixture.debugElement.injector.get(ActivatedRouteSnapshot) as Mutable<ActivatedRouteSnapshot>;
        const routeConfig = snapshot.routeConfig as Route;
        routeConfig.path = route;
        snapshot.queryParams = { ['jwt']: 'testToken' };
        snapshot.data = { authorities: ['ROLE_USER'] };

        service.canActivate(snapshot, routeStateMock);
        expect(MockSyncStorage.retrieve('authenticationToken')).to.equal('testToken');
    });
});
