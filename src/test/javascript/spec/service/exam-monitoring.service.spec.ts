import { TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { StudentExam } from 'app/entities/student-exam.model';
import {
    ConnectionUpdatedAction,
    ContinuedAfterHandedInEarlyAction,
    EndedExamAction,
    ExamAction,
    ExamActionType,
    ExamActivity,
    HandedInEarlyAction,
    SavedExerciseAction,
    StartedExamAction,
    SwitchedExerciseAction,
} from 'app/entities/exam-user-activity.model';
import { ExamMonitoringService } from 'app/exam/monitor/exam-monitoring.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';

const createExamActions = (): ExamAction[] => {
    return Object.keys(ExamActionType).map((type) => createExamActionBasedOnType(ExamActionType[type]));
};

const createExamActionBasedOnType = (examActionType: ExamActionType): ExamAction => {
    let examAction: ExamAction;

    switch (examActionType) {
        case ExamActionType.STARTED_EXAM:
            examAction = new StartedExamAction(0);
            break;
        case ExamActionType.ENDED_EXAM:
            examAction = new EndedExamAction();
            break;
        case ExamActionType.HANDED_IN_EARLY:
            examAction = new HandedInEarlyAction();
            break;
        case ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY:
            examAction = new ContinuedAfterHandedInEarlyAction();
            break;
        case ExamActionType.SWITCHED_EXERCISE:
            examAction = new SwitchedExerciseAction(0);
            break;
        case ExamActionType.SAVED_EXERCISE:
            examAction = new SavedExerciseAction(false, 0, false, true);
            break;
        case ExamActionType.CONNECTION_UPDATED:
            examAction = new ConnectionUpdatedAction(false);
            break;
    }
    examAction.studentExamId = 1;
    return examAction;
};

describe('ExamMonitoringService', () => {
    let examMonitoringService: ExamMonitoringService;
    let websocketService: JhiWebsocketService;
    let exam: Exam;
    let studentExam: StudentExam;
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [ArtemisServerDateService, ExamMonitoringService, { provide: JhiWebsocketService, useClass: MockWebsocketService }],
        })
            .compileComponents()
            .then(() => {
                examMonitoringService = TestBed.inject(ExamMonitoringService);
                websocketService = TestBed.inject(JhiWebsocketService);
            });
    });

    beforeEach(() => {
        // reset exam
        exam = new Exam();
        exam.id = 1;
        exam.monitoring = true;

        // reset studentExam
        studentExam = new StudentExam();
        studentExam.id = 1;

        // reset course
        course = new Course();
        course.id = 1;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should handle exam actions when monitoring enabled', () => {
        const examActions = createExamActions();
        expect(examActions).toHaveLength(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            examMonitoringService.handleActionEvent(studentExam, action, exam.monitoring!);
            expect(studentExam.examActivity?.examActions).toEqual([action]);
            studentExam.examActivity!.examActions = [];
        });
    });

    it('should not handle exam actions when monitoring disabled', () => {
        exam.monitoring = false;
        const examActions = createExamActions();
        expect(examActions).toHaveLength(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            examMonitoringService.handleActionEvent(studentExam, action, exam.monitoring!);
            expect(studentExam.examActivity).toBeUndefined();
        });
    });

    it.each(createExamActions())('should send actions when monitoring enabled and websocket connected', (action: ExamAction) => {
        const spy = jest.spyOn(examMonitoringService, 'sendAction');
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, true);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(action, exam.id);
    });

    it.each(createExamActions())('should not send actions when monitoring disabled', (action: ExamAction) => {
        const spy = jest.spyOn(examMonitoringService, 'sendAction');
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        exam.monitoring = false;
        examMonitoringService.saveActions(exam, studentExam, true);
        expect(spy).not.toHaveBeenCalled();
    });

    it.each(createExamActions())('should remove actions after send when websocket connected', (action: ExamAction) => {
        const spy = jest.spyOn(examMonitoringService, 'sendAction');

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, true);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(action, exam.id);

        expect(studentExam.examActivity.examActions).toHaveLength(0);
    });

    it.each(createExamActions())('should not remove actions when websocket not connected', (action: ExamAction) => {
        const spy = jest.spyOn(examMonitoringService, 'sendAction');

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, false);
        expect(spy).not.toHaveBeenCalled();

        expect(studentExam.examActivity.examActions).toHaveLength(1);
    });

    it('should return the correct topic', () => {
        const spy = jest.spyOn(ExamMonitoringService, 'buildWebsocketTopic');
        const topic = ExamMonitoringService.buildWebsocketTopic(exam.id!);

        expect(spy).toHaveBeenCalledOnce();

        expect(topic).toEqual(`topic/exam-monitoring/${exam.id}/actions`);
    });

    it.each(createExamActions())('should call websocket send', (action: ExamAction) => {
        const spy = jest.spyOn(websocketService, 'send');

        examMonitoringService.sendAction(action, exam.id!);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(ExamMonitoringService.buildWebsocketTopic(exam.id!), action);
    });
});
