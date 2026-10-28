import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';

const tabs = (overrides: Partial<CourseAvailableTabs> = {}): CourseAvailableTabs => ({
    lectures: false,
    exams: false,
    competencies: false,
    tutorialGroups: false,
    iris: false,
    faq: false,
    learningPaths: false,
    communication: false,
    training: false,
    ...overrides,
});

describe('CourseAvailableTabsService', () => {
    let service: CourseAvailableTabsService;
    let courseManagementService: CourseManagementService;
    let fetchSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, provideHttpClient(), provideHttpClientTesting(), MockProvider(AlertService)],
        });
        service = TestBed.inject(CourseAvailableTabsService);
        service.clear();
        courseManagementService = TestBed.inject(CourseManagementService);
        fetchSpy = vi.spyOn(courseManagementService, 'getCourseAvailableTabs').mockReturnValue(of(tabs({ lectures: true })));
    });

    afterEach(() => {
        TestBed.inject(HttpTestingController).verify();
        vi.restoreAllMocks();
    });

    it('should report no tabs before anything is loaded', () => {
        expect(service.tabsFor(1)).toBeUndefined();
    });

    it('should not report a hit for an undefined course id on empty state', () => {
        // Guarding only with optional chaining would compare undefined === undefined and wrongly report a hit
        expect(service.tabsFor(undefined as unknown as number)).toBeUndefined();
    });

    it('should fetch and hold the tabs of a course', () => {
        service.load(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(service.tabsFor(1)?.lectures).toBe(true);
    });

    it('should fetch only once per course when using loadIfNeeded', () => {
        service.loadIfNeeded(1).subscribe();
        service.loadIfNeeded(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledOnce();
    });

    it('shares one HTTP request between concurrent tab readers and delivers the response to both', () => {
        fetchSpy.mockRestore();
        const http = TestBed.inject(HttpTestingController);
        const first: CourseAvailableTabs[] = [];
        const second: CourseAvailableTabs[] = [];
        service.loadIfNeeded(589).subscribe((value) => first.push(value));
        service.loadIfNeeded(589).subscribe((value) => second.push(value));

        const requests = http.match('api/course/courses/589/available-tabs');
        expect(requests).toHaveLength(1);
        const response = tabs({ lectures: true, competencies: true });
        requests[0].flush(response);

        expect(first).toEqual([response]);
        expect(second).toEqual([response]);
        expect(service.tabsFor(589)).toEqual(response);
    });

    it('starts a fresh request after every reader cancels an unfinished load', () => {
        fetchSpy.mockRestore();
        const http = TestBed.inject(HttpTestingController);
        const subscription = service.loadIfNeeded(589).subscribe();
        const cancelled = http.expectOne('api/course/courses/589/available-tabs');
        subscription.unsubscribe();
        expect(cancelled.cancelled).toBe(true);

        const values: CourseAvailableTabs[] = [];
        service.loadIfNeeded(589).subscribe((value) => values.push(value));
        const response = tabs({ lectures: true });
        http.expectOne('api/course/courses/589/available-tabs').flush(response);
        expect(values).toEqual([response]);
    });

    it('does not retain a response from a request cleared before it completes', () => {
        fetchSpy.mockRestore();
        const http = TestBed.inject(HttpTestingController);
        service.loadIfNeeded(589).subscribe();
        const previous = http.expectOne('api/course/courses/589/available-tabs');
        service.clear();
        previous.flush(tabs({ lectures: true }));
        expect(service.tabsFor(589)).toBeUndefined();

        service.loadIfNeeded(589).subscribe();
        const response = tabs({ competencies: true });
        http.expectOne('api/course/courses/589/available-tabs').flush(response);
        expect(service.tabsFor(589)).toEqual(response);
    });

    it('should refetch for a different course and drop the previous one', () => {
        service.loadIfNeeded(1).subscribe();
        service.loadIfNeeded(2).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
        expect(service.tabsFor(1)).toBeUndefined();
        expect(service.tabsFor(2)).toBeDefined();
    });

    it('should always refetch on an explicit load, so a refresh picks up newly published content', () => {
        service.loadIfNeeded(1).subscribe();
        service.load(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });

    it('should drop the held tabs on clear so the next visit refetches', () => {
        service.loadIfNeeded(1).subscribe();
        service.clear();
        expect(service.tabsFor(1)).toBeUndefined();
        service.loadIfNeeded(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });
});
