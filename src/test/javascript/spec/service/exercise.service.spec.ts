import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { EntityResponseType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { MockProvider } from 'ng-mocks';
import { SafeHtml } from '@angular/platform-browser';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { Observable } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

describe('Exercise Service', () => {
    let service: ExerciseService;
    let httpMock: HttpTestingController;
    let artemisMarkdown: ArtemisMarkdownService;
    let exercise: Exercise;
    let currentDate: dayjs.Dayjs;

    const modelingExercise = {
        id: 23,
        type: ExerciseType.MODELING,
        studentParticipations: [],
        exampleSolutionModel: '{ "key": "value" }',
        exampleSolutionExplanation: 'Solution<br>Explanation',
    } as unknown as ModelingExercise;

    const textExercise = {
        id: 24,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        exampleSolution: 'Example<br>Solution',
    } as unknown as TextExercise;

    const fileUploadExercise = {
        id: 25,
        type: ExerciseType.FILE_UPLOAD,
        studentParticipations: [],
        exampleSolution: 'Example<br>Solution',
    } as unknown as FileUploadExercise;

    const programmingExercise = {
        id: 26,
        type: ExerciseType.PROGRAMMING,
        studentParticipations: [],
        exam: 'Example<br>Solution',
        exampleSolutionPublished: true,
    } as unknown as ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ArtemisMarkdownService),
                MockProvider(AccountService),
                MockProvider(EntityTitleService),
                MockProvider(ProfileService),
            ],
        });
        service = TestBed.inject(ExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
        artemisMarkdown = TestBed.inject(ArtemisMarkdownService);
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

        expect(exercise.dueDateError).toBeFalsy();
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

    it('should fill & empty example modeling solution', () => {
        let exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...modelingExercise, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toEqual(JSON.parse(modelingExercise.exampleSolutionModel!));
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeTrue();

        exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...exercise }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeFalse();
    });

    it('should fill & empty example text solution', () => {
        const artemisMarkdownSpy = jest.spyOn(artemisMarkdown, 'safeHtmlForMarkdown').mockReturnValue({} as SafeHtml);

        let exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...textExercise, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeDefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeTrue();
        expect(artemisMarkdownSpy).toHaveBeenCalledOnce();
        expect(artemisMarkdownSpy).toHaveBeenCalledWith(textExercise.exampleSolution);

        exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...exercise }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeFalse();
    });

    it('should fill & empty example file upload solution', () => {
        const artemisMarkdownSpy = jest.spyOn(artemisMarkdown, 'safeHtmlForMarkdown').mockReturnValue({} as SafeHtml);

        let exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...fileUploadExercise, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeDefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeTrue();
        expect(artemisMarkdownSpy).toHaveBeenCalledOnce();
        expect(artemisMarkdownSpy).toHaveBeenCalledWith(fileUploadExercise.exampleSolution);

        exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...exercise }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeFalse();
    });

    it('should fill & empty example programming exercise solution', () => {
        let exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...programmingExercise, exampleSolutionPublicationDate: dayjs().subtract(1, 'm') }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeTrue();

        exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...programmingExercise, exampleSolutionPublicationDate: dayjs().add(1, 'm') }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeFalse();

        exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo({ ...exercise }, artemisMarkdown);
        expect(exampleSolutionInfo.exampleSolution).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionUML).toBeUndefined();
        expect(exampleSolutionInfo.programmingExercise).toBeUndefined();
        expect(exampleSolutionInfo.exampleSolutionPublished).toBeFalse();
    });

    it('should determine is included in score string', () => {
        const translateService = TestBed.inject(TranslateService);
        const translateServiceSpy = jest.spyOn(translateService, 'instant');

        let callCount = 0;
        const result = service.isIncludedInScore({} as Exercise);
        expect(result).toBe('');
        expect(translateServiceSpy).not.toHaveBeenCalled();

        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
        service.isIncludedInScore(exercise);

        callCount++;
        expect(translateServiceSpy).toHaveBeenCalledTimes(callCount);
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.bonus');

        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        service.isIncludedInScore(exercise);

        callCount++;
        expect(translateServiceSpy).toHaveBeenCalledTimes(callCount);
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.yes');

        exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
        service.isIncludedInScore(exercise);

        callCount++;
        expect(translateServiceSpy).toHaveBeenCalledTimes(callCount);
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.no');
    });

    it.each([
        [{ quizBatches: [{ started: false }, { started: true }] } as QuizExercise, true],
        [{ quizBatches: [{ started: false }, { started: false }] } as QuizExercise, false],
        [{ studentParticipations: [{ initializationState: InitializationState.INITIALIZED }] } as QuizExercise, true],
        [{ studentParticipations: [{ initializationState: InitializationState.FINISHED }] } as QuizExercise, true],
        [{ studentParticipations: [{ initializationState: InitializationState.INACTIVE }] } as QuizExercise, false],
    ])('should determine correctly if quiz is active', (quizExercise: QuizExercise, expected: boolean) => {
        expect(service.isActiveQuiz(quizExercise)).toEqual(expected);
    });

    it('should process exercise entity response', () => {
        const accountService = TestBed.inject(AccountService);
        const entityTitleService = TestBed.inject(EntityTitleService);
        const profileService = TestBed.inject(ProfileService);

        const accountServiceSpy = jest.spyOn(accountService, 'setAccessRightsForExerciseAndReferencedCourse');
        const entityTitleServiceSpy = jest.spyOn(entityTitleService, 'setTitle');
        const profileServiceSpy = jest.spyOn(profileService, 'getProfileInfo');

        const category = {
            color: '#6ae8ac',
            category: 'category1',
        } as ExerciseCategory;

        const releaseDate = dayjs();

        const exerciseFromServer: Exercise = Object.assign({}, textExercise, {
            title: 'My Title',
            categories: [JSON.stringify(category)],
            releaseDate: releaseDate.toJSON(),
        });

        const processedExercise = service.processExerciseEntityResponse({ body: exerciseFromServer } as EntityResponseType).body!;

        expect(processedExercise.id).toBe(exerciseFromServer.id);
        expect(processedExercise.categories).toHaveLength(1);
        expect(processedExercise.categories![0]).toEqual(category);

        expect(processedExercise.releaseDate).toEqual(releaseDate);
        expect(processedExercise.startDate).toBeUndefined();

        expect(accountServiceSpy).toHaveBeenCalledOnce();
        expect(accountServiceSpy).toHaveBeenCalledWith(expect.objectContaining({ id: exerciseFromServer.id }));

        expect(entityTitleServiceSpy).toHaveBeenCalledOnce();
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.EXERCISE, [exerciseFromServer.id], exerciseFromServer.title);

        expect(profileServiceSpy).not.toHaveBeenCalled();
    });

    it.each(['create', 'update'])('should send %s request for the exercise', (action: string) => {
        const serviceSpy = jest.spyOn(service, 'processExerciseEntityResponse');

        const category = {
            color: '#6ae8ac',
            category: 'category1',
        } as ExerciseCategory;

        const releaseDate = dayjs();

        exercise = Object.assign({}, textExercise, {
            categories: [category],
            releaseDate,
        });
        const expectedReturnedExercise = { id: exercise.id } as Exercise;

        const expectedUrl = `api/exercises`;
        let result$: Observable<EntityResponseType>;
        let method: string;
        if (action === 'create') {
            result$ = service.create(exercise);
            method = 'POST';
        } else if (action === 'update') {
            result$ = service.update(exercise);
            method = 'PUT';
        } else {
            throw new Error(`Unexpected action: ${action}`);
        }

        let actualReturnedExercise = undefined;
        result$.subscribe((exerciseResponse) => (actualReturnedExercise = exerciseResponse.body!));

        const testRequest = httpMock.expectOne({
            url: expectedUrl,
            method,
        });

        testRequest.flush(expectedReturnedExercise);

        const sentBody = testRequest.request.body;

        expect(sentBody.categories).toHaveLength(1);
        expect(sentBody.categories[0]).toBe(JSON.stringify(category));

        expect(sentBody.releaseDate).toBe(releaseDate.toJSON());
        expect(sentBody.startDate).toBeUndefined();

        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(expect.objectContaining({ body: expectedReturnedExercise }));
        expect(actualReturnedExercise).toEqual(expectedReturnedExercise);
    });

    it('should get exercise details', () => {
        const serviceSpy = jest.spyOn(service, 'processExerciseEntityResponse');

        const exerciseId = 123;

        const expectedReturnedExercise = {
            id: exerciseId,
            posts: undefined,
        } as Exercise;

        const result = service.getExerciseDetails(exerciseId);

        let actualReturnedExercise: Exercise | undefined = undefined;
        result.subscribe((exerciseResponse) => (actualReturnedExercise = exerciseResponse.body!));

        const testRequest = httpMock.expectOne({
            url: `api/exercises/${exerciseId}/details`,
            method: 'GET',
        });

        testRequest.flush(expectedReturnedExercise);

        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(expect.objectContaining({ body: expectedReturnedExercise }));
        expect(actualReturnedExercise).toEqual(expectedReturnedExercise);
        expect(actualReturnedExercise!.posts).toEqual([]);
    });

    it('should get exercise for example solution', () => {
        const serviceSpy = jest.spyOn(service, 'processExerciseEntityResponse');

        const exerciseId = 124;

        const expectedReturnedExercise = {
            id: exerciseId,
            exampleSolutionPublicationDate: dayjs().subtract(1, 'm'),
            exampleSolution: 'Example solution',
        } as TextExercise;

        const result = service.getExerciseForExampleSolution(exerciseId);

        let actualReturnedExercise = undefined;
        result.subscribe((exerciseResponse) => (actualReturnedExercise = exerciseResponse.body!));

        const testRequest = httpMock.expectOne({
            url: `api/exercises/${exerciseId}/example-solution`,
            method: 'GET',
        });

        testRequest.flush(expectedReturnedExercise);

        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledWith(expect.objectContaining({ body: expectedReturnedExercise }));
        expect(actualReturnedExercise).toEqual(expectedReturnedExercise);
    });

    it('should send a reset request', () => {
        const exerciseId = 125;

        service.reset(exerciseId).subscribe();

        httpMock.expectOne({
            url: `api/exercises/${exerciseId}/reset`,
            method: 'DELETE',
        });
    });

    it('should toggle second correction', () => {
        const exerciseId = 127;

        service.toggleSecondCorrection(exerciseId).subscribe();

        httpMock.expectOne({
            url: `api/exercises/${exerciseId}/toggle-second-correction`,
            method: 'PUT',
        });
    });

    it('should correctly send the exercise name to the title service', () => {
        const entityTitleService = TestBed.inject(EntityTitleService);
        const examExerciseForStudent = { id: 1, title: 'exercise', exerciseGroup: { id: 1, title: 'exercise group' } } as Exercise;
        const examExerciseForTutor = { ...examExerciseForStudent, isAtLeastTutor: true } as Exercise;
        const courseExerciseForStudent = { ...examExerciseForStudent, exerciseGroup: undefined, course: { id: 2, title: 'course' } } as Exercise;
        const courseExerciseForTutor = { ...courseExerciseForStudent, isAtLeastTutor: true } as Exercise;
        const entityTitleServiceSpy = jest.spyOn(entityTitleService, 'setTitle');
        service.sendExerciseTitleToTitleService(examExerciseForStudent);
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.EXERCISE, [1], 'exercise group');
        service.sendExerciseTitleToTitleService(examExerciseForTutor);
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.EXERCISE, [1], 'exercise');
        service.sendExerciseTitleToTitleService(courseExerciseForStudent);
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.EXERCISE, [1], 'exercise');
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.COURSE, [2], 'course');
        service.sendExerciseTitleToTitleService(courseExerciseForTutor);
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.EXERCISE, [1], 'exercise');
        expect(entityTitleServiceSpy).toHaveBeenCalledWith(EntityType.COURSE, [2], 'course');
    });
});
