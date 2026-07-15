import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';

describe('PresentationAssessmentService', () => {
    setupTestBed({ zoneless: true });

    let service: PresentationAssessmentService;
    let httpMock: HttpTestingController;

    const courseId = 1;
    const resourceUrl = `api/courses/${courseId}/presentation-assessments`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(PresentationAssessmentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find all presentation assessments and convert presentation dates from server', () => {
        service.findAllByCourseId(courseId).subscribe((response) => {
            expect(response.body).toHaveLength(1);
            expect(dayjs.isDayjs(response.body?.[0].presentationDate)).toBe(true);
        });

        const req = httpMock.expectOne({ method: 'GET', url: resourceUrl });
        req.flush([
            {
                id: 1,
                title: 'Final presentation',
                maxPoints: 30,
                resultPoints: 28,
                presentationDate: '2026-07-20T10:00:00+02:00',
                courseId,
            },
        ]);
    });

    it('should create a presentation assessment and serialize the presentation date', () => {
        const presentationAssessment: PresentationAssessment = {
            title: 'Final presentation',
            maxPoints: 30,
            resultPoints: 28,
            presentationDate: dayjs('2026-07-20T10:00:00+02:00'),
        };

        service.create(courseId, presentationAssessment).subscribe((response) => {
            expect(response.body?.id).toBe(1);
            expect(dayjs.isDayjs(response.body?.presentationDate)).toBe(true);
        });

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        expect(typeof req.request.body.presentationDate).toBe('string');
        expect(req.request.body.resultPoints).toBe(28);
        req.flush({
            id: 1,
            title: 'Final presentation',
            maxPoints: 30,
            resultPoints: 28,
            presentationDate: '2026-07-20T10:00:00+02:00',
            courseId,
        });
    });

    it('should delete a presentation assessment', () => {
        service.delete(courseId, 1).subscribe((response) => {
            expect(response.ok).toBe(true);
        });

        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/1` });
        req.flush(null);
    });
});
