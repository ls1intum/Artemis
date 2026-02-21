import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot } from '@angular/router';
import { CourseManagementResolve } from 'app/core/course/manage/services/course-management-resolve.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';

describe('CourseManagementResolve', () => {
    setupTestBed({ zoneless: true });

    let resolver: CourseManagementResolve;
    let service: CourseManagementService;
    let route: ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseManagementResolve, { provide: CourseManagementService, useValue: { find: vi.fn() } }],
        });
        resolver = TestBed.inject(CourseManagementResolve);
        service = TestBed.inject(CourseManagementService);
        route = new ActivatedRouteSnapshot();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return course when courseId param is present', () => {
        const mockCourse = new Course();
        mockCourse.id = 42;
        mockCourse.title = 'Test Course';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: mockCourse, status: 200, statusText: 'OK' })));

        route.params = { courseId: 42 };
        let result: Course | undefined;

        resolver.resolve(route).subscribe((res) => (result = res));

        expect(service.find).toHaveBeenCalledWith(42);
        expect(result).toBe(mockCourse);
    });

    it('should return new Course when no courseId param is provided', () => {
        route.params = {};
        let result: Course | undefined;

        resolver.resolve(route).subscribe((res) => (result = res));

        expect(result).toBeInstanceOf(Course);
        expect(service.find).not.toHaveBeenCalled();
    });

    it('should filter out non-ok responses', () => {
        const mockCourse = new Course();
        mockCourse.id = 42;
        // Create a response with ok: false (status not in 200-299 range)
        const nonOkResponse = new HttpResponse({ body: mockCourse, status: 404, statusText: 'Not Found' });
        vi.spyOn(service, 'find').mockReturnValue(of(nonOkResponse));

        route.params = { courseId: 42 };
        let result: Course | undefined;

        resolver.resolve(route).subscribe({
            next: (res) => (result = res),
        });

        expect(service.find).toHaveBeenCalledWith(42);
        // Since filter filters out non-ok responses, the observable should complete without emitting
        expect(result).toBeUndefined();
    });

    it('should handle course with all properties', () => {
        const mockCourse = new Course();
        mockCourse.id = 123;
        mockCourse.title = 'Full Course';
        mockCourse.shortName = 'FC';
        mockCourse.studentGroupName = 'fc-students';
        mockCourse.teachingAssistantGroupName = 'fc-tutors';
        mockCourse.editorGroupName = 'fc-editors';
        mockCourse.instructorGroupName = 'fc-instructors';

        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: mockCourse, status: 200, statusText: 'OK' })));

        route.params = { courseId: 123 };
        let result: Course | undefined;

        resolver.resolve(route).subscribe((res) => (result = res));

        expect(service.find).toHaveBeenCalledWith(123);
        expect(result).toBe(mockCourse);
        expect(result?.id).toBe(123);
        expect(result?.title).toBe('Full Course');
        expect(result?.shortName).toBe('FC');
    });

    it('should handle numeric string courseId', () => {
        const mockCourse = new Course();
        mockCourse.id = 99;
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: mockCourse, status: 200, statusText: 'OK' })));

        // Route params are typically strings in Angular
        route.params = { courseId: '99' };
        let result: Course | undefined;

        resolver.resolve(route).subscribe((res) => (result = res));

        expect(service.find).toHaveBeenCalledWith('99');
        expect(result).toBe(mockCourse);
    });

    it('should return new Course with default values when no courseId', () => {
        route.params = {};
        let result: Course | undefined;

        resolver.resolve(route).subscribe((res) => (result = res));

        expect(result).toBeInstanceOf(Course);
        expect(result?.onlineCourse).toBe(false);
        expect(result?.isAtLeastTutor).toBe(false);
        expect(result?.isAtLeastEditor).toBe(false);
        expect(result?.isAtLeastInstructor).toBe(false);
        expect(result?.enrollmentEnabled).toBe(false);
        expect(result?.complaintsEnabled).toBe(true);
        expect(result?.requestMoreFeedbackEnabled).toBe(true);
    });
});
