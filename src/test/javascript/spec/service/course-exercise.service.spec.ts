import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
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
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
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
    let participationService: ParticipationWebsocketService;
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
        participationService = injector.get(ParticipationWebsocketService);

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

    it('should find all programming exercises', async () => {
        returnedFromService = [programmingExercise];
        service
            .findAllProgrammingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([programmingExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/programming-exercises/`, returnedFromService, programmingExercise);
    });

    it('should find all modeling exercises', async () => {
        returnedFromService = [modelingExercise];
        service
            .findAllModelingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([modelingExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/modeling-exercises/`, returnedFromService, modelingExercise);
    });

    it('should find all text exercises', async () => {
        returnedFromService = [textExercise];
        service
            .findAllTextExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([textExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/text-exercises/`, returnedFromService, textExercise);
    });

    it('should find all file upload exercises', async () => {
        returnedFromService = [fileUploadExercise];
        service
            .findAllFileUploadExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([fileUploadExercise]));

        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/file-upload-exercises/`, returnedFromService, fileUploadExercise);
    });

    it('should start exercise', async () => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        service
            .startExercise(course.id!, exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).to.eq([programmingExercise]));

        requestAndExpectDateConversion('POST', `${resourceUrl}/${course.id}/exercises/${exerciseId}/participations`, returnedFromService, participation.exercise, true);
        expect(programmingExercise.studentParticipations?.[0]?.id).to.eq(participationId);
    });

    it('should resume programming exercise', async () => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        service
            .resumeProgrammingExercise(course.id!, exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).to.eq([programmingExercise]));

        requestAndExpectDateConversion(
            'PUT',
            `${resourceUrl}/${course.id}/exercises/${exerciseId}/resume-programming-participation`,
            returnedFromService,
            participation.exercise,
            true,
        );
        expect(programmingExercise.studentParticipations?.[0]?.id).to.eq(participationId);
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });
});
