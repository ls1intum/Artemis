import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { SERVER_API_URL } from 'app/app.constants';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import * as chai from 'chai';
import * as moment from 'moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
chai.use(sinonChai);
const expect = chai.expect;

describe('Course Management Service', () => {
    let injector: TestBed;
    let service: CourseExerciseService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    const resourceUrl = SERVER_API_URL + 'api';
    let course: Course;
    let exercises: Exercise[];
    let returnedFromService: any;
    let programmingExercise: ProgrammingExercise;
    let modelingExercise: ModelingExercise;

    let textExercise: TextExercise;

    let fileUploadExercise: FileUploadExercise;
    let releaseDate: moment.Moment;
    let dueDate: moment.Moment;
    let assessmentDueDate: moment.Moment;

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
        injector = getTestBed();
        service = injector.get(CourseExerciseService);
        httpMock = injector.get(HttpTestingController);
        exerciseId = 123;

        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        const releaseDateRaw = new Date();
        releaseDateRaw.setMonth(3);
        releaseDate = moment(releaseDateRaw);
        const dueDateRaw = new Date();
        dueDateRaw.setMonth(6);
        dueDate = moment(dueDateRaw);
        const assessmentDueDateRaw = new Date();
        assessmentDueDate = moment(assessmentDueDateRaw);

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
        expect(moment.isMoment(exerciseToCheck.releaseDate)).to.be.true;
        expect(exerciseToCheck.releaseDate?.toISOString()).to.equal(releaseDateString);
        expect(moment.isMoment(exerciseToCheck.dueDate)).to.be.true;
        expect(exerciseToCheck.dueDate?.toISOString()).to.equal(dueDateString);
        if (!withoutAssessmentDueDate) {
            expect(moment.isMoment(exerciseToCheck.assessmentDueDate)).to.be.true;
            expect(exerciseToCheck.assessmentDueDate?.toISOString()).to.equal(assessmentDueDateString);
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
            .subscribe((res) => expect(res.body).to.deep.equal([programmingExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/courses/${course.id}/programming-exercises/`, returnedFromService, programmingExercise);
        tick();
    }));

    it('should find all modeling exercises', fakeAsync(() => {
        returnedFromService = [modelingExercise];
        service
            .findAllModelingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([modelingExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/courses/${course.id}/modeling-exercises/`, returnedFromService, modelingExercise);
        tick();
    }));

    it('should find all text exercises', fakeAsync(() => {
        returnedFromService = [textExercise];
        service
            .findAllTextExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equals([textExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/courses/${course.id}/text-exercises/`, returnedFromService, textExercise);
        tick();
    }));

    it('should find all file upload exercises', fakeAsync(() => {
        returnedFromService = [fileUploadExercise];
        service
            .findAllFileUploadExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equals([fileUploadExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/courses/${course.id}/file-upload-exercises/`, returnedFromService, fileUploadExercise);
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
        service
            .startExercise(exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).to.deep.equals(expected));

        requestAndExpectDateConversion('POST', `${resourceUrl}/exercises/${exerciseId}/participations`, returnedFromService, participation.exercise, true);
        expect(programmingExercise.studentParticipations?.[0]?.id).to.eq(participationId);
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
        service
            .resumeProgrammingExercise(exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).to.deep.equals(expected));

        requestAndExpectDateConversion('PUT', `${resourceUrl}/exercises/${exerciseId}/resume-programming-participation`, returnedFromService, participation.exercise, true);
        expect(programmingExercise.studentParticipations?.[0]?.id).to.eq(participationId);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });
});
