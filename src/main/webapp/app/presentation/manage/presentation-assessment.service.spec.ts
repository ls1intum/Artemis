import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { PresentationAssessment, PresentationAssessmentInstance } from 'app/presentation/shared/entities/presentation-assessment.model';

describe('PresentationAssessmentService', () => {
    let service: PresentationAssessmentService;
    let httpMock: HttpTestingController;

    const courseId = 1;
    const resourceUrl = `api/presentation/courses/${courseId}/presentation-assessments`;

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

    it('should find all presentation assessments and convert instance dates from server', () => {
        service.findAllByCourseId(courseId).subscribe((response) => {
            expect(response.body).toHaveLength(1);
            expect(dayjs.isDayjs(response.body?.[0].instances?.[0].presentationDate)).toBe(true);
        });

        const req = httpMock.expectOne({ method: 'GET', url: resourceUrl });
        req.flush([
            {
                id: 1,
                title: 'Final presentation',
                maxPoints: 30,
                courseId,
                instances: [{ id: 2, presentationDate: '2026-07-20T10:00:00+02:00' }],
            },
        ]);
    });

    it('should create a presentation assessment', () => {
        const presentationAssessment: PresentationAssessment = {
            title: 'Final presentation',
            maxPoints: 30,
        };

        service.create(courseId, presentationAssessment).subscribe((response) => {
            expect(response.body?.id).toBe(1);
            expect(response.body?.title).toBe('Final presentation');
        });

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        expect(req.request.body).toEqual(presentationAssessment);
        req.flush({
            id: 1,
            title: 'Final presentation',
            maxPoints: 30,
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

    it('should save presentation instances atomically and convert their dates', () => {
        const presentationDate = dayjs('2026-07-20T10:00:00+02:00');
        const instance: PresentationAssessmentInstance = { presentationDate, studentLogins: ['student1', 'student2'] };

        service.saveInstances(courseId, 1, instance).subscribe((response) => {
            expect(response.body).toHaveLength(2);
            expect(dayjs.isDayjs(response.body?.[0].presentationDate)).toBe(true);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/1/instances/batch` });
        expect(req.request.body.presentationDate).toBe(presentationDate.toJSON());
        expect(req.request.body.studentLogins).toEqual(['student1', 'student2']);
        req.flush([
            { id: 1, presentationDate: '2026-07-20T10:00:00+02:00', studentLogins: ['student1'] },
            { id: 2, presentationDate: '2026-07-20T10:00:00+02:00', studentLogins: ['student2'] },
        ]);
    });

    it('should find all students in the course', () => {
        service.findCourseStudents(courseId).subscribe((response) => {
            expect(response.body?.[0].login).toBe('student1');
        });

        const req = httpMock.expectOne({ method: 'GET', url: `api/course/courses/${courseId}/students` });
        req.flush([{ id: 1, login: 'student1' }]);
    });
});
