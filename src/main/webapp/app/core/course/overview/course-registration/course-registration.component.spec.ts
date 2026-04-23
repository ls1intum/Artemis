import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseRegistrationComponent } from 'app/core/course/overview/course-registration/course-registration.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { BehaviorSubject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CoursePrerequisitesButtonComponent } from 'app/core/course/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CourseRegistrationButtonComponent } from 'app/core/course/overview/course-registration/course-registration-button/course-registration-button.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('CourseRegistrationComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseRegistrationComponent>;
    let component: CourseRegistrationComponent;
    let courseService: CourseManagementService;
    let router: Router;
    let findAllForRegistrationStub: ReturnType<typeof vi.spyOn>;
    let navigateSpy: ReturnType<typeof vi.spyOn>;

    let queryParamMapSubject: BehaviorSubject<any>;
    let dataSubject: BehaviorSubject<any>;

    const course1 = {
        id: 1,
        title: 'Course A',
        semester: 'SS25/26',
    } as Course;

    const course2 = {
        id: 2,
        title: 'Course B',
        semester: 'WS22/23',
    };
    const course3 = {
        id: 3,
    };

    beforeEach(async () => {
        queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));
        dataSubject = new BehaviorSubject({ defaultSort: 'title,asc' });

        const mockActivatedRoute = {
            data: dataSubject.asObservable(),
            queryParamMap: queryParamMapSubject.asObservable(),
            parent: null,
        };

        TestBed.configureTestingModule({
            imports: [
                CourseRegistrationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CoursePrerequisitesButtonComponent),
                MockComponent(CourseRegistrationButtonComponent),
            ],
            providers: [
                MockProvider(AccountService),
                MockProvider(CourseManagementService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: Router, useClass: MockRouter },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseRegistrationComponent);
        component = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
        router = TestBed.inject(Router);

        findAllForRegistrationStub = vi.spyOn(courseService, 'findAllForRegistration').mockReturnValue(of(new HttpResponse({ body: [course1] })));
        navigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show registrable courses', () => {
        component.loadRegistrableCourses();
        expect(component.coursesToSelect).toHaveLength(1);
        expect(findAllForRegistrationStub).toHaveBeenCalledOnce();
    });

    it('should be able to remove courses from its list', () => {
        component.loadRegistrableCourses();
        component.removeCourseFromList(course1.id!);

        expect(component.coursesToSelect).toHaveLength(0);
    });

    it('should filter registrable courses based on search term', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2, course3] })));

        component.loadRegistrableCourses();
        component.searchTermString = 'Course A';
        component.applySearch();

        expect(component.filteredCoursesToSelect).toHaveLength(1);
        expect(component.filteredCoursesToSelect[0]).toEqual(course1);
    });

    it('should sort registrable courses by title in ascending order', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course3, course2, course1] })));

        component.predicate = 'title';
        component.ascending = true;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course1, course2, course3]);
    });

    it('should sort registrable courses by title in descending order', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course3, course2] })));

        component.predicate = 'title';
        component.ascending = false;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course3, course2, course1]);
    });

    it('should sort registrable courses by semester in ascending order', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course2, course3, course1] })));
        component.predicate = 'semester';
        component.ascending = true;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course1, course2, course3]);
    });

    it('should sort registrable courses by semester in descending order', async () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course2, course3, course1] })));
        component.predicate = 'semester';
        component.ascending = false;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course3, course2, course1]);
    });

    it('should call transition and update URL with sort parameters', () => {
        const activatedRoute = TestBed.inject(ActivatedRoute);
        component.predicate = 'title';
        component.ascending = true;
        component.searchTermString = 'test';

        component.transition();

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/courses/enroll'], {
            relativeTo: activatedRoute.parent,
            queryParams: {
                sort: 'title,asc',
                search: 'test',
            },
        });
    });

    it('should call transition with descending sort', () => {
        const activatedRoute = TestBed.inject(ActivatedRoute);
        component.predicate = 'semester';
        component.ascending = false;
        component.searchTermString = '';

        component.transition();

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/courses/enroll'], {
            relativeTo: activatedRoute.parent,
            queryParams: {
                sort: 'semester,desc',
                search: '',
            },
        });
    });

    it('should initialize and handle navigation on ngOnInit', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2] })));

        component.ngOnInit();

        expect(findAllForRegistrationStub).toHaveBeenCalledOnce();
        expect(component.predicate).toBe('title');
        expect(component.ascending).toBe(true);
    });

    it('should handle navigation with sort parameter from query params', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2] })));
        queryParamMapSubject.next(convertToParamMap({ sort: 'semester,desc', search: 'test search' }));

        component.ngOnInit();

        expect(component.predicate).toBe('semester');
        expect(component.ascending).toBe(false);
        expect(component.searchTermString).toBe('test search');
    });

    it('should handle navigation with invalid sort parameter format', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1] })));
        // Sort param with only one part (missing direction)
        queryParamMapSubject.next(convertToParamMap({ sort: 'title' }));
        dataSubject.next({ defaultSort: 'title,asc' });

        component.ngOnInit();

        // Should still use default predicate and ascending since the sort param is invalid
        expect(component.predicate).toBe('title');
        expect(component.ascending).toBe(true);
    });

    it('should use default sort when sortParam is not a string', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1] })));
        // No sort param and no defaultSort in data - should fall back to defaults
        queryParamMapSubject.next(convertToParamMap({}));
        dataSubject.next({});

        component.ngOnInit();

        // Should use fallback 'title,asc'
        expect(component.predicate).toBe('title');
        expect(component.ascending).toBe(true);
    });

    it('should use defaultSort when predicate is "defaultSort"', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2, course3] })));

        component.predicate = 'defaultSort';
        component.loadRegistrableCourses();

        // Should sort by title in ascending order (default behavior)
        expect(component.coursesToSelect[0]).toEqual(course1);
    });

    it('should use defaultSort when predicate is undefined', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2, course3] })));

        component.predicate = undefined!;
        component.loadRegistrableCourses();

        // Should sort by title in ascending order (default behavior)
        expect(component.coursesToSelect[0]).toEqual(course1);
    });

    it('should show all courses when search term is empty', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2, course3] })));

        component.loadRegistrableCourses();
        component.searchTermString = '';
        component.applySearch();

        expect(component.filteredCoursesToSelect).toHaveLength(3);
    });

    it('should handle courses without title in search', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2, course3] })));

        component.loadRegistrableCourses();
        component.searchTermString = 'Course';
        component.applySearch();

        // course3 has no title, so should not be included
        expect(component.filteredCoursesToSelect).toHaveLength(2);
        expect(component.filteredCoursesToSelect).toContain(course1);
        expect(component.filteredCoursesToSelect).toContain(course2);
    });

    it('should set loading to true at start and false after loading courses', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1] })));

        expect(component.loading).toBe(false);
        component.loadRegistrableCourses();
        // After synchronous observable completes, loading should be false
        expect(component.loading).toBe(false);
    });

    it('should apply case-insensitive search', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2] })));

        component.loadRegistrableCourses();
        component.searchTermString = 'course a';
        component.applySearch();

        expect(component.filteredCoursesToSelect).toHaveLength(1);
        expect(component.filteredCoursesToSelect[0]).toEqual(course1);
    });
});
