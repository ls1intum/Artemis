import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GocastService } from './gocast.service';

describe('GocastService', () => {
    let service: GocastService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(GocastService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('uses the course-scoped binding endpoints', () => {
        service.getBinding(37).subscribe();
        expect(http.expectOne('api/videosource/courses/37/binding').request.method).toBe('GET');

        service.startApproval(37).subscribe();
        expect(http.expectOne('api/videosource/courses/37/binding/approval').request.method).toBe('POST');

        service.unlink(37).subscribe();
        expect(http.expectOne('api/videosource/courses/37/binding').request.method).toBe('DELETE');
    });
});
