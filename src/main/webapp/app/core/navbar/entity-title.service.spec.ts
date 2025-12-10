import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import * as Sentry from '@sentry/angular';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
// Preliminary mock before import to prevent errors
jest.mock('@sentry/angular', () => {
    const originalModule = jest.requireActual('@sentry/angular');
    return Object.assign({}, originalModule, { captureException: jest.fn() });
});

describe('EntityTitleService', () => {
    let service: EntityTitleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        });
        service = TestBed.inject(EntityTitleService);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('returns a title that was set immediately', () => {
        service.setTitle(EntityType.COURSE, [1], 'Test Course');

        let result: string | undefined = undefined;
        service.getTitle(EntityType.COURSE, [1]).subscribe((title) => (result = title));

        expect(result).toBe('Test Course');
    });

    it.each([
        { type: EntityType.EXERCISE, ids: [1], url: 'exercise/exercises/1' },
        { type: EntityType.LECTURE, ids: [1], url: 'lecture/lectures/1' },
        { type: EntityType.COURSE, ids: [1], url: 'core/courses/1' },
        { type: EntityType.DIAGRAM, ids: [1], url: 'modeling/apollon-diagrams/1' },
        { type: EntityType.EXAM, ids: [1], url: 'exam/exams/1' },
        { type: EntityType.ORGANIZATION, ids: [1], url: 'core/organizations/1' },
    ])(
        'fires a request to fetch the title after 3 seconds',
        fakeAsync(({ type, ids, url }: { type: EntityType; ids: number[]; url: string }) => {
            const http = TestBed.inject(HttpClient);
            const httpSpy = jest.spyOn(http, 'get').mockReturnValue(of(new HttpResponse<string>({ body: 'Test Entity' })));

            let result: string | undefined = undefined;
            service.getTitle(type, ids).subscribe((title) => (result = title));
            tick(500);
            expect(result).toBeUndefined();
            expect(httpSpy).not.toHaveBeenCalled();
            tick(2500);
            expect(result).toBe('Test Entity');
            expect(httpSpy).toHaveBeenCalledOnce();
            expect(httpSpy).toHaveBeenCalledWith(`api/${url}/title`, { observe: 'response', responseType: 'text' });
        }),
    );

    it('waits for a title to be set if not present and does not fire the request if it arrives within 3 seconds', fakeAsync(() => {
        const http = TestBed.inject(HttpClient);
        const httpSpy = jest.spyOn(http, 'get').mockReturnValue(of(new HttpResponse<string>({ body: 'Test Hint' })));

        let result: string | undefined = undefined;
        service.getTitle(EntityType.COURSE, [1]).subscribe((title) => (result = title));
        tick(500);
        expect(result).toBeUndefined();
        tick(500);
        service.setTitle(EntityType.COURSE, [1], 'Test Course');
        expect(result).toBe('Test Course');
        tick(3000);

        expect(httpSpy).not.toHaveBeenCalled();
    }));

    it.each([
        { type: EntityType.COURSE, ids: [] },
        { type: EntityType.COURSE, ids: [undefined] },
        { type: undefined, ids: [undefined] },
        { type: undefined, ids: [] },
    ])('captures an exception if invalid parameters are supplied to getTitle', ({ type, ids }) => {
        // Re-mock to get reference because direct import doesn't work here
        const captureSpy = jest.spyOn(Sentry, 'captureException').mockImplementation();

        let result: string | undefined = undefined;

        // @ts-ignore we want to test invalid params
        service.getTitle(type, ids).subscribe((title) => (result = title));
        expect(captureSpy).toHaveBeenCalledOnce();
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
        const captureSpy = jest.spyOn(Sentry, 'captureException').mockImplementation();

        // @ts-ignore we want to test invalid params
        service.setTitle(type, ids, title);
        expect(captureSpy).toHaveBeenCalledOnce();
    });

    it('sets the exercise group title for students during an exam', () => {
        const exercise = { id: 1, exerciseGroup: { title: 'Group Title' }, isAtLeastTutor: false } as Exercise;
        service.setExerciseTitle(exercise);

        let result: string | undefined = undefined;
        service.getTitle(EntityType.EXERCISE, [1]).subscribe((title) => (result = title));

        expect(result).toBe('Group Title');
    });

    it('sets the exercise title for tutors and more privileged users', () => {
        const exercise = { id: 1, exerciseGroup: { title: 'Group Title' }, isAtLeastTutor: true, title: 'Exercise Title' } as Exercise;
        service.setExerciseTitle(exercise);

        let result: string | undefined = undefined;
        service.getTitle(EntityType.EXERCISE, [1]).subscribe((title) => (result = title));

        expect(result).toBe('Exercise Title');
    });

    it('sets the exercise title for course exercises', () => {
        const exercise = { id: 1, isAtLeastTutor: false, title: 'Exercise Title' } as Exercise;
        service.setExerciseTitle(exercise);

        let result: string | undefined = undefined;
        service.getTitle(EntityType.EXERCISE, [1]).subscribe((title) => (result = title));

        expect(result).toBe('Exercise Title');
    });
});
