import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import dayjs from 'dayjs/esm';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

describe('Course Management Service', () => {
    let service: CourseExerciseService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    const resourceUrl = SERVER_API_URL + 'api/courses';
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
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
        expect(dayjs.isDayjs(exerciseToCheck.releaseDate)).toBeTrue();
        expect(exerciseToCheck.releaseDate?.toISOString()).toBe(releaseDateString);
        expect(dayjs.isDayjs(exerciseToCheck.dueDate)).toBeTrue();
        expect(exerciseToCheck.dueDate?.toISOString()).toBe(dueDateString);
        if (!withoutAssessmentDueDate) {
            expect(dayjs.isDayjs(exerciseToCheck.assessmentDueDate)).toBeTrue();
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

    it('should find all programming exercises', fakeAsync(() => {
        returnedFromService = [programmingExercise];
        service
            .findAllProgrammingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([programmingExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/programming-exercises/`, returnedFromService, programmingExercise);
        tick();
    }));

    it('should find all modeling exercises', fakeAsync(() => {
        returnedFromService = [modelingExercise];
        service
            .findAllModelingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([modelingExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/modeling-exercises/`, returnedFromService, modelingExercise);
        tick();
    }));

    it('should find all text exercises', fakeAsync(() => {
        returnedFromService = [textExercise];
        service
            .findAllTextExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([textExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/text-exercises/`, returnedFromService, textExercise);
        tick();
    }));

    it('should find all file upload exercises', fakeAsync(() => {
        returnedFromService = [fileUploadExercise];
        service
            .findAllFileUploadExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([fileUploadExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/file-upload-exercises/`, returnedFromService, fileUploadExercise);
        tick();
    }));

    it('should start exercise', fakeAsync(() => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        const expected = Object.assign(
            {
                initializationDate: undefined,
            },
            participation,
        );
        jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue(of({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo));

        service
            .startExercise(exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(expected));

        requestAndExpectDateConversion('POST', SERVER_API_URL + `api/exercises/${exerciseId}/participations`, returnedFromService, participation.exercise, true);
        expect(programmingExercise.studentParticipations?.[0]?.id).toBe(participationId);
        tick();
    }));

    it('should start practice', fakeAsync(() => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        const expected = Object.assign(
            {
                initializationDate: undefined,
            },
            participation,
        );
        jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue(of({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo));

        service
            .startPractice(exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(expected));

        requestAndExpectDateConversion('POST', SERVER_API_URL + `api/exercises/${exerciseId}/participations/practice`, returnedFromService, participation.exercise, true);
        expect(programmingExercise.studentParticipations?.[0]?.id).toBe(participationId);
        tick();
    }));

    it('should resume programming exercise', fakeAsync(() => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        const expected = Object.assign(
            {
                initializationDate: undefined,
            },
            participation,
        );
        jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue(of({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo));

        service
            .resumeProgrammingExercise(exerciseId, participationId)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(expected));

        requestAndExpectDateConversion(
            'PUT',
            SERVER_API_URL + `api/exercises/${exerciseId}/resume-programming-participation/${participationId}`,
            returnedFromService,
            participation.exercise,
            true,
        );
        expect(programmingExercise.studentParticipations?.[0]?.id).toBe(participationId);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });
});
