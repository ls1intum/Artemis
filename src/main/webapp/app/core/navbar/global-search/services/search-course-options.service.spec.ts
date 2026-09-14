import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SearchCourseOptionsService } from './search-course-options.service';
import { MenuCourse } from '../models/search-menu.util';

describe('SearchCourseOptionsService', () => {
    const URL = 'api/course/courses/for-dropdown';

    let service: SearchCourseOptionsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(SearchCourseOptionsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('reads the courses the user can filter by', () => {
        const received: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => received.push(courses));

        httpMock.expectOne(URL).flush([{ id: 7, title: 'Databases' }]);

        expect(received).toEqual([[{ id: 7, title: 'Databases' }]]);
    });

    it('issues one request however many callers ask, including while it is still in flight', () => {
        const first: MenuCourse[][] = [];
        const second: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => first.push(courses));
        service.getCourses().subscribe((courses) => second.push(courses));

        // One request for both subscribers: the second joined the one already open rather than starting another.
        httpMock.expectOne(URL).flush([{ id: 7, title: 'Databases' }]);

        expect(first).toEqual([[{ id: 7, title: 'Databases' }]]);
        expect(second).toEqual([[{ id: 7, title: 'Databases' }]]);

        const third: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => third.push(courses));

        httpMock.expectNone(URL);
        expect(third).toEqual([[{ id: 7, title: 'Databases' }]]);
    });

    it('degrades a failed read to an empty list rather than breaking the menu', () => {
        const received: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => received.push(courses));

        httpMock.expectOne(URL).flush('nope', { status: 500, statusText: 'Server Error' });

        expect(received).toEqual([[]]);
    });

    it('retries after a failure instead of replaying it for the rest of the session', () => {
        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush('nope', { status: 500, statusText: 'Server Error' });

        const retried: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => retried.push(courses));

        httpMock.expectOne(URL).flush([{ id: 7, title: 'Databases' }]);
        expect(retried).toEqual([[{ id: 7, title: 'Databases' }]]);
    });
});
