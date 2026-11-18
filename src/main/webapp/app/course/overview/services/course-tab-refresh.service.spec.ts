import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';

describe('CourseTabRefreshService', () => {
    let service: CourseTabRefreshService;

    /** A route whose own URL is /courses/1/lectures, regardless of which child is open. */
    const lecturesRoute = {
        pathFromRoot: [{ snapshot: { url: [] } }, { snapshot: { url: [{ path: 'courses' }, { path: '1' }] } }, { snapshot: { url: [{ path: 'lectures' }] } }],
    } as unknown as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CourseTabRefreshService);
    });

    it('should emit when the user selects this tab', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        service.notifyTabSelected('/courses/1/lectures');

        expect(refreshed).toHaveBeenCalledOnce();
    });

    it('should emit for a tab showing a child, so a selected lecture does not block the refresh', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        // The user is on /courses/1/lectures/7; the sidebar link is still the tab URL
        service.notifyTabSelected('/courses/1/lectures');

        expect(refreshed).toHaveBeenCalledOnce();
    });

    it('should not emit for a different tab', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        service.notifyTabSelected('/courses/1/exercises');

        expect(refreshed).not.toHaveBeenCalled();
    });

    it('should not emit for anything the router does on its own', () => {
        // The whole point of reporting the click: a tab navigating to its own URL while rendering must not look like a
        // user selecting it, which previously refreshed the tab in an unbounded loop
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        expect(refreshed).not.toHaveBeenCalled();
    });

    it('should match the relative links the sidebar actually holds', () => {
        // CourseSidebarItemService produces `communication` for the student sidebar and `{courseId}/lectures` for the
        // management one, never the absolute path the route knows
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        service.notifyTabSelected('lectures');
        service.notifyTabSelected('1/lectures');

        expect(refreshed).toHaveBeenCalledTimes(2);
    });

    it('should compare links that differ only by a trailing slash or query string as the same tab', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        service.notifyTabSelected('/courses/1/lectures/');
        service.notifyTabSelected('/courses/1/lectures?filter=upcoming');

        expect(refreshed).toHaveBeenCalledTimes(2);
    });
});
