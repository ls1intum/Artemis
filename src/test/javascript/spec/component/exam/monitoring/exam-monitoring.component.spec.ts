import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { ExamMonitoringComponent, TableContent } from 'app/exam/monitoring/exam-monitoring.component';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { ArtemisTestModule } from '../../../test.module';
import { createTestExercises } from './exam-monitoring-helper';

describe('Exam Monitoring Component', () => {
    // Course
    const course = new Course();
    course.id = 1;

    // exercises
    const exercises = createTestExercises(3);

    // Exam
    const exam = new Exam();
    exam.id = 1;
    exam.title = 'Test Exam';
    exam.startDate = dayjs();
    exam.endDate = dayjs().add(1, 'hour');
    exam.numberOfRegisteredUsers = 2;
    exam.exerciseGroups = [];

    let comp: ExamMonitoringComponent;
    let fixture: ComponentFixture<ExamMonitoringComponent>;
    let examMonitoringService: ExamMonitoringService;
    let examManagementService: ExamManagementService;
    let examActionService: ExamActionService;
    let pipe: ArtemisDatePipe;

    const route = { params: of({ courseId: course.id, examId: exam.id }) };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamMonitoringComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: ArtemisDatePipe },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
                fixture = TestBed.createComponent(ExamMonitoringComponent);
                comp = fixture.componentInstance;
                examMonitoringService = TestBed.inject(ExamMonitoringService);
                examManagementService = TestBed.inject(ExamManagementService);
                examActionService = TestBed.inject(ExamActionService);
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    // On init
    it('should call find of examManagementService to get the exam on init', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examManagementService.find).toHaveBeenCalledOnce();
        expect(examManagementService.find).toHaveBeenCalledWith(course.id, exam.id, false, true);
        expect(comp.exam).toEqual(exam);
    });

    it('should call notifyExamSubscribers on init', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));
        const spy = jest.spyOn(examMonitoringService, 'notifyExamSubscribers');

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(exam);
    });

    it('should call initTable on init', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));

        const table: TableContent[] = [
            new TableContent('title', exam.title),
            new TableContent('start', pipe.transform(exam.startDate)),
            new TableContent('end', pipe.transform(exam.endDate)),
            new TableContent('students', exam.numberOfRegisteredUsers),
            new TableContent('exercises', 0),
            new TableContent('exerciseGroups', exam.exerciseGroups?.length),
        ];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.table).toEqual(table);
    });

    it('should handle undefined exercise groups and init table', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));

        exam.exerciseGroups = undefined;

        const table: TableContent[] = [
            new TableContent('title', exam.title),
            new TableContent('start', pipe.transform(exam.startDate)),
            new TableContent('end', pipe.transform(exam.endDate)),
            new TableContent('students', exam.numberOfRegisteredUsers),
            new TableContent('exercises', 0),
            new TableContent('exerciseGroups', 0),
        ];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.table).toEqual(table);
    });

    it('should handle exercise groups with exercises and init table', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));

        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exercises = exercises;
        exam.exerciseGroups = [exerciseGroup];

        const table: TableContent[] = [
            new TableContent('title', exam.title),
            new TableContent('start', pipe.transform(exam.startDate)),
            new TableContent('end', pipe.transform(exam.endDate)),
            new TableContent('students', exam.numberOfRegisteredUsers),
            new TableContent('exercises', 3),
            new TableContent('exerciseGroups', 1),
        ];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.table).toEqual(table);
    });

    it('should handle exercise groups with undefined exercises and init table', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));

        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exercises = undefined;
        exam.exerciseGroups = [exerciseGroup];

        const table: TableContent[] = [
            new TableContent('title', exam.title),
            new TableContent('start', pipe.transform(exam.startDate)),
            new TableContent('end', pipe.transform(exam.endDate)),
            new TableContent('students', exam.numberOfRegisteredUsers),
            new TableContent('exercises', 0),
            new TableContent('exerciseGroups', 1),
        ];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.table).toEqual(table);
    });

    it('should call subscribeForLatestExamAction of examActionService to get the latest actions on init', () => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));
        jest.spyOn(examActionService, 'subscribeForLatestExamAction');

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examActionService.subscribeForLatestExamAction).toHaveBeenCalledOnce();
        expect(examActionService.subscribeForLatestExamAction).toHaveBeenCalledWith(exam);
    });

    it('should call loadInitialActions of examActionService to get the initial actions on init', fakeAsync(() => {
        // GIVEN
        const responseFakeExam = { body: exam as Exam } as HttpResponse<Exam>;
        jest.spyOn(examManagementService, 'find').mockReturnValue(of(responseFakeExam));
        jest.spyOn(examActionService, 'loadInitialActions');

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examActionService.loadInitialActions).toHaveBeenCalledOnce();
        expect(examActionService.loadInitialActions).toHaveBeenCalledWith(exam);
    }));
});
