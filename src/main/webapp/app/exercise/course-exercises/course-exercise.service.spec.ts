import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipationDTO } from 'app/exercise/shared/entities/participation/student-participation.dto';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import dayjs from 'dayjs/esm';
import { take } from 'rxjs/operators';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { UMLDiagramType } from '@tumaet/apollon';
import { provideHttpClient } from '@angular/common/http';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('Course Management Service', () => {
    let service: CourseExerciseService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    let course: Course;
    let exercises: Exercise[];
    let returnedFromService: any;
    let programmingExercise: ProgrammingExercise;
    let modelingExercise: ModelingExercise;

    let textExercise: TextExercise;

    let fileUploadExercise: FileUploadExercise;
    let releaseDate: dayjs.Dayjs;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;

    let releaseDateString: string;
    let dueDateString: string;
    let assessmentDueDateString: string;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(CourseExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseId = 123;

        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        const releaseDateRaw = new Date();
        releaseDateRaw.setMonth(3);
        releaseDate = dayjs(releaseDateRaw);
        const dueDateRaw = new Date();
        dueDateRaw.setMonth(6);
        dueDate = dayjs(dueDateRaw);
        const assessmentDueDateRaw = new Date();
        assessmentDueDate = dayjs(assessmentDueDateRaw);

        releaseDateString = releaseDateRaw.toISOString();
        dueDateString = dueDateRaw.toISOString();
        assessmentDueDateString = assessmentDueDateRaw.toISOString();

        modelingExercise = new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined);
        modelingExercise.releaseDate = releaseDate;
        modelingExercise.dueDate = dueDate;
        modelingExercise.assessmentDueDate = assessmentDueDate;
        modelingExercise = JSON.parse(JSON.stringify(modelingExercise));

        programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.releaseDate = releaseDate;
        programmingExercise.dueDate = dueDate;
        programmingExercise.assessmentDueDate = assessmentDueDate;
        programmingExercise = JSON.parse(JSON.stringify(programmingExercise));

        textExercise = new TextExercise(course, undefined);
        textExercise.releaseDate = releaseDate;
        textExercise.dueDate = dueDate;
        textExercise.assessmentDueDate = assessmentDueDate;
        textExercise = JSON.parse(JSON.stringify(textExercise));

        fileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.releaseDate = releaseDate;
        fileUploadExercise.dueDate = dueDate;
        fileUploadExercise.assessmentDueDate = assessmentDueDate;
        fileUploadExercise = JSON.parse(JSON.stringify(fileUploadExercise));

        exercises = [];
        course.exercises = exercises;
        returnedFromService = { ...course };
    });

    const expectDateConversionToBeDone = (exerciseToCheck: Exercise, withoutAssessmentDueDate?: boolean) => {
        expect(dayjs.isDayjs(exerciseToCheck.releaseDate)).toBe(true);
        expect(exerciseToCheck.releaseDate?.toISOString()).toBe(releaseDateString);
        expect(dayjs.isDayjs(exerciseToCheck.dueDate)).toBe(true);
        expect(exerciseToCheck.dueDate?.toISOString()).toBe(dueDateString);
        if (!withoutAssessmentDueDate) {
            expect(dayjs.isDayjs(exerciseToCheck.assessmentDueDate)).toBe(true);
            expect(exerciseToCheck.assessmentDueDate?.toISOString()).toBe(assessmentDueDateString);
        }
    };

    const requestAndExpectDateConversion = (
        method: string,
        url: string,
        flushedObject: any = returnedFromService,
        exerciseToCheck: Exercise,
        withoutAssessmentDueDate?: boolean,
    ) => {
        const req = httpMock.expectOne({ method, url });
        req.flush(flushedObject);
        expectDateConversionToBeDone(exerciseToCheck, withoutAssessmentDueDate);
    };

    it('should find all programming exercises', () => {
        returnedFromService = [programmingExercise];
        service
            .findAllProgrammingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([programmingExercise]));

        requestAndExpectDateConversion('GET', `api/programming/courses/${course.id}/programming-exercises`, returnedFromService, programmingExercise);
    });

    it('should find all modeling exercises', () => {
        returnedFromService = [modelingExercise];
        service
            .findAllModelingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([modelingExercise]));

        requestAndExpectDateConversion('GET', `api/modeling/courses/${course.id}/modeling-exercises`, returnedFromService, modelingExercise);
    });

    it('should find all text exercises', () => {
        returnedFromService = [textExercise];
        service
            .findAllTextExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([textExercise]));

        requestAndExpectDateConversion('GET', `api/text/courses/${course.id}/text-exercises`, returnedFromService, textExercise);
    });

    it('should find all file upload exercises', () => {
        returnedFromService = [
            {
                id: exerciseId,
                type: ExerciseType.FILE_UPLOAD,
                teamMode: false,
                gradingInstructionFeedbackUsed: false,
                releaseDate: releaseDateString,
                dueDate: dueDateString,
                assessmentDueDate: assessmentDueDateString,
            },
        ];
        let receivedExercise: FileUploadExercise | undefined;
        service
            .findAllFileUploadExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => {
                receivedExercise = res.body?.[0];
                expect(receivedExercise).toBeInstanceOf(FileUploadExercise);
            });

        const req = httpMock.expectOne({ method: 'GET', url: `api/fileupload/courses/${course.id}/file-upload-exercises` });
        req.flush(returnedFromService);
        expectDateConversionToBeDone(receivedExercise!);
    });

    it('should start exercise', () => {
        const participationId = 12345;
        const participationDTO = createProgrammingParticipationDTO(participationId, false);
        let participation: StudentParticipation | undefined;
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo);

        service
            .startExercise(exerciseId)
            .pipe(take(1))
            .subscribe((res) => (participation = res));

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercise/exercises/${exerciseId}/participations` });
        req.flush(participationDTO);
        expect(participation).toBeInstanceOf(ProgrammingExerciseStudentParticipation);
        expect(participation?.id).toBe(participationId);
        expectDateConversionToBeDone(participation!.exercise!);
        expect(participation?.exercise?.studentParticipations?.[0]).toBe(participation);
    });

    it.each([true, false])('should start practice', (useGradedParticipation: boolean) => {
        const participationId = 12345;
        const participationDTO = createProgrammingParticipationDTO(participationId, true);
        let participation: StudentParticipation | undefined;
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo);

        service
            .startPractice(exerciseId, useGradedParticipation)
            .pipe(take(1))
            .subscribe((res) => (participation = res));

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/exercise/exercises/${exerciseId}/participations/practice?useGradedParticipation=${useGradedParticipation}`,
        });
        req.flush(participationDTO);
        expect(participation).toBeInstanceOf(ProgrammingExerciseStudentParticipation);
        expect(participation?.testRun).toBe(true);
        expectDateConversionToBeDone(participation!.exercise!);
        expect(participation?.exercise?.studentParticipations?.[0]).toBe(participation);
    });

    it('should resume programming exercise', () => {
        const participationId = 12345;
        const participationDTO = createProgrammingParticipationDTO(participationId, false);
        let participation: StudentParticipation | undefined;
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo);

        service
            .resumeProgrammingExercise(exerciseId, participationId)
            .pipe(take(1))
            .subscribe((res) => (participation = res));

        const req = httpMock.expectOne({
            method: 'PUT',
            url: `api/exercise/exercises/${exerciseId}/participations/${participationId}/resume-programming-participation`,
        });
        req.flush(participationDTO);
        expect(participation).toBeInstanceOf(ProgrammingExerciseStudentParticipation);
        expect((participation as ProgrammingExerciseStudentParticipation).repositoryUri).toBe('repository-uri');
        expectDateConversionToBeDone(participation!.exercise!);
        expect(participation?.exercise?.studentParticipations?.[0]).toBe(participation);
    });

    it('should adapt a request-feedback response', () => {
        const participationId = 12345;
        let participation: StudentParticipation | undefined;

        service
            .requestFeedback(exerciseId, participationId)
            .pipe(take(1))
            .subscribe((res) => (participation = res));

        const req = httpMock.expectOne({
            method: 'PUT',
            url: `api/exercise/exercises/${exerciseId}/participations/${participationId}/request-feedback`,
        });
        req.flush(createProgrammingParticipationDTO(participationId, false));
        expect(participation).toBeInstanceOf(ProgrammingExerciseStudentParticipation);
        expect((participation as ProgrammingExerciseStudentParticipation).repositoryUri).toBe('repository-uri');
    });

    const createProgrammingParticipationDTO = (participationId: number, testRun: boolean): StudentParticipationDTO => ({
        id: participationId,
        testRun,
        type: ParticipationType.PROGRAMMING,
        repositoryUri: 'repository-uri',
        buildPlanId: 'build-plan-id',
        branch: 'main',
        exercise: {
            id: exerciseId,
            title: 'Programming exercise',
            type: ExerciseType.PROGRAMMING,
            exerciseType: ExerciseType.PROGRAMMING,
            releaseDate: releaseDateString,
            dueDate: dueDateString,
            assessmentDueDate: assessmentDueDateString,
        },
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });
});
