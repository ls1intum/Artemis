import { TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActionType, ExamActivity } from 'app/entities/exam-user-activity.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { EXAM_MONITORING_UPDATE_URL, ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { createActions } from './exam-monitoring-helper';
import * as Sentry from '@sentry/browser';
import { CaptureContext } from '@sentry/types';
import { BehaviorSubject, of } from 'rxjs';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';

describe('ExamMonitoringService', () => {
    let examMonitoringService: ExamMonitoringService;
    let examActionService: ExamActionService;
    let artemisServerDateService: ArtemisServerDateService;
    let http: HttpClient;
    let captureExceptionSpy: jest.SpyInstance<string, [exception: any, captureContext?: CaptureContext | undefined]>;
    let exam: Exam;
    let studentExam: StudentExam;
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                ArtemisServerDateService,
                ExamMonitoringService,
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: HttpClient, useClass: MockHttpService },
                ExamActionService,
            ],
        })
            .compileComponents()
            .then(() => {
                examMonitoringService = TestBed.inject(ExamMonitoringService);
                examActionService = TestBed.inject(ExamActionService);
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
                http = TestBed.inject(HttpClient);
                captureExceptionSpy = jest.spyOn(Sentry, 'captureException');
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

        // Set exam course
        exam.course = course;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    // Handle action event
    it('should handle exam actions when monitoring enabled', () => {
        const examActions = createActions();
        expect(examActions).toHaveLength(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            examMonitoringService.handleActionEvent(studentExam, action, exam.monitoring!);
            expect(studentExam.examActivity?.examActions).toEqual([action]);
            studentExam.examActivity!.examActions = [];
        });
    });

    it('should not handle exam actions when monitoring disabled', () => {
        exam.monitoring = false;
        const examActions = createActions();
        expect(examActions).toHaveLength(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            examMonitoringService.handleActionEvent(studentExam, action, exam.monitoring!);
            expect(studentExam.examActivity).toBeUndefined();
        });
    });

    it.each(createActions())('should send exception to sentry during invalid handle', (action: ExamAction) => {
        const error = new Error('Invalid');
        const spy = jest.spyOn(artemisServerDateService, 'now').mockImplementation(() => {
            throw error;
        });

        examMonitoringService.handleActionEvent(studentExam, action, exam.monitoring!);
        expect(spy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledWith(error);
    });

    // Save actions
    it.each(createActions())('should send actions when monitoring enabled and websocket connected', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, true);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(action, exam.id);
    });

    it.each(createActions())('should not send actions when monitoring disabled', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        exam.monitoring = false;
        examMonitoringService.saveActions(exam, studentExam, true);
        expect(spy).not.toHaveBeenCalled();
    });

    it.each(createActions())('should remove actions after send when websocket connected', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, true);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(action, exam.id);

        expect(studentExam.examActivity.examActions).toHaveLength(0);
    });

    it.each(createActions())('should not remove actions when websocket not connected', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, false);
        expect(spy).not.toHaveBeenCalled();

        expect(studentExam.examActivity.examActions).toHaveLength(1);
    });

    it.each(createActions())('should send exception to sentry during invalid save', (action: ExamAction) => {
        const error = new Error('Invalid');
        const spy = jest.spyOn(examActionService, 'sendAction').mockImplementation(() => {
            throw error;
        });

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examMonitoringService.saveActions(exam, studentExam, true);

        expect(spy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledWith(error);
    });

    // Handle and save action
    it.each(createActions())('should handle and save action', (action: ExamAction) => {
        const handleActionEventSpy = jest.spyOn(examMonitoringService, 'handleActionEvent').mockImplementation(() => {});
        const saveActionsSpy = jest.spyOn(examMonitoringService, 'saveActions').mockImplementation(() => {});

        examMonitoringService.handleAndSaveActionEvent(exam, studentExam, action, true);

        expect(handleActionEventSpy).toHaveBeenCalledOnce();
        expect(handleActionEventSpy).toHaveBeenCalledWith(studentExam, action, exam.monitoring, undefined);
        expect(saveActionsSpy).toHaveBeenCalledOnce();
        expect(saveActionsSpy).toHaveBeenCalledWith(exam, studentExam, true);
    });

    // Notify subscribers
    it('should notify exam subscribers', () => {
        const examObservables = new Map<number, BehaviorSubject<Exam | undefined>>();
        expect(examMonitoringService.examObservables).toEqual(examObservables);

        examMonitoringService.notifyExamSubscribers(exam);

        examObservables.set(exam.id!, new BehaviorSubject(exam));

        expect(examMonitoringService.examObservables).toEqual(examObservables);

        examMonitoringService.notifyExamSubscribers(exam);

        examObservables.get(exam.id!)?.next(exam);
        expect(examMonitoringService.examObservables).toEqual(examObservables);
    });

    it('should return exam behavior subject', () => {
        const examObservables = new Map<number, BehaviorSubject<Exam | undefined>>();
        expect(examMonitoringService.examObservables).toEqual(examObservables);

        examMonitoringService.getExamBehaviorSubject(exam.id!);

        expect(examMonitoringService.getExamBehaviorSubject(exam.id!)).toEqual(undefined);

        const subject = new BehaviorSubject(exam);
        examObservables.set(exam.id!, subject);

        examMonitoringService.notifyExamSubscribers(exam);

        expect(examMonitoringService.getExamBehaviorSubject(exam.id!)).toEqual(subject);
    });

    // Update monitoring
    it.each([true, false])('should update monitoring', (monitoring: boolean) => {
        exam.monitoring = !monitoring;
        const spy = jest.spyOn(http, 'put').mockReturnValue(of(monitoring));

        examMonitoringService.updateMonitoring(exam, monitoring);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(EXAM_MONITORING_UPDATE_URL(course.id!, exam.id!), monitoring, { observe: 'response' });
    });

    // url
    it('should get correct exam monitoring update url', () => {
        expect(EXAM_MONITORING_UPDATE_URL(exam.course?.id!, exam.id!)).toEqual(`${SERVER_API_URL}/api/courses/${exam.course?.id!}/exams/${exam.id!}/statistics`);
    });
});
