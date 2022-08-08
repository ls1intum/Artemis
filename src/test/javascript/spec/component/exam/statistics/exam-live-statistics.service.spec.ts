import { TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActionType, ExamActivity } from 'app/entities/exam-user-activity.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { EXAM_LIVE_STATISTICS_UPDATE_URL, ExamLiveStatisticsService } from 'app/exam/statistics/exam-live-statistics.service';
import { createActions } from './exam-live-statistics-helper';
import * as Sentry from '@sentry/browser';
import { CaptureContext } from '@sentry/types';
import { BehaviorSubject, of } from 'rxjs';
import { ExamActionService } from 'app/exam/statistics/exam-action.service';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';

describe('examLiveStatisticsService', () => {
    let examLiveStatisticsService: ExamLiveStatisticsService;
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
                ExamLiveStatisticsService,
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: HttpClient, useClass: MockHttpService },
                ExamActionService,
            ],
        })
            .compileComponents()
            .then(() => {
                examLiveStatisticsService = TestBed.inject(ExamLiveStatisticsService);
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
        exam.liveStatistics = true;

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
    it('should handle exam actions when live statistics enabled', () => {
        const examActions = createActions();
        expect(examActions).toHaveLength(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            examLiveStatisticsService.handleActionEvent(studentExam, action, exam.liveStatistics!);
            expect(studentExam.examActivity?.examActions).toEqual([action]);
            studentExam.examActivity!.examActions = [];
        });
    });

    it('should not handle exam actions when live statistics disabled', () => {
        exam.liveStatistics = false;
        const examActions = createActions();
        expect(examActions).toHaveLength(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            examLiveStatisticsService.handleActionEvent(studentExam, action, exam.liveStatistics!);
            expect(studentExam.examActivity).toBeUndefined();
        });
    });

    it.each(createActions())('should send exception to sentry during invalid handle', (action: ExamAction) => {
        const error = new Error('Invalid');
        const spy = jest.spyOn(artemisServerDateService, 'now').mockImplementation(() => {
            throw error;
        });

        examLiveStatisticsService.handleActionEvent(studentExam, action, exam.liveStatistics!);
        expect(spy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledWith(error);
    });

    // Save actions
    it.each(createActions())('should send actions when live statistics enabled and websocket connected', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examLiveStatisticsService.saveActions(exam, studentExam, true);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(action, exam.id);
    });

    it.each(createActions())('should not send actions when live statistics disabled', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        exam.liveStatistics = false;
        examLiveStatisticsService.saveActions(exam, studentExam, true);
        expect(spy).not.toHaveBeenCalled();
    });

    it.each(createActions())('should remove actions after send when websocket connected', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examLiveStatisticsService.saveActions(exam, studentExam, true);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(action, exam.id);

        expect(studentExam.examActivity.examActions).toHaveLength(0);
    });

    it.each(createActions())('should not remove actions when websocket not connected', (action: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'sendAction');

        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions.push(action);
        examLiveStatisticsService.saveActions(exam, studentExam, false);
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
        examLiveStatisticsService.saveActions(exam, studentExam, true);

        expect(spy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledOnce();
        expect(captureExceptionSpy).toHaveBeenCalledWith(error);
    });

    // Handle and save action
    it.each(createActions())('should handle and save action', (action: ExamAction) => {
        const handleActionEventSpy = jest.spyOn(examLiveStatisticsService, 'handleActionEvent').mockImplementation(() => {});
        const saveActionsSpy = jest.spyOn(examLiveStatisticsService, 'saveActions').mockImplementation(() => {});

        examLiveStatisticsService.handleAndSaveActionEvent(exam, studentExam, action, true, true);

        expect(handleActionEventSpy).toHaveBeenCalledOnce();
        expect(handleActionEventSpy).toHaveBeenCalledWith(studentExam, action, exam.liveStatistics, undefined);
        expect(saveActionsSpy).toHaveBeenCalledOnce();
        expect(saveActionsSpy).toHaveBeenCalledWith(exam, studentExam, true);
    });

    // Notify subscribers
    it('should notify exam subscribers', () => {
        const examObservables = new Map<number, BehaviorSubject<Exam | undefined>>();
        expect(examLiveStatisticsService.examObservables).toEqual(examObservables);

        examLiveStatisticsService.notifyExamSubscribers(exam);

        examObservables.set(exam.id!, new BehaviorSubject(exam));

        expect(examLiveStatisticsService.examObservables).toEqual(examObservables);

        examLiveStatisticsService.notifyExamSubscribers(exam);

        examObservables.get(exam.id!)?.next(exam);
        expect(examLiveStatisticsService.examObservables).toEqual(examObservables);
    });

    it('should return exam behavior subject', () => {
        const examObservables = new Map<number, BehaviorSubject<Exam | undefined>>();
        expect(examLiveStatisticsService.examObservables).toEqual(examObservables);

        examLiveStatisticsService.getExamBehaviorSubject(exam.id!);

        expect(examLiveStatisticsService.getExamBehaviorSubject(exam.id!)).toBeUndefined();

        const subject = new BehaviorSubject(exam);
        examObservables.set(exam.id!, subject);

        examLiveStatisticsService.notifyExamSubscribers(exam);

        expect(examLiveStatisticsService.getExamBehaviorSubject(exam.id!)).toEqual(subject);
    });

    // Update live statistics
    it.each([true, false])('should update live statistics', (liveStatistics: boolean) => {
        exam.liveStatistics = !liveStatistics;
        const spy = jest.spyOn(http, 'put').mockReturnValue(of(liveStatistics));

        examLiveStatisticsService.updateExamLiveStatistics(exam, liveStatistics);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(EXAM_LIVE_STATISTICS_UPDATE_URL(course.id!, exam.id!), liveStatistics, { observe: 'response' });
    });

    // url
    it('should get correct exam live statistics update url', () => {
        expect(EXAM_LIVE_STATISTICS_UPDATE_URL(exam.course?.id!, exam.id!)).toBe(`${SERVER_API_URL}/api/courses/${exam.course?.id!}/exams/${exam.id!}/statistics`);
    });
});
