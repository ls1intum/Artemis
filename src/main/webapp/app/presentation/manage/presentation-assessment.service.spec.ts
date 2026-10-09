import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import {
    PresentationAssessment,
    PresentationAssessmentInstanceRequest,
    PresentationAssessmentInstancesCreate,
    PresentationAssessmentMode,
} from 'app/presentation/shared/entities/presentation-assessment.model';

describe('PresentationAssessmentService', () => {
    let service: PresentationAssessmentService;
    let httpMock: HttpTestingController;

    const courseId = 1;
    const resourceUrl = `api/assessment/courses/${courseId}/presentation-assessments`;

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

    it('should find all presentation assessment definitions', () => {
        service.findAllByCourseId(courseId).subscribe((response) => {
            expect(response.body).toHaveLength(1);
            expect(response.body?.[0]).toEqual({ id: 1, title: 'Final presentation', maxPoints: 30, courseId });
        });

        const req = httpMock.expectOne({ method: 'GET', url: resourceUrl });
        req.flush([
            {
                id: 1,
                title: 'Final presentation',
                maxPoints: 30,
                courseId,
            },
        ]);
    });

    it('should fetch a page of student rows', () => {
        const presentationDate = '2026-07-20T10:00:00+02:00';

        service
            .findStudentRows(courseId, {
                page: 1,
                size: 25,
                sortField: 'presentationDate',
                direction: 'DESC',
                assessmentId: 42,
                assessed: false,
                linkedToExercise: false,
                searchTerm: 'student2',
            })
            .subscribe((response) => {
                const instance = response.body?.[0].instance;
                expect(dayjs.isDayjs(instance?.presentationDate)).toBe(true);
                expect(instance?.presentationDate?.valueOf()).toBe(dayjs(presentationDate).valueOf());
                expect(response.headers.get('X-Total-Count')).toBe('26');
            });

        const req = httpMock.expectOne((request) => request.method === 'GET' && request.url === `${resourceUrl}/student-rows`);

        expect(req.request.params.get('page')).toBe('1');
        expect(req.request.params.get('size')).toBe('25');
        expect(req.request.params.get('sortField')).toBe('presentationDate');
        expect(req.request.params.get('direction')).toBe('DESC');
        expect(req.request.params.get('assessmentId')).toBe('42');
        expect(req.request.params.get('assessed')).toBe('false');
        expect(req.request.params.get('linkedToExercise')).toBe('false');
        expect(req.request.params.get('searchTerm')).toBe('student2');

        req.flush(
            [
                {
                    presentationAssessment: { id: 42, title: 'Final presentation' },
                    instance: { id: 12, student: { login: 'student2' }, presentationDate },
                },
            ],
            { headers: { 'X-Total-Count': '26' } },
        );
    });

    it('should exclude undefined filters from the request', () => {
        service
            .findStudentRows(courseId, {
                page: 0,
                size: 25,
                sortField: 'studentLogin',
                direction: 'ASC',
                assessmentId: undefined,
                assessed: undefined,
                linkedToExercise: undefined,
                searchTerm: undefined,
            })
            .subscribe((response) => {
                expect(response.body).toEqual([]);
            });

        const req = httpMock.expectOne((request) => request.method === 'GET' && request.url === `${resourceUrl}/student-rows`);

        expect(req.request.params.get('page')).toBe('0');
        expect(req.request.params.keys().sort()).toEqual(['direction', 'page', 'size', 'sortField']);

        req.flush([]);
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

    it('should create individual presentation instances and convert their dates', () => {
        const presentationDate = dayjs('2026-07-20T10:00:00+02:00');
        const request: PresentationAssessmentInstancesCreate = {
            presentationDate,
            studentLogins: ['student1', 'student2'],
            language: 'en',
            mode: PresentationAssessmentMode.IN_PERSON,
        };

        service.saveInstances(courseId, 1, request).subscribe((response) => {
            expect(response.body).toHaveLength(2);
            expect(dayjs.isDayjs(response.body?.[0].presentationDate)).toBe(true);
            expect(response.body?.map((instance) => instance.student?.login)).toEqual(['student1', 'student2']);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/1/instances` });
        expect(req.request.body.presentationDate).toBe(presentationDate.toJSON());
        expect(req.request.body.studentLogins).toEqual(['student1', 'student2']);
        req.flush([
            { id: 1, presentationDate: '2026-07-20T10:00:00+02:00', student: { login: 'student1' } },
            { id: 2, presentationDate: '2026-07-20T10:00:00+02:00', student: { login: 'student2' } },
        ]);
    });

    it('should send an instance update request and return student details', () => {
        const presentationDate = dayjs('2026-07-20T10:00:00+02:00');
        const request: PresentationAssessmentInstanceRequest = {
            id: 11,
            presentationDate,
            resultPoints: 19,
            studentLogin: 'student1',
            language: 'en',
            mode: PresentationAssessmentMode.IN_PERSON,
        };

        service.updateInstance(courseId, 42, request).subscribe((response) => {
            expect(response.body?.id).toBe(11);
            expect(response.body?.resultPoints).toBe(19);
            expect(response.body?.student).toEqual({ login: 'student1', name: 'Student One' });
            expect(dayjs.isDayjs(response.body?.presentationDate)).toBe(true);
            expect(response.body?.presentationDate?.valueOf()).toBe(presentationDate.valueOf());
        });

        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/42/instances/11` });
        expect(req.request.body).toEqual({
            id: 11,
            presentationDate: presentationDate.toJSON(),
            resultPoints: 19,
            studentLogin: 'student1',
            language: 'en',
            mode: PresentationAssessmentMode.IN_PERSON,
        });
        req.flush({
            id: 11,
            presentationDate: presentationDate.toJSON(),
            resultPoints: 19,
            language: 'en',
            mode: PresentationAssessmentMode.IN_PERSON,
            student: { login: 'student1', name: 'Student One' },
        });
    });
});
