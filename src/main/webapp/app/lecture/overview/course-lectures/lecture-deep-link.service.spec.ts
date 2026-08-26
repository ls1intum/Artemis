import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LectureDeepLink } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';
import { LectureDeepLinkService } from 'app/lecture/overview/course-lectures/lecture-deep-link.service';

describe('LectureDeepLinkService', () => {
    let service: LectureDeepLinkService;
    let router: Router;

    const LECTURE_ROUTE = '/courses/1/lectures/1';

    /** Pretends the router has arrived at the given URL, which is what `isActive` reads the current one from. */
    const pretendCurrentUrl = (url: string) => Object.defineProperty(router, 'lastSuccessfulNavigation', { value: () => ({ finalUrl: router.parseUrl(url) }), configurable: true });

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideRouter([])] });
        service = TestBed.inject(LectureDeepLinkService);
        router = TestBed.inject(Router);
    });

    it.each([
        { name: 'carries the deep link in the query parameters', route: LECTURE_ROUTE, link: { unitId: 7, timestamp: 30, page: 4 }, expected: { unit: 7, timestamp: 30, page: 4 } },
        { name: 'just opens the lecture when no place inside it is named', route: LECTURE_ROUTE, link: undefined, expected: {} },
        { name: 'accepts a route given as segments', route: ['/courses', 1, 'lectures', 1], link: { unitId: 7 }, expected: { unit: 7 } },
    ])('should navigate when the lecture is not on screen and $name', ({ route, link, expected }) => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl('/courses/1/dashboard');

        service.jump(route, link);

        expect(navigate).toHaveBeenCalledWith(typeof route === 'string' ? [route] : route, { queryParams: expected });
    });

    it.each([
        { name: 'that lecture is already on screen', current: LECTURE_ROUTE },
        // The query parameters are the jump itself, so they must not count towards judging the page.
        { name: 'it is on screen carrying an earlier jump', current: `${LECTURE_ROUTE}?unit=9&page=2` },
    ])('should hand the jump over without navigating when $name', ({ current }) => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl(current);
        const received: LectureDeepLink[] = [];
        service.requests.subscribe((request) => received.push(request));

        service.jump(LECTURE_ROUTE, { unitId: 7, page: 4 });

        // Navigating would push a history entry onto an identical one, costing a Back press that does nothing.
        expect(navigate).not.toHaveBeenCalled();
        expect(received).toEqual([{ unitId: 7, page: 4 }]);
    });

    it('should hand over every jump, so asking for the same place twice jumps twice', () => {
        pretendCurrentUrl(LECTURE_ROUTE);
        const received: LectureDeepLink[] = [];
        service.requests.subscribe((request) => received.push(request));

        service.jump(LECTURE_ROUTE, { unitId: 7, page: 4 });
        service.jump(LECTURE_ROUTE, { unitId: 7, page: 4 });

        expect(received).toHaveLength(2);
    });

    it('should not replay a jump to a page that subscribes afterwards', () => {
        pretendCurrentUrl(LECTURE_ROUTE);
        service.jump(LECTURE_ROUTE, { unitId: 7, page: 4 });

        const received: LectureDeepLink[] = [];
        service.requests.subscribe((request) => received.push(request));

        // A jump is a command: a lecture page opened later must not execute one that is long done.
        expect(received).toHaveLength(0);
    });
});
