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

    it('should send updates of the same course one after the other', () => {
        const second: AthenaCourseConfigDTO = { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true };
        const received: AthenaCourseConfigDTO[] = [];

        service.updateCourseConfig(42, config).subscribe((response) => received.push(response.body!));
        service.updateCourseConfig(42, second).subscribe((response) => received.push(response.body!));

        // The second update waits: it is only sent once the first one has answered, so the server cannot store the
        // older snapshot last and the responses arrive in the order the instructor switched the features.
        const first = httpMock.expectOne({ method: 'PUT', url: 'api/course/courses/42/athena-configuration' });
        expect(first.request.body).toEqual(config);
        first.flush(config);

        const next = httpMock.expectOne({ method: 'PUT', url: 'api/course/courses/42/athena-configuration' });
        expect(next.request.body).toEqual(second);
        next.flush(second);

        expect(received).toEqual([config, second]);
    });

    it('should send a queued update even when the one before it failed', () => {
        const second: AthenaCourseConfigDTO = { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true };
        let error: unknown;
        let received: AthenaCourseConfigDTO | undefined;

        service.updateCourseConfig(42, config).subscribe({ error: (failure) => (error = failure) });
        service.updateCourseConfig(42, second).subscribe((response) => (received = response.body ?? undefined));

        httpMock.expectOne({ method: 'PUT', url: 'api/course/courses/42/athena-configuration' }).flush('error', { status: 500, statusText: 'Server Error' });

        const next = httpMock.expectOne({ method: 'PUT', url: 'api/course/courses/42/athena-configuration' });
        expect(next.request.body).toEqual(second);
        next.flush(second);

        expect(error).toBeDefined();
        expect(received).toEqual(second);
    });

    it('should not make the update of one course wait for another course', () => {
        service.updateCourseConfig(42, config).subscribe();
        service.updateCourseConfig(43, config).subscribe();

        const requests = httpMock.match({ method: 'PUT' });
        expect(requests).toHaveLength(2);
        requests.forEach((request) => request.flush(config));
    });
});
