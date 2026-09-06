import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';

describe('AthenaCourseConfigService', () => {
    let service: AthenaCourseConfigService;
    let httpMock: HttpTestingController;

    const config: AthenaCourseConfigDTO = { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(AthenaCourseConfigService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get the athena configuration of a course', () => {
        let received: AthenaCourseConfigDTO | undefined;
        service.getCourseConfig(42).subscribe((response) => (received = response));

        const request = httpMock.expectOne({ method: 'GET', url: 'api/course/courses/42/athena-configuration' });
        request.flush(config);

        expect(received).toEqual(config);
    });

    it('should update the athena configuration of a course', () => {
        let received: AthenaCourseConfigDTO | undefined;
        service.updateCourseConfig(42, config).subscribe((response) => (received = response.body ?? undefined));

        const request = httpMock.expectOne({ method: 'PUT', url: 'api/course/courses/42/athena-configuration' });
        expect(request.request.body).toEqual(config);
        request.flush(config);

        expect(received).toEqual(config);
    });
});
