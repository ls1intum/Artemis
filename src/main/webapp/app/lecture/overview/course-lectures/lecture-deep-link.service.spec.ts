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

    it('should navigate with the deep link in the query parameters when the lecture is not the page on screen', () => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl('/courses/1/dashboard');

        service.jump(LECTURE_ROUTE, { unitId: 7, timestamp: 30, page: 4 });

        expect(navigate).toHaveBeenCalledWith([LECTURE_ROUTE], { queryParams: { unit: 7, timestamp: 30, page: 4 } });
    });

    it('should hand the jump over without navigating when that lecture is already on screen', () => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl(LECTURE_ROUTE);
        const received: LectureDeepLink[] = [];
        service.requests.subscribe((request) => received.push(request));

        service.jump(LECTURE_ROUTE, { unitId: 7, page: 4 });

        // Navigating here would push a history entry onto an identical one, costing the student a Back press that
        // visibly does nothing.
        expect(navigate).not.toHaveBeenCalled();
        expect(received).toEqual([{ unitId: 7, page: 4 }]);
    });

    it('should judge the page without the query parameters, which are the jump itself', () => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl(`${LECTURE_ROUTE}?unit=9&page=2`);

        service.jump(LECTURE_ROUTE, { unitId: 7, page: 4 });

        expect(navigate).not.toHaveBeenCalled();
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

    it('should open the lecture when the target names no place inside it', () => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl('/courses/1/dashboard');

        service.jump(LECTURE_ROUTE, undefined);

        expect(navigate).toHaveBeenCalledWith([LECTURE_ROUTE], { queryParams: {} });
    });

    it('should accept a route given as segments', () => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        pretendCurrentUrl('/courses/1/dashboard');

        service.jump(['/courses', 1, 'lectures', 1], { unitId: 7 });

        expect(navigate).toHaveBeenCalledWith(['/courses', 1, 'lectures', 1], { queryParams: { unit: 7 } });
    });
});
