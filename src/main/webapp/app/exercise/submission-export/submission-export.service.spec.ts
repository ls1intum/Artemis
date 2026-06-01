import { expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { SubmissionExportService } from 'app/exercise/submission-export/submission-export.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { provideHttpClient } from '@angular/common/http';

describe('Submission Export Service', () => {
    setupTestBed({ zoneless: true });
    let service: SubmissionExportService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    const exerciseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(SubmissionExportService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('check exercise url for text exercise', () => {
        const exerciseType = ExerciseType.TEXT;
        const result = service.getExerciseUrl(exerciseType, exerciseId);

        expect(result).toBe('text/text-exercises/' + exerciseId);
    });

    it('check exercise url for modeling exercise', () => {
        const exerciseType = ExerciseType.MODELING;
        const result = service.getExerciseUrl(exerciseType, exerciseId);

        expect(result).toBe('modeling/modeling-exercises/' + exerciseId);
    });

    it('check exercise url for file upload exercise', () => {
        const exerciseType = ExerciseType.FILE_UPLOAD;
        const result = service.getExerciseUrl(exerciseType, exerciseId);

        expect(result).toBe('fileupload/file-upload-exercises/' + exerciseId);
    });

    it('check exercise url for unsupported exercise types', () => {
        const exerciseTypes = [ExerciseType.QUIZ, ExerciseType.PROGRAMMING];

        for (const exerciseType of exerciseTypes) {
            expect(() => {
                service.getExerciseUrl(exerciseType, exerciseId);
            }).toThrow('Export not implemented for exercise type ' + exerciseType);
        }
    });

    it('should export submissions', () => {
        service
            .exportSubmissions(exerciseId, ExerciseType.TEXT, {
                exportAllParticipants: true,
                filterLateSubmissions: true,
                filterLateSubmissionsDate: null,
                participantIdentifierList: 'participant1,participant2,participant3',
            })
            .subscribe((resp) => (expectedResult = resp.ok));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(new Blob());
        expect(expectedResult).toBe(true);
    });
});
