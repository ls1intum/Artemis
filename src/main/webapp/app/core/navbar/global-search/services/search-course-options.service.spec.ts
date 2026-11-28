import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject } from 'rxjs';
import { signal } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { CourseForSearchFilter } from 'app/openapi/model/course-for-search-filter';
import { SearchCourseOptionsService } from './search-course-options.service';
import { MenuCourse } from '../models/search-menu.util';

describe('SearchCourseOptionsService', () => {
    const URL = '/api/search/course-options';
    const course: CourseForSearchFilter = { id: 7, title: 'Databases', active: true };

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

        const response: CourseForSearchFilter[] = [
            { id: 7, title: 'Databases', active: true },
            { id: 3, title: 'Compilers', active: false },
        ];
        const request = httpMock.expectOne(URL);
        expect(request.request.method).toBe('GET');
        expect(request.request.params.keys()).toEqual([]);
        expect(request.request.body).toBeNull();
        expect(request.request.responseType).toBe('json');
        request.flush(response);

        expect(received).toEqual([
            [
                { id: 7, title: 'Databases', active: true },
                { id: 3, title: 'Compilers', active: false },
            ],
        ]);
    });

    it('issues one request however many callers ask, including while it is still in flight', () => {
        const first: MenuCourse[][] = [];
        const second: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => first.push(courses));
        service.getCourses().subscribe((courses) => second.push(courses));

        // One request for both subscribers: the second joined the one already open rather than starting another.
        httpMock.expectOne(URL).flush([course]);

        expect(first).toEqual([[course]]);
        expect(second).toEqual([[course]]);

        const third: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => third.push(courses));

        httpMock.expectNone(URL);
        expect(third).toEqual([[course]]);
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

        httpMock.expectOne(URL).flush([course]);
        expect(retried).toEqual([[course]]);

        const cached: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => cached.push(courses));
        httpMock.expectNone(URL);
        expect(cached).toEqual([[course]]);
    });

    it('drops the cached list when a different user signs in', () => {
        // The list belongs to the user it was read for: signing out and in without reloading would otherwise
        // leave the next user filtering by courses they cannot open.
        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush([course]);

        authenticationState.next({ id: 2 } as User);

        const afterSwitch: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => afterSwitch.push(courses));
        const otherCourse: CourseForSearchFilter = { id: 9, title: "B's course", active: false };
        httpMock.expectOne(URL).flush([otherCourse]);
        expect(afterSwitch).toEqual([[otherCourse]]);
    });

    it('keeps the cache when the same user is re-emitted', () => {
        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush([course]);

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

    it('does not request courses until the first subscription', () => {
        httpMock.expectNone(URL);
        const courses = service.getCourses();
        httpMock.expectNone(URL);

        courses.subscribe();
        httpMock.expectOne(URL).flush([course]);
    });

    it('keeps an in-flight request when all current subscribers unsubscribe', () => {
        const first = service.getCourses().subscribe();
        const request = httpMock.expectOne(URL);
        first.unsubscribe();

        expect(request.cancelled).toBe(false);
        request.flush([course]);

        const received: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => received.push(courses));
        httpMock.expectNone(URL);
        expect(received).toEqual([[course]]);
    });

    it('caches a successful empty response', () => {
        const first: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => first.push(courses));
        httpMock.expectOne(URL).flush([]);

        const second: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => second.push(courses));
        httpMock.expectNone(URL);
        expect(first).toEqual([[]]);
        expect(second).toEqual([[]]);
    });

    it('preserves an omitted title and a false active flag', () => {
        const untitledCourse: CourseForSearchFilter = { id: 3, active: false };
        const received: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => received.push(courses));
        httpMock.expectOne(URL).flush([untitledCourse]);

        expect(received).toEqual([[{ id: 3, active: false }]]);
    });

    it('shares an error fallback with concurrent subscribers without automatically retrying', () => {
        const first: MenuCourse[][] = [];
        const second: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => first.push(courses));
        service.getCourses().subscribe((courses) => second.push(courses));
        httpMock.expectOne(URL).flush('nope', { status: 500, statusText: 'Server Error' });

        expect(first).toEqual([[]]);
        expect(second).toEqual([[]]);
        httpMock.expectNone(URL);

        const retried: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => retried.push(courses));
        httpMock.expectOne(URL).flush([course]);
        expect(retried).toEqual([[course]]);
    });

    it('invalidates the cache and generation on sign-out and sign-in', () => {
        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush([course]);
        const initial = service.generation();

        authenticationState.next(undefined);
        expect(service.generation()).toBe(initial + 1);
        authenticationState.next(undefined);
        expect(service.generation()).toBe(initial + 1);
        httpMock.expectNone(URL);

        authenticationState.next({ id: 1 } as User);
        expect(service.generation()).toBe(initial + 2);
        httpMock.expectNone(URL);

        service.getCourses().subscribe();
        httpMock.expectOne(URL).flush([course]);
    });

    it('does not replay an old user response to new subscribers after switching accounts', () => {
        service.getCourses().subscribe();
        const oldRequest = httpMock.expectOne(URL);
        authenticationState.next({ id: 2 } as User);

        const received: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => received.push(courses));
        const newRequest = httpMock.expectOne(URL);
        oldRequest.flush([course]);
        expect(received).toEqual([]);

        const otherCourse: CourseForSearchFilter = { id: 9, title: "B's course", active: true };
        newRequest.flush([otherCourse]);
        service.getCourses().subscribe((courses) => received.push(courses));
        httpMock.expectNone(URL);
        expect(received).toEqual([[otherCourse], [otherCourse]]);
    });

    it.each(['in flight', 'completed'])('does not let an old user error evict a %s request for the current user', (state) => {
        const oldReceived: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => oldReceived.push(courses));
        const oldRequest = httpMock.expectOne(URL);
        authenticationState.next({ id: 2 } as User);

        const otherCourse: CourseForSearchFilter = { id: 9, title: "B's course", active: true };
        const first: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => first.push(courses));
        const newRequest = httpMock.expectOne(URL);
        if (state === 'completed') {
            newRequest.flush([otherCourse]);
        }

        oldRequest.flush('nope', { status: 500, statusText: 'Server Error' });
        expect(oldReceived).toEqual([[]]);

        const second: MenuCourse[][] = [];
        service.getCourses().subscribe((courses) => second.push(courses));
        httpMock.expectNone(URL);
        if (state === 'in flight') {
            newRequest.flush([otherCourse]);
        }
        expect(first).toEqual([[otherCourse]]);
        expect(second).toEqual([[otherCourse]]);
    });
});
