import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Lti13DeepLinkingComponent } from 'app/lti/manage/lti13-deep-linking/lti13-deep-linking.component';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { DeepLinkingType } from 'app/lti/manage/lti13-deep-linking/lti.constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/shared/constants/authority.constants';

describe('Lti13DeepLinkingComponent', () => {
    let component: Lti13DeepLinkingComponent;
    let fixture: ComponentFixture<Lti13DeepLinkingComponent>;
    let activatedRouteMock: any;

    const routerMock = { navigate: jest.fn() };
    const httpMock = { post: jest.fn() };
    const courseManagementServiceMock = { findWithExercisesAndLecturesAndCompetencies: jest.fn() };
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
            imports: [Lti13DeepLinkingComponent],
            providers: [
                { provide: ActivatedRoute, useValue: activatedRouteMock },
                { provide: Router, useValue: routerMock },
                { provide: HttpClient, useValue: httpMock },
                { provide: CourseManagementService, useValue: courseManagementServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: SortService, useValue: sortServiceMock },
                SessionStorageService,
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
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
        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: course })));

        component.ngOnInit();
        tick(1000);

        expect(accountServiceMock.identity).toHaveBeenCalled();
        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(course.id);
        expect(component.courseId).toBe(123);
        expect(component.course).toEqual(course);
        expect(component.exercises).toIncludeAllMembers(course.exercises!);
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

        component.ngOnInit();
        tick(1000);

        expect(component.isLinking).toBeFalse();
        expect(accountServiceMock.identity).not.toHaveBeenCalled();
        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).not.toHaveBeenCalled();
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
            params: new HttpParams()
                .set('resourceType', DeepLinkingType.EXERCISE)
                .set('ltiIdToken', '')
                .set('clientRegistrationId', '')
                .set('contentIds', Array.from(component.selectedExercises!).join(',')),
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

        const expectedParams = new HttpParams()
            .set('resourceType', DeepLinkingType.EXERCISE)
            .set('ltiIdToken', '')
            .set('clientRegistrationId', '')
            .set('contentIds', Array.from(component.selectedExercises!).join(','));

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
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

        const expectedParams = new HttpParams()
            .set('resourceType', DeepLinkingType.EXERCISE)
            .set('ltiIdToken', '')
            .set('clientRegistrationId', '')
            .set('contentIds', Array.from(component.selectedExercises!).join(','));

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    });

    it('should retrieve course lectures on init when user is authenticated', fakeAsync(() => {
        const loggedInUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUser));

        const lecture1 = { id: 1, title: 'Introduction to LTI' };
        const lecture2 = { id: 2, title: 'Advanced LTI Concepts' };

        const extendedCourse = {
            ...course,
            lectures: [lecture1, lecture2],
        };

        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: extendedCourse })));

        component.ngOnInit();
        tick(1000);

        expect(accountServiceMock.identity).toHaveBeenCalledOnce();
        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(course.id);
        expect(component.course).toEqual(extendedCourse);
        expect(component.lectures).toEqual([lecture1, lecture2]);
    }));

    it('should handle empty lectures gracefully', fakeAsync(() => {
        const loggedInUser: User = { id: 3, login: 'lti_user' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUser));

        const emptyCourse = {
            ...course,
            lectures: [],
        };

        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: emptyCourse })));

        component.ngOnInit();
        tick(1000);

        expect(accountServiceMock.identity).toHaveBeenCalledOnce();
        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledExactlyOnceWith(course.id);
        expect(component.course).toEqual(emptyCourse);
        expect(component.lectures).toEqual([]);
    }));

    it('should select and deselect a competency', () => {
        component.isCompetencySelected = false;
        component.enableCompetency();
        expect(component.isCompetencySelected).toBeTrue();
        expect(component.isLearningPathSelected).toBeFalse();
        expect(component.isIrisSelected).toBeFalse();

        component.enableCompetency();
        expect(component.isCompetencySelected).toBeTrue();
    });

    it('should select and deselect a learning path', () => {
        component.isLearningPathSelected = false;
        component.enableLearningPath();
        expect(component.isLearningPathSelected).toBeTrue();
        expect(component.isCompetencySelected).toBeFalse();
        expect(component.isIrisSelected).toBeFalse();

        component.enableLearningPath();
        expect(component.isLearningPathSelected).toBeTrue();
    });

    it('should send deep link request when competency is selected', fakeAsync(() => {
        component.enableCompetency();
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        tick();

        const expectedParams = new HttpParams()
            .set('resourceType', DeepLinkingType.COMPETENCY)
            .set('ltiIdToken', '')
            .set('clientRegistrationId', '')
            .set('contentIds', '')
            .toString();

        expect(httpMock.post).toHaveBeenCalledWith(
            `api/lti/lti13/deep-linking/${component.courseId}`,
            null,
            expect.objectContaining({
                observe: 'response',
                params: expect.objectContaining({
                    toString: expect.any(Function),
                }),
            }),
        );

        expect(httpMock.post.mock.calls[0][2].params.toString()).toBe(expectedParams);
    }));

    it('should send deep link request when learning path is selected', fakeAsync(() => {
        component.enableLearningPath();
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        tick();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.LEARNING_PATH).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    }));

    it('should send deep link request when IRIS is selected', fakeAsync(() => {
        component.enableIris();
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        tick();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.IRIS).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    }));

    it('should send deep link request when lectures are selected', fakeAsync(() => {
        const lecture1 = { id: 1, title: 'Introduction to LTI' };
        const lecture2 = { id: 2, title: 'Advanced LTI Concepts' };
        component.lectures = [lecture1, lecture2];
        component.selectLecture(lecture1.id);
        component.selectLecture(lecture2.id);
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        tick();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.LECTURE).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '1,2');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    }));

    it('should show an error when no content is selected', () => {
        component.selectedExercises = new Set();
        component.selectedLectures = new Set();
        component.isCompetencySelected = false;
        component.isLearningPathSelected = false;
        component.isIrisSelected = false;

        component.sendDeepLinkRequest();

        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.lti13.deepLinking.selectToLink');
        expect(httpMock.post).not.toHaveBeenCalled();
    });

    it('should sort exercises by title in ascending order', () => {
        component.exercises = [exercise1, exercise2, exercise3];
        component.predicate = 'title';
        component.reverse = false;

        component.sortRows();

        expect(sortServiceMock.sortByProperty).toHaveBeenCalledWith(component.exercises, 'title', false);
        expect(component.exercises[0].title).toBe(exercise1.title);
        expect(component.exercises[1].title).toBe(exercise3.title);
        expect(component.exercises[2].title).toBe(exercise2.title);
    });

    it('should handle empty course gracefully', fakeAsync(() => {
        const loggedInUser: User = { id: 3, login: 'lti_user' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUser));

        const emptyCourse = { id: 123, shortName: 'tutorial', exercises: [], lectures: [] } as Course;
        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: emptyCourse })));

        component.ngOnInit();
        tick(1000);

        expect(component.course).toEqual(emptyCourse);
        expect(component.exercises).toEqual([]);
        expect(component.lectures).toEqual([]);
    }));

    it('should invoke account service using jhiHasAnyAuthority directive', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(accountServiceMock.hasAnyAuthority).toHaveBeenCalledWith(IS_AT_LEAST_INSTRUCTOR);
    });
});
