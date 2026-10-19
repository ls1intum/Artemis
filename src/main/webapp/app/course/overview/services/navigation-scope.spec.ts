import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AlertService } from 'app/foundation/service/alert.service';

/**
 * Course tab data is held for exactly one router navigation: everything one tab selection needs shares a response, and
 * the next selection — including re-selecting the tab you are on, which is how a student refreshes — asks the server.
 * CourseAvailableTabsService stands in for all three tab data services, which scope their state the same way.
 */
describe('Navigation-scoped course tab data', () => {
    let service: CourseAvailableTabsService;
    let router: MockRouter;
    let fetchSpy: ReturnType<typeof vi.spyOn>;

    const tabs: CourseAvailableTabs = {
        lectures: true,
        exams: false,
        competencies: false,
        tutorialGroups: false,
        iris: false,
        faq: false,
        learningPaths: false,
        communication: false,
        training: false,
    };

    /** Puts the router in the middle of a navigation, as it is while a route guard runs. */
    function during(navigationId: number): void {
        router.currentNavigation.mockReturnValue({ id: navigationId });
    }

    /** Puts the router just after a navigation, as it is when a component loads shortly after activation. */
    function after(navigationId: number): void {
        router.currentNavigation.mockReturnValue(null);
        router.lastSuccessfulNavigation.mockReturnValue({ id: navigationId });
    }

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, { provide: Router, useValue: router }, provideHttpClient(), MockProvider(AlertService)],
        });
        service = TestBed.inject(CourseAvailableTabsService);
        service.clear();
        fetchSpy = vi.spyOn(TestBed.inject(CourseManagementService), 'getCourseAvailableTabs').mockReturnValue(of(tabs));
    });

    it('should share one response between the guard and the sidebar of the same tab selection', () => {
        during(4);
        service.loadIfNeeded(1).subscribe();
        // The container is created during activation, so it sees the same navigation as the guard that preceded it
        after(4);
        service.loadIfNeeded(1).subscribe();

        expect(fetchSpy).toHaveBeenCalledOnce();
    });

    it('should ask again on the next tab selection, so a newly published lecture shows up without a page reload', () => {
        during(4);
        service.loadIfNeeded(1).subscribe();
        after(4);
        during(5);
        service.loadIfNeeded(1).subscribe();

        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });

    it('should ask again when the user re-selects the tab they are already on', () => {
        after(4);
        service.loadIfNeeded(1).subscribe();
        // onSameUrlNavigation: 'reload' gives the identical URL a new navigation id, which is what makes this a refresh
        after(5);
        service.loadIfNeeded(1).subscribe();

        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });

    it('should ask again for a different course within the same navigation', () => {
        during(4);
        service.loadIfNeeded(1).subscribe();
        service.loadIfNeeded(2).subscribe();

        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });
});
