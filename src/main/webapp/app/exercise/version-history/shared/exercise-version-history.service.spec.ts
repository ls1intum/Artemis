import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ExerciseVersionHistoryService } from 'app/exercise/version-history/shared/exercise-version-history.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExerciseVersionHistoryService', () => {
    setupTestBed({ zoneless: true });

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
        let result: any;
        service.getVersions(42, 0, 20).subscribe((response) => (result = response));

        const req = httpMock.expectOne('api/exercise/42/versions?page=0&size=20');
        expect(req.request.method).toBe('GET');
        req.flush([{ id: 3, author: { login: 'editor1', name: 'Editor One' }, createdDate: '2026-03-04T11:00:00Z' }], {
            headers: {
                link: '<http://localhost/api/exercise/42/versions?page=1&size=20>; rel="next"',
                'X-Total-Count': '50',
            },
        });

        expect(result.versions).toHaveLength(1);
        expect(result.versions[0].id).toBe(3);
        expect(result.versions[0].createdDate?.isValid()).toBeTruthy();
        expect(result.nextPage).toBe(1);
        expect(result.totalItems).toBe(50);
    });

    it('should fetch a snapshot by version id', () => {
        let result: any;
        service.getSnapshot(42, 7).subscribe((response) => (result = response));

        const req = httpMock.expectOne('api/exercise/42/version/7');
        expect(req.request.method).toBe('GET');
        req.flush({ id: 42, title: 'Snapshot title' });

        expect(result.id).toBe(42);
        expect(result.title).toBe('Snapshot title');
    });
});
