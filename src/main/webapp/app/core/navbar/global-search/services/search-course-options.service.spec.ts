import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject } from 'rxjs';
import { signal } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { SearchCourseOptionsService } from './search-course-options.service';
import { MenuCourse } from '../models/search-menu.util';

describe('SearchCourseOptionsService', () => {
    const URL = 'api/course/courses/for-dropdown';

    let service: SearchCourseOptionsService;
    let httpMock: HttpTestingController;
    let authenticationState: BehaviorSubject<User | undefined>;

    beforeEach(() => {
        authenticationState = new BehaviorSubject<User | undefined>({ id: 1 } as User);
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: AccountService,
                    useValue: { userIdentity: signal({ id: 1 } as User), getAuthenticationState: () => authenticationState.asObservable() },
                },
            ],
        });
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

    it('drops the cached list when a different user signs in', () => {
        // The list belongs to the user it was read for: signing out and in without reloading would otherwise
        // leave the next user filtering by courses they cannot open.
        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush([{ id: 7, title: "A's course" }]);

        authenticationState.next({ id: 2 } as User);

        const afterSwitch: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => afterSwitch.push(courses));
        httpMock.expectOne(URL).flush([{ id: 9, title: "B's course" }]);
        expect(afterSwitch).toEqual([[{ id: 9, title: "B's course" }]]);
    });

    it('keeps the cache when the same user is re-emitted', () => {
        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush([{ id: 7, title: 'Databases' }]);

        authenticationState.next({ id: 1 } as User);

        service.getCourses().subscribe();
        httpMock.expectNone(URL);
    });

    it('reports a new generation only when the user actually changes', () => {
        const initial = service.generation();
        authenticationState.next({ id: 1 } as User);
        expect(service.generation()).toBe(initial);

        authenticationState.next({ id: 2 } as User);
        expect(service.generation()).toBe(initial + 1);
    });
});
