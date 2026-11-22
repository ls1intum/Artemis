import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('Exercise Group Service', () => {
    let service: ExerciseGroupService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ExerciseGroupService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should GET the group and convert it into the domain classes', () => {
        let group: ExerciseGroup | undefined;
        service.find(1, 2, 3).subscribe((result) => (group = result));

        const req = httpMock.expectOne('/api/exam/courses/1/exams/2/exercise-groups/3');
        expect(req.request.method).toBe('GET');
        req.flush({
            id: 3,
            title: 'Group',
            isMandatory: true,
            exam: { id: 2, examMode: 'TEST', exampleSolutionPublicationDate: '2026-01-02T03:04:05Z', course: { id: 1, defaultProgrammingLanguage: 'JAVA' } },
        });

        expect(Object.keys(group?.exam ?? {}).sort()).toEqual(['course', 'examMode', 'exampleSolutionPublicationDate', 'id']);
        expect(Object.keys(group?.exam?.course ?? {}).sort()).toEqual(['defaultProgrammingLanguage', 'id']);
        expect(group?.id).toBe(3);
        expect(group?.title).toBe('Group');
        expect(group?.isMandatory).toBe(true);
        expect(group?.exam?.id).toBe(2);
        expect(group?.exam?.examMode).toBe(ExamMode.TEST);
        expect(group?.exam?.exampleSolutionPublicationDate?.toISOString()).toBe('2026-01-02T03:04:05.000Z');
        expect(group?.exam?.course?.id).toBe(1);
        expect(group?.exam?.course?.defaultProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
        expect(group?.exercises).toBeUndefined();
    });

    it('should leave out the exam and the dates the server does not send', () => {
        let group: ExerciseGroup | undefined;
        service.find(1, 2, 3).subscribe((result) => (group = result));

        httpMock.expectOne('/api/exam/courses/1/exams/2/exercise-groups/3').flush({ id: 3, exam: { id: 2 } });

        expect(group?.exam?.exampleSolutionPublicationDate).toBeUndefined();
        expect(group?.exam?.course).toBeUndefined();
        expect(group?.exam?.examMode).toBeUndefined();
    });

    it('should propagate the error of the request', () => {
        let status: number | undefined;
        service.find(1, 2, 3).subscribe({ error: (error) => (status = error.status) });

        httpMock.expectOne('/api/exam/courses/1/exams/2/exercise-groups/3').flush(null, { status: 404, statusText: 'Not Found' });

        expect(status).toBe(404);
    });
});
