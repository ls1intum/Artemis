import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseHomeService } from './course-home.service';

describe('CourseHomeService', () => {
    let service: CourseHomeService;
    let http: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(CourseHomeService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());
    it('preserves the original new-content baseline', () => {
        service.load(42, '2026-10-19T10:00:00Z').subscribe();
        const request = http.expectOne((r) => r.url === 'api/course/courses/42/home');
        expect(request.request.params.get('since')).toBe('2026-10-19T10:00:00Z');
        request.flush({});
    });
    it('sends only the rendered snapshot and observed cursor when acknowledging', () => {
        service.acknowledgeVisit(42, '2026-10-26T10:00:00Z', 3).subscribe();
        const request = http.expectOne('api/course/courses/42/home/visits');
        expect(request.request.body).toEqual({ snapshotAt: '2026-10-26T10:00:00Z', observedVariantCursor: 3 });
        request.flush(null);
    });
    it('sends no client course, student, question or chat data to the welcome endpoint', () => {
        service.generateWelcome(42, 'fingerprint', 'de').subscribe();
        const request = http.expectOne('api/course/courses/42/home/welcome');
        expect(request.request.body).toEqual({ contextFingerprint: 'fingerprint', locale: 'de' });
        request.flush({ source: 'FALLBACK', contextFingerprint: 'fingerprint' });
    });
    it('loads bounded teaching-team pages', () => {
        service.teachingTeam(42, 1).subscribe();
        const request = http.expectOne('api/course/courses/42/home/teaching-team?page=1');
        request.flush({ items: [], hasMore: false, page: 1 });
    });
});
