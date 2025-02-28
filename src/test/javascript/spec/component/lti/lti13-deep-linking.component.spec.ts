import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { Lti13DeepLinkingComponent } from 'app/lti/lti13-deep-linking.component';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { of, throwError } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

function HttpLoaderFactory(http: HttpClient) {
    return new TranslateHttpLoader(http);
}

describe('Lti13DeepLinkingComponent', () => {
    let component: Lti13DeepLinkingComponent;
    let fixture: ComponentFixture<Lti13DeepLinkingComponent>;
    let activatedRouteMock: any;

    const routerMock = { navigate: jest.fn() };
    const httpMock = { post: jest.fn() };
    const courseManagementServiceMock = { findWithExercises: jest.fn() };
    const accountServiceMock = { identity: jest.fn(), getAuthenticationState: jest.fn(), hasAnyAuthority: jest.fn().mockResolvedValue(true) };
    const sortServiceMock = { sortByProperty: jest.fn() };
    const alertServiceMock = { error: jest.fn(), addAlert: jest.fn() };

    const exercise1 = { id: 1, shortName: 'git', type: ExerciseType.PROGRAMMING } as Exercise;
    const exercise2 = { id: 2, shortName: 'test', type: ExerciseType.PROGRAMMING } as Exercise;
    const exercise3 = { id: 3, shortName: 'git', type: ExerciseType.MODELING } as Exercise;
    const course = { id: 123, shortName: 'tutorial', exercises: [exercise2, exercise1, exercise3] } as Course;

    beforeEach(waitForAsync(() => {
        activatedRouteMock = { params: of({ courseId: '123' }) };

        TestBed.configureTestingModule({
            imports: [
                Lti13DeepLinkingComponent,
                TranslateModule.forRoot({
                    loader: {
                        provide: TranslateLoader,
                        useFactory: HttpLoaderFactory,
                        deps: [HttpClient],
                    },
                }),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: activatedRouteMock },
                { provide: Router, useValue: routerMock },
                { provide: HttpClient, useValue: httpMock },
                { provide: CourseManagementService, useValue: courseManagementServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: SortService, useValue: sortServiceMock },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AlertService, useValue: alertServiceMock },
                TranslateService,
            ],
        }).compileComponents();
        jest.spyOn(console, 'error').mockImplementation(() => {});
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(Lti13DeepLinkingComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should retrieve course details and exercises on init when user is authenticated', fakeAsync(() => {
        const loggedInUserUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUserUser));
        courseManagementServiceMock.findWithExercises.mockReturnValue(of(new HttpResponse({ body: course })));

        component.ngOnInit();
        tick(1000);

        expect(accountServiceMock.identity).toHaveBeenCalled();
        expect(courseManagementServiceMock.findWithExercises).toHaveBeenCalledWith(course.id);
        expect(component.courseId).toBe(123);
        expect(component.course).toEqual(course);
        expect(component.exercises).toContainAllValues(course.exercises!);
    }));

    it('should alert user when no exercise is selected', () => {
        component.selectedExercises = undefined;
        component.sendDeepLinkRequest();
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.lti13.deepLinking.selectToLink');

        component.selectedExercises = new Set();
        component.sendDeepLinkRequest();
        expect(alertServiceMock.error).toHaveBeenNthCalledWith(2, 'artemisApp.lti13.deepLinking.selectToLink');
    });

    it('should navigate on init when user is authenticated', fakeAsync(() => {
        const redirectSpy = jest.spyOn(component, 'redirectUserToLoginThenTargetLink');
        accountServiceMock.identity.mockResolvedValue(undefined);
        routerMock.navigate.mockReturnValue(Promise.resolve({}));
        accountServiceMock.getAuthenticationState.mockReturnValue(of());

        component.ngOnInit();
        tick();

        expect(redirectSpy).toHaveBeenCalledWith(window.location.href);
        expect(routerMock.navigate).toHaveBeenCalledWith(['/']);
        expect(component.redirectUserToLoginThenTargetLink).toHaveBeenCalled();
    }));

    it('should not course details and exercises on init when courseId is empty', fakeAsync(() => {
        activatedRouteMock.params = of({});
        fixture = TestBed.createComponent(Lti13DeepLinkingComponent);
        component = fixture.componentInstance;
        // Manually set the activatedRouteMock to component here
        component.route = activatedRouteMock;

        component.ngOnInit();
        tick(1000);

        expect(component.isLinking).toBeFalse();
        expect(accountServiceMock.identity).not.toHaveBeenCalled();
        expect(courseManagementServiceMock.findWithExercises).not.toHaveBeenCalled();
        expect(component.courseId).toBeNaN();
    }));

    it('should not send deep link request when exercise is not selected', () => {
        component.selectedExercises = undefined;

        component.sendDeepLinkRequest();

        expect(httpMock.post).not.toHaveBeenCalled();
    });

    it('should set isDeepLinking to false if the response status is not 200', fakeAsync(() => {
        const replaceMock = jest.fn();
        // TODO: change the test to avoid mocking window.location
        component.selectExercise(exercise1.id);
        component.selectExercise(exercise2.id);
        component.courseId = 123;
        const nonSuccessResponse = new HttpResponse({
            status: 400,
            body: { message: 'Bad request' },
        });
        httpMock.post.mockReturnValue(of(nonSuccessResponse));

        component.sendDeepLinkRequest();
        tick();

        expect(component.isLinking).toBeFalse();
        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: new HttpParams().set('exerciseIds', Array.from(component.selectedExercises!).join(',')).set('ltiIdToken', '').set('clientRegistrationId', ''),
        });
        expect(replaceMock).not.toHaveBeenCalled(); // Verify that we did not navigate
    }));

    it('should set isLinking to false if there is an error during the HTTP request', fakeAsync(() => {
        component.selectExercise(exercise1.id);
        component.courseId = 123;
        const mockError = new Error('Network error');
        httpMock.post.mockReturnValue(throwError(() => mockError));

        component.sendDeepLinkRequest();
        tick();

        expect(component.isLinking).toBeFalse();
        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: new HttpParams().set('exerciseIds', Array.from(component.selectedExercises!).join(',')).set('ltiIdToken', '').set('clientRegistrationId', ''),
        });
    }));

    it('should send deep link request and navigate when exercise is selected', () => {
        // TODO: change the test to avoid mocking window.location
        component.selectExercise(exercise1.id);
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: new HttpParams().set('exerciseIds', Array.from(component.selectedExercises!).join(',')).set('ltiIdToken', '').set('clientRegistrationId', ''),
        });
    });

    it('should invoke account service using jhiHasAnyAuthority directive', () => {
        fixture.detectChanges();
        expect(accountServiceMock.hasAnyAuthority).toHaveBeenCalledWith(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
    });
});
