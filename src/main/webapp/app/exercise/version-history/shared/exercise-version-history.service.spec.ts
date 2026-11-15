import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { ExerciseVersionPage } from 'app/exercise/version-history/shared/exercise-version-history.model';
import { ExerciseVersionHistoryService } from 'app/exercise/version-history/shared/exercise-version-history.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('ExerciseVersionHistoryService', () => {
    let service: ExerciseVersionHistoryService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(ExerciseVersionHistoryService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should fetch versions and parse pagination headers', () => {
        let result: ExerciseVersionPage | undefined;
        service.getVersions(42, 0, 20).subscribe((response) => (result = response));

        const req = httpMock.expectOne(({ urlWithParams }) => urlWithParams.split('?')[0] === '/api/exercise/exercises/42/versions');
        expect(req.request.method).toBe('GET');
        const query = new URL(req.request.urlWithParams, 'https://artemis.example').searchParams;
        expect(query.size).toBe(2);
        expect(query.get('page')).toBe('0');
        expect(query.get('size')).toBe('20');

        const author = { login: 'editor1', name: 'Editor One', imageUrl: '/public/avatar.png' };
        const versionsUrl = 'http://localhost/api/exercise/exercises/42/versions';
        req.flush([{ id: 3, author, createdDate: '2026-03-04T11:00:00+02:00' }, { id: 2 }], {
            headers: {
                Link: `<${versionsUrl}?page=1&size=20>; rel="next",<${versionsUrl}?page=2&size=20>; rel="last",<${versionsUrl}?page=0&size=20>; rel="first"`,
                'X-Total-Count': '50',
            },
        });

        expect(result?.versions).toHaveLength(2);
        expect(result?.versions[0].id).toBe(3);
        expect(result?.versions[0].author).toEqual(author);
        expect(result?.versions[0].createdDate?.toISOString()).toBe('2026-03-04T09:00:00.000Z');
        expect(result?.versions[1]).toEqual({ id: 2, author: undefined, createdDate: undefined });
        expect(result?.nextPage).toBe(1);
        expect(result?.totalItems).toBe(50);
    });

    it.each([undefined, 'invalid link', '<http://localhost/api/exercise/exercises/42/versions?page=0&size=20>; rel="last"'])(
        'should handle an empty page with Link header %s',
        (link) => {
            let result: ExerciseVersionPage | undefined;
            service.getVersions(42, 0, 20).subscribe((response) => (result = response));

            httpMock.expectOne('/api/exercise/exercises/42/versions?page=0&size=20').flush([], { headers: link ? { Link: link } : {} });

            expect(result).toEqual({ versions: [], nextPage: undefined, totalItems: 0 });
        },
    );

    it('should fetch a snapshot by version id and preserve omitted fields', () => {
        let result: ExerciseSnapshotDTO | undefined;
        service.getSnapshot(42, 7).subscribe((response) => (result = response));

        const snapshot = { id: 42, title: 'Snapshot title', assessmentType: AssessmentType.AUTOMATIC, competencyLinks: [{ competencyId: {}, weight: 0 }] };
        httpMock.expectOne({ method: 'GET', url: '/api/exercise/exercises/42/versions/7' }).flush(snapshot);

        expect(result).toStrictEqual(snapshot);
        expect(result).not.toHaveProperty('programmingData');
    });

    it('should preserve historical programming languages and falsy values', () => {
        let result: ExerciseSnapshotDTO | undefined;
        service.getSnapshot(42, 7).subscribe((response) => (result = response));

        const programmingData = { programmingLanguage: 'OCAML', allowOnlineEditor: false, buildConfig: { timeoutSeconds: 0 } };
        httpMock.expectOne('/api/exercise/exercises/42/versions/7').flush({ id: 42, programmingData });

        expect(result?.programmingData).toStrictEqual(programmingData);
    });
});
