import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

describe('Exercise Service', () => {
    let service: ExerciseService;
    let httpMock: HttpTestingController;
    let exercise: Exercise;
    let currentDate: dayjs.Dayjs;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(ExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
        currentDate = dayjs();

        exercise = new TextExercise(undefined, undefined);
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should validate equal dates', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.dueDateError = true;
        exercise.assessmentDueDateError = true;
        exercise.exampleSolutionPublicationDateError = true;
        exercise.exampleSolutionPublicationDateWarning = true;

        exercise.releaseDate = currentDate.add(1, 'day');
        exercise.dueDate = currentDate.add(1, 'day');
        exercise.assessmentDueDate = currentDate.add(1, 'day');
        exercise.exampleSolutionPublicationDate = currentDate.add(1, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.assessmentDueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeFalse();
    });

    it('should validate dates', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.dueDateError = true;
        exercise.assessmentDueDateError = true;
        exercise.exampleSolutionPublicationDateError = true;
        exercise.exampleSolutionPublicationDateWarning = true;

        exercise.releaseDate = currentDate.add(1, 'day');
        exercise.dueDate = currentDate.add(2, 'day');
        exercise.assessmentDueDate = currentDate.add(4, 'day');
        exercise.exampleSolutionPublicationDate = currentDate.add(2, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.assessmentDueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeFalse();
    });

    it('should set errors on invalid due and assessment due dates', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.dueDateError = false;
        exercise.assessmentDueDateError = false;

        exercise.releaseDate = currentDate.add(3, 'day');
        exercise.dueDate = currentDate.add(2, 'day');
        exercise.assessmentDueDate = currentDate.add(1, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeTrue();
        expect(exercise.assessmentDueDateError).toBeTrue();
    });

    it('should validate empty example solution publication date with assessment due date', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.dueDateError = true;
        exercise.assessmentDueDateError = true;
        exercise.exampleSolutionPublicationDateError = true;
        exercise.exampleSolutionPublicationDateWarning = true;

        exercise.releaseDate = currentDate.add(1, 'day');
        exercise.dueDate = currentDate.add(2, 'day');
        exercise.assessmentDueDate = currentDate.add(4, 'day');
        exercise.exampleSolutionPublicationDate = undefined;

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.assessmentDueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeFalse();
    });

    it('should validate empty example solution publication date', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.dueDateError = true;
        exercise.exampleSolutionPublicationDateError = true;
        exercise.exampleSolutionPublicationDateWarning = true;

        exercise.releaseDate = currentDate.add(1, 'day');
        exercise.dueDate = currentDate.add(2, 'day');
        exercise.exampleSolutionPublicationDate = undefined;

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeFalse();
    });

    it('should set error when due date is before release date', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.dueDateError = false;

        exercise.releaseDate = currentDate.add(5, 'day');
        exercise.dueDate = currentDate.add(2, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeTrue();
    });

    it('should set error when example solution publication date is before release date', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.exampleSolutionPublicationDateError = false;
        exercise.exampleSolutionPublicationDateWarning = false;

        exercise.releaseDate = currentDate.add(5, 'day');
        exercise.dueDate = undefined;
        exercise.exampleSolutionPublicationDate = currentDate.add(3, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeTrue();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeTrue();
    });

    it('should set error when example solution publication date is before due date', () => {
        // Set flags to opposite of what is expected so we know they are changed.
        exercise.exampleSolutionPublicationDateError = false;
        exercise.exampleSolutionPublicationDateWarning = true;

        exercise.releaseDate = currentDate.add(1, 'day');
        exercise.dueDate = currentDate.add(5, 'day');
        exercise.exampleSolutionPublicationDate = currentDate.add(3, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeTrue();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeFalse();
    });

    it('should allow example solution publication date is before due date with a warning', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;

        // Set flags to opposite of what is expected so we know they are changed.
        exercise.exampleSolutionPublicationDateError = true;
        exercise.exampleSolutionPublicationDateWarning = false;

        exercise.releaseDate = currentDate.add(1, 'day');
        exercise.dueDate = currentDate.add(5, 'day');
        exercise.exampleSolutionPublicationDate = currentDate.add(3, 'day');

        service.validateDate(exercise);

        expect(exercise.dueDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateError).toBeFalse();
        expect(exercise.exampleSolutionPublicationDateWarning).toBeTrue();
    });
});
