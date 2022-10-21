import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import { SubmissionExportService } from 'app/exercises/shared/submission-export/submission-export.service';
import { ArtemisTestModule } from '../test.module';

describe('Submission Export Service', () => {
    let service: SubmissionExportService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    const exerciseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
        });
        service = TestBed.inject(SubmissionExportService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('check exercise url for text exercise', () => {
        const exerciseType = ExerciseType.TEXT;
        const result = service.getExerciseUrl(exerciseType, exerciseId);

        expect(result).toBe('text-exercises/' + exerciseId);
    });

    it('check exercise url for modeling exercise', () => {
        const exerciseType = ExerciseType.MODELING;
        const result = service.getExerciseUrl(exerciseType, exerciseId);

        expect(result).toBe('modeling-exercises/' + exerciseId);
    });

    it('check exercise url for file upload exercise', () => {
        const exerciseType = ExerciseType.FILE_UPLOAD;
        const result = service.getExerciseUrl(exerciseType, exerciseId);

        expect(result).toBe('file-upload-exercises/' + exerciseId);
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
        expect(expectedResult).toBeTrue();
    });
});
