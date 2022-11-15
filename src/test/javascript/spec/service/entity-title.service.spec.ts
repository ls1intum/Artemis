import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import * as Sentry from '@sentry/browser';

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
        { type: EntityType.HINT, ids: [3, 2], url: 'programming-exercises/2/exercise-hints/3' },
        { type: EntityType.EXERCISE, ids: [1], url: 'exercises/1' },
        { type: EntityType.LECTURE, ids: [1], url: 'lectures/1' },
        { type: EntityType.COURSE, ids: [1], url: 'courses/1' },
        { type: EntityType.DIAGRAM, ids: [1], url: 'apollon-diagrams/1' },
        { type: EntityType.EXAM, ids: [1], url: 'exams/1' },
        { type: EntityType.ORGANIZATION, ids: [1], url: 'organizations/1' },
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
            expect(httpSpy).toHaveBeenCalledWith(`${SERVER_API_URL}api/${url}/title`, { observe: 'response', responseType: 'text' });
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
        { type: EntityType.HINT, ids: [1, undefined] },
        { type: undefined, ids: [undefined] },
        { type: undefined, ids: [] },
    ])('captures an exception if invalid parameters are supplied to getTitle', ({ type, ids }) => {
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
        { type: EntityType.HINT, ids: [1, undefined], title: 'Test' },
        { type: undefined, ids: [undefined], title: 'Test' },
        { type: undefined, ids: [], title: 'Test' },
        { type: EntityType.COURSE, ids: [], title: undefined },
        { type: EntityType.COURSE, ids: [undefined], title: undefined },
        { type: EntityType.HINT, ids: [1, undefined], title: undefined },
        { type: undefined, ids: [undefined], title: undefined },
        { type: undefined, ids: [], title: undefined },
    ])('captures an exception if invalid parameters are supplied to setTitle', ({ type, ids, title }) => {
        const captureSpy = jest.spyOn(Sentry, 'captureException').mockImplementation();

        // @ts-ignore we want to test invalid params
        service.setTitle(type, ids, title);
        expect(captureSpy).toHaveBeenCalledOnce();
    });
});
