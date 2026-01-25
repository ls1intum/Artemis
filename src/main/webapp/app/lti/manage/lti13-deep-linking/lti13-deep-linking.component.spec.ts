import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Lti13DeepLinkingComponent } from 'app/lti/manage/lti13-deep-linking/lti13-deep-linking.component';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpParams, HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject, of, throwError } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { DeepLinkingType } from 'app/lti/manage/lti13-deep-linking/lti.constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/shared/constants/authority.constants';

describe('Lti13DeepLinkingComponent', () => {
    setupTestBed({ zoneless: true });
    let component: Lti13DeepLinkingComponent;
    let fixture: ComponentFixture<Lti13DeepLinkingComponent>;
    let routeParamsSubject: Subject<{ courseId?: string }>;
    let activatedRouteMock: { params: Subject<{ courseId?: string }> };

    let routerMock: { navigate: ReturnType<typeof vi.fn> };
    let httpMock: { post: ReturnType<typeof vi.fn> };
    let courseManagementServiceMock: { findWithExercisesAndLecturesAndCompetencies: ReturnType<typeof vi.fn> };
    let accountServiceMock: {
        identity: ReturnType<typeof vi.fn>;
        getAuthenticationState: ReturnType<typeof vi.fn>;
        hasAnyAuthority: ReturnType<typeof vi.fn>;
    };
    let sortServiceMock: { sortByProperty: ReturnType<typeof vi.fn> };
    let alertServiceMock: { error: ReturnType<typeof vi.fn>; addAlert: ReturnType<typeof vi.fn> };

    const exercise1 = { id: 1, shortName: 'git', type: ExerciseType.PROGRAMMING } as Exercise;
    const exercise2 = { id: 2, shortName: 'test', type: ExerciseType.PROGRAMMING } as Exercise;
    const exercise3 = { id: 3, shortName: 'git', type: ExerciseType.MODELING } as Exercise;
    const course = { id: 123, shortName: 'tutorial', exercises: [exercise2, exercise1, exercise3] } as Course;

    beforeEach(async () => {
        routeParamsSubject = new Subject<{ courseId?: string }>();
        activatedRouteMock = { params: routeParamsSubject };
        routerMock = { navigate: vi.fn().mockReturnValue(Promise.resolve(true)) };
        httpMock = { post: vi.fn() };
        courseManagementServiceMock = { findWithExercisesAndLecturesAndCompetencies: vi.fn() };
        accountServiceMock = {
            identity: vi.fn().mockResolvedValue(undefined),
            getAuthenticationState: vi.fn().mockReturnValue(of(null)),
            hasAnyAuthority: vi.fn().mockResolvedValue(true),
        };
        sortServiceMock = { sortByProperty: vi.fn() };
        alertServiceMock = { error: vi.fn(), addAlert: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [Lti13DeepLinkingComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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

        vi.spyOn(console, 'error').mockImplementation(() => {});
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(Lti13DeepLinkingComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        routeParamsSubject.complete();
        vi.clearAllMocks();
    });

    it('should retrieve course details and exercises on init when user is authenticated', async () => {
        const loggedInUserUser: User = { id: 3, login: 'lti_user', firstName: 'TestUser', lastName: 'Moodle' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUserUser));
        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: course })));

        component.ngOnInit();
        routeParamsSubject.next({ courseId: '123' });
        await vi.waitFor(() => {
            expect(accountServiceMock.identity).toHaveBeenCalled();
        });
        await fixture.whenStable();

        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(course.id);
        expect(component.courseId).toBe(123);
        expect(component.course).toEqual(course);
        expect(component.exercises).toEqual(expect.arrayContaining(course.exercises!));
    });

    it('should alert user when no exercise is selected', () => {
        component.selectedExercises = undefined;
        component.sendDeepLinkRequest();
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.lti13.deepLinking.selectToLink');

        component.selectedExercises = new Set();
        component.sendDeepLinkRequest();
        expect(alertServiceMock.error).toHaveBeenNthCalledWith(2, 'artemisApp.lti13.deepLinking.selectToLink');
    });

    it('should navigate on init when user is not authenticated', async () => {
        const redirectSpy = vi.spyOn(component, 'redirectUserToLoginThenTargetLink');
        accountServiceMock.identity.mockResolvedValue(undefined);
        accountServiceMock.getAuthenticationState.mockReturnValue(of());

        component.ngOnInit();
        routeParamsSubject.next({ courseId: '123' });
        await vi.waitFor(() => {
            expect(redirectSpy).toHaveBeenCalledWith(window.location.href);
        });

        expect(routerMock.navigate).toHaveBeenCalledWith(['/']);
    });

    it('should not load course details and exercises on init when courseId is empty', async () => {
        component.ngOnInit();
        routeParamsSubject.next({});
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);
        expect(accountServiceMock.identity).not.toHaveBeenCalled();
        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).not.toHaveBeenCalled();
        expect(component.courseId).toBeNaN();
    });

    it('should not send deep link request when exercise is not selected', () => {
        component.selectedExercises = undefined;

        component.sendDeepLinkRequest();

        expect(httpMock.post).not.toHaveBeenCalled();
    });

    it('should set isDeepLinking to false if the response status is not 200', async () => {
        component.selectExercise(exercise1.id);
        component.selectExercise(exercise2.id);
        component.courseId = 123;
        const nonSuccessResponse = new HttpResponse({
            status: 400,
            body: { message: 'Bad request' },
        });
        httpMock.post.mockReturnValue(of(nonSuccessResponse));

        component.sendDeepLinkRequest();
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);
        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: new HttpParams()
                .set('resourceType', DeepLinkingType.EXERCISE)
                .set('ltiIdToken', '')
                .set('clientRegistrationId', '')
                .set('contentIds', Array.from(component.selectedExercises!).join(',')),
        });
    });

    it('should set isLinking to false if there is an error during the HTTP request', async () => {
        component.selectExercise(exercise1.id);
        component.courseId = 123;
        const mockError = new Error('Network error');
        httpMock.post.mockReturnValue(throwError(() => mockError));

        component.sendDeepLinkRequest();
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);

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

    it('should send deep link request and navigate when exercise is selected', () => {
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

    it('should retrieve course lectures on init when user is authenticated', async () => {
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
        routeParamsSubject.next({ courseId: '123' });
        await vi.waitFor(() => {
            expect(accountServiceMock.identity).toHaveBeenCalledOnce();
        });
        await fixture.whenStable();

        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(course.id);
        expect(component.course).toEqual(extendedCourse);
        expect(component.lectures).toEqual([lecture1, lecture2]);
    });

    it('should handle empty lectures gracefully', async () => {
        const loggedInUser: User = { id: 3, login: 'lti_user' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUser));

        const emptyCourse = {
            ...course,
            lectures: [],
        };

        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: emptyCourse })));

        component.ngOnInit();
        routeParamsSubject.next({ courseId: '123' });
        await vi.waitFor(() => {
            expect(accountServiceMock.identity).toHaveBeenCalledOnce();
        });
        await fixture.whenStable();

        expect(courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies).toHaveBeenCalledWith(course.id);
        expect(component.course).toEqual(emptyCourse);
        expect(component.lectures).toEqual([]);
    });

    it('should select and deselect a competency', () => {
        component.isCompetencySelected = false;
        component.enableCompetency();
        expect(component.isCompetencySelected).toBe(true);
        expect(component.isLearningPathSelected).toBe(false);
        expect(component.isIrisSelected).toBe(false);

        component.enableCompetency();
        expect(component.isCompetencySelected).toBe(true);
    });

    it('should select and deselect a learning path', () => {
        component.isLearningPathSelected = false;
        component.enableLearningPath();
        expect(component.isLearningPathSelected).toBe(true);
        expect(component.isCompetencySelected).toBe(false);
        expect(component.isIrisSelected).toBe(false);

        component.enableLearningPath();
        expect(component.isLearningPathSelected).toBe(true);
    });

    it('should send deep link request when competency is selected', async () => {
        component.enableCompetency();
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        await fixture.whenStable();

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
    });

    it('should send deep link request when learning path is selected', async () => {
        component.enableLearningPath();
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        await fixture.whenStable();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.LEARNING_PATH).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    });

    it('should send deep link request when IRIS is selected', async () => {
        component.enableIris();
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        await fixture.whenStable();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.IRIS).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    });

    it('should send deep link request when lectures are selected', async () => {
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
        await fixture.whenStable();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.LECTURE).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '1,2');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    });

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
    });

    it('should handle empty course gracefully', async () => {
        const loggedInUser: User = { id: 3, login: 'lti_user' } as User;
        accountServiceMock.identity.mockReturnValue(Promise.resolve(loggedInUser));

        const emptyCourse = { id: 123, shortName: 'tutorial', exercises: [], lectures: [] } as Course;
        courseManagementServiceMock.findWithExercisesAndLecturesAndCompetencies.mockReturnValue(of(new HttpResponse({ body: emptyCourse })));

        component.ngOnInit();
        routeParamsSubject.next({ courseId: '123' });
        await vi.waitFor(() => {
            expect(accountServiceMock.identity).toHaveBeenCalled();
        });
        await fixture.whenStable();

        expect(component.course).toEqual(emptyCourse);
        expect(component.exercises).toEqual([]);
        expect(component.lectures).toEqual([]);
    });

    it('should invoke account service using jhiHasAnyAuthority directive', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(accountServiceMock.hasAnyAuthority).toHaveBeenCalledWith(IS_AT_LEAST_INSTRUCTOR);
    });

    it('should toggle exercise selection correctly', () => {
        component.selectExercise(1);
        expect(component.selectedExercises?.has(1)).toBe(true);

        component.selectExercise(1);
        expect(component.selectedExercises?.has(1)).toBe(false);
    });

    it('should check if exercise is selected correctly', () => {
        expect(component.isExerciseSelected(1)).toBe(false);

        component.selectExercise(1);
        expect(component.isExerciseSelected(1)).toBe(true);

        expect(component.isExerciseSelected(undefined)).toBe(false);
    });

    it('should toggle lecture selection correctly', () => {
        component.selectLecture(1);
        expect(component.selectedLectures?.has(1)).toBe(true);

        component.selectLecture(1);
        expect(component.selectedLectures?.has(1)).toBe(false);
    });

    it('should check if lecture is selected correctly', () => {
        expect(component.isLectureSelected(1)).toBe(false);

        component.selectLecture(1);
        expect(component.isLectureSelected(1)).toBe(true);

        expect(component.isLectureSelected(undefined)).toBe(false);
    });

    it('should activate exercise grouping', () => {
        expect(component.isExerciseGroupingActive).toBe(false);
        component.activateExerciseGrouping();
        expect(component.isExerciseGroupingActive).toBe(true);
    });

    it('should activate lecture grouping', () => {
        expect(component.isLectureGroupingActive).toBe(false);
        component.activateLectureGrouping();
        expect(component.isLectureGroupingActive).toBe(true);
    });

    it('should enable Iris and disable other selections', () => {
        component.isCompetencySelected = true;
        component.isLearningPathSelected = true;
        component.enableIris();

        expect(component.isIrisSelected).toBe(true);
        expect(component.isCompetencySelected).toBe(false);
        expect(component.isLearningPathSelected).toBe(false);
    });

    it('should send grouped exercise deep link request when exercise grouping is active', async () => {
        component.activateExerciseGrouping();
        component.selectExercise(exercise1.id);
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        await fixture.whenStable();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.GROUPED_EXERCISE).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '1');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    });

    it('should send grouped lecture deep link request when lecture grouping is active', async () => {
        component.activateLectureGrouping();
        component.selectLecture(1);
        component.courseId = 123;

        const mockResponse = new HttpResponse({
            status: 200,
            body: { targetLinkUri: 'http://example.com/deep_link' },
        });

        httpMock.post.mockReturnValue(of(mockResponse));
        component.sendDeepLinkRequest();
        await fixture.whenStable();

        const expectedParams = new HttpParams().set('resourceType', DeepLinkingType.GROUPED_LECTURE).set('ltiIdToken', '').set('clientRegistrationId', '').set('contentIds', '1');

        expect(httpMock.post).toHaveBeenCalledWith(`api/lti/lti13/deep-linking/${component.courseId}`, null, {
            observe: 'response',
            params: expectedParams,
        });
    });

    it('should not select exercise with undefined id', () => {
        const initialSize = component.selectedExercises?.size ?? 0;
        component.selectExercise(undefined);
        expect(component.selectedExercises?.size ?? 0).toBe(initialSize);
    });

    it('should not select lecture with undefined id', () => {
        const initialSize = component.selectedLectures?.size ?? 0;
        component.selectLecture(undefined);
        expect(component.selectedLectures?.size ?? 0).toBe(initialSize);
    });
});
