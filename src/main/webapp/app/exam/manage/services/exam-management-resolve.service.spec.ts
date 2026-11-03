import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { CourseResolve } from './exam-management-resolve.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';

describe('Exam management course resolver', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [MockProvider(CourseManagementService)] }));
    afterEach(() => vi.restoreAllMocks());

    it('provides the management course projection to the exam route', async () => {
        const course = { id: 42, title: 'Management course', isAtLeastTutor: true };
        const load = vi.spyOn(TestBed.inject(CourseManagementService), 'findForManagement').mockReturnValue(of(new HttpResponse({ body: course })));
        const route = new ActivatedRouteSnapshot();
        route.params = { courseId: 42 };
        expect(await firstValueFrom(TestBed.inject(CourseResolve).resolve(route))).toEqual(course);
        expect(load).toHaveBeenCalledExactlyOnceWith(42);
    });

    it('resolves a failed management request as an absent course', async () => {
        vi.spyOn(TestBed.inject(CourseManagementService), 'findForManagement').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        const route = new ActivatedRouteSnapshot();
        route.params = { courseId: 42 };
        expect(await firstValueFrom(TestBed.inject(CourseResolve).resolve(route))).toBeNull();
    });

    it('does not request a course when the route has no course id', async () => {
        const load = vi.spyOn(TestBed.inject(CourseManagementService), 'findForManagement');
        const route = new ActivatedRouteSnapshot();
        route.params = {};
        expect(await firstValueFrom(TestBed.inject(CourseResolve).resolve(route))).toBeNull();
        expect(load).not.toHaveBeenCalled();
    });
});
