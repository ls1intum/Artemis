import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import 'app/shared/util/map.extension';

vi.mock('@sentry/angular', async (importOriginal) => {
    const originalModule = await importOriginal<typeof import('@sentry/angular')>();
    return {
        ...originalModule,
        captureException: vi.fn(),
    };
});

import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom, of } from 'rxjs';
import * as Sentry from '@sentry/angular';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';

describe('EntityTitleService', () => {
    setupTestBed({ zoneless: true });

    let service: EntityTitleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                // Override the service to get a fresh instance for each test
                { provide: EntityTitleService, useClass: EntityTitleService },
            ],
        });
        service = TestBed.inject(EntityTitleService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('returns a title that was set immediately', async () => {
        service.setTitle(EntityType.COURSE, [1], 'Test Course');

        const result = await firstValueFrom(service.getTitle(EntityType.COURSE, [1]));

        expect(result).toBe('Test Course');
    });

    it.each([
        { type: EntityType.EXERCISE, ids: [1], url: 'exercise/exercises/1' },
        { type: EntityType.LECTURE, ids: [1], url: 'lecture/lectures/1' },
        { type: EntityType.COURSE, ids: [1], url: 'core/courses/1' },
        { type: EntityType.DIAGRAM, ids: [1], url: 'modeling/apollon-diagrams/1' },
        { type: EntityType.EXAM, ids: [1], url: 'exam/exams/1' },
        { type: EntityType.ORGANIZATION, ids: [1], url: 'core/organizations/1' },
    ])('fires a request to fetch the title after 3 seconds', async ({ type, ids, url }: { type: EntityType; ids: number[]; url: string }) => {
        // Use real timers with actual delay
        const http = TestBed.inject(HttpClient);
        const httpSpy = vi.spyOn(http, 'get').mockReturnValue(of(new HttpResponse<string>({ body: 'Test Entity' })));

        let result: string | undefined = undefined;
        const subscription = service.getTitle(type, ids).subscribe((title) => (result = title));

        // Check that nothing happens initially
        expect(result).toBeUndefined();
        expect(httpSpy).not.toHaveBeenCalled();

        // Wait for the 3 second fallback timeout plus a small buffer
        await new Promise((resolve) => setTimeout(resolve, 3100));

        expect(httpSpy).toHaveBeenCalledOnce();
        expect(httpSpy).toHaveBeenCalledWith(`api/${url}/title`, { observe: 'response', responseType: 'text' });
        expect(result).toBe('Test Entity');

        subscription.unsubscribe();
    });

    it('waits for a title to be set if not present and does not fire the request if it arrives within 3 seconds', async () => {
        const http = TestBed.inject(HttpClient);
        const httpSpy = vi.spyOn(http, 'get').mockReturnValue(of(new HttpResponse<string>({ body: 'Test Hint' })));

        let result: string | undefined = undefined;
        const subscription = service.getTitle(EntityType.COURSE, [1]).subscribe((title) => (result = title));

        // Initially no result
        expect(result).toBeUndefined();

        // Wait a bit but less than 3 seconds
        await new Promise((resolve) => setTimeout(resolve, 500));
        expect(result).toBeUndefined();

        // Set title before the 3 second timeout
        service.setTitle(EntityType.COURSE, [1], 'Test Course');

        // The result should be set immediately after setTitle
        expect(result).toBe('Test Course');

        // Wait past 3 seconds total
        await new Promise((resolve) => setTimeout(resolve, 3000));

        // HTTP should not have been called since we set the title before the timeout
        expect(httpSpy).not.toHaveBeenCalled();

        subscription.unsubscribe();
    });

    it.each([
        { type: EntityType.COURSE, ids: [] },
        { type: EntityType.COURSE, ids: [undefined] },
        { type: undefined, ids: [undefined] },
        { type: undefined, ids: [] },
    ])('captures an exception if invalid parameters are supplied to getTitle', async ({ type, ids }) => {
        // Re-mock to get reference because direct import doesn't work here
        const captureSpy = vi.spyOn(Sentry, 'captureException').mockImplementation(() => '');
        captureSpy.mockClear();

        // @ts-ignore we want to test invalid params
        const observable = service.getTitle(type, ids);
        let result: string | undefined = undefined;
        observable.subscribe((title) => (result = title));

        // Since EMPTY observable completes immediately, the spy should have been called synchronously
        expect(captureSpy).toHaveBeenCalled();
        expect(result).toBeUndefined();
    });

    it.each([
        { type: EntityType.COURSE, ids: [], title: 'Test' },
        { type: EntityType.COURSE, ids: [undefined], title: 'Test' },
        { type: undefined, ids: [undefined], title: 'Test' },
        { type: undefined, ids: [], title: 'Test' },
        { type: EntityType.COURSE, ids: [], title: undefined },
        { type: EntityType.COURSE, ids: [undefined], title: undefined },
        { type: undefined, ids: [undefined], title: undefined },
        { type: undefined, ids: [], title: undefined },
    ])('captures an exception if invalid parameters are supplied to setTitle', ({ type, ids, title }) => {
        // Re-mock to get reference because direct import doesn't work here
        const captureSpy = vi.spyOn(Sentry, 'captureException').mockImplementation(() => '');
        captureSpy.mockClear();

        // @ts-ignore we want to test invalid params
        service.setTitle(type, ids, title);
        expect(captureSpy).toHaveBeenCalled();
    });

    it('sets the exercise group title for students during an exam', async () => {
        const exercise = { id: 1, exerciseGroup: { title: 'Group Title' }, isAtLeastTutor: false } as Exercise;
        service.setExerciseTitle(exercise);

        const result = await firstValueFrom(service.getTitle(EntityType.EXERCISE, [1]));

        expect(result).toBe('Group Title');
    });

    it('sets the exercise title for tutors and more privileged users', async () => {
        const exercise = { id: 1, exerciseGroup: { title: 'Group Title' }, isAtLeastTutor: true, title: 'Exercise Title' } as Exercise;
        service.setExerciseTitle(exercise);

        const result = await firstValueFrom(service.getTitle(EntityType.EXERCISE, [1]));

        expect(result).toBe('Exercise Title');
    });

    it('sets the exercise title for course exercises', async () => {
        const exercise = { id: 1, isAtLeastTutor: false, title: 'Exercise Title' } as Exercise;
        service.setExerciseTitle(exercise);

        const result = await firstValueFrom(service.getTitle(EntityType.EXERCISE, [1]));

        expect(result).toBe('Exercise Title');
    });
});
