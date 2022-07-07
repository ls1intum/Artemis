import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { createActions } from './exam-monitoring-helper';
import { BehaviorSubject } from 'rxjs';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import dayjs from 'dayjs/esm';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';

describe('ExamActionService', () => {
    let examActionService: ExamActionService;
    let httpClient: HttpClient;
    let exam: Exam;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                ArtemisServerDateService,
                ExamActionService,
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: HttpClient, useClass: MockHttpService },
            ],
        })
            .compileComponents()
            .then(() => {
                examActionService = TestBed.inject(ExamActionService);
                httpClient = TestBed.inject(HttpClient);
            });
    });

    beforeEach(() => {
        // reset exam
        exam = new Exam();
        exam.id = 1;
        exam.monitoring = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    // Notify subscribers
    it.each(createActions())('should notify exam subscribers', (examAction: ExamAction) => {
        const spy = jest.spyOn(examActionService, 'prepareAction').mockImplementation((action) => action);

        const examActionObservables = new Map<number, BehaviorSubject<ExamAction | undefined>>();
        expect(examActionService.examActionObservables).toEqual(examActionObservables);

        examActionService.notifyExamActionSubscribers(exam, examAction);

        examActionObservables.set(exam.id!, new BehaviorSubject(examAction));

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(examAction);
        expect(examActionService.examActionObservables).toEqual(examActionObservables);

        examActionService.notifyExamActionSubscribers(exam, examAction);

        examActionObservables.get(exam.id!)?.next(examAction);
        expect(examActionService.examActionObservables).toEqual(examActionObservables);
    });

    // Notify exam monitoring update subscribers
    it.each([true, false])('should notify exam monitoring update subscribers', (status: boolean) => {
        const examMonitoringStatusObservables = new Map<number, BehaviorSubject<boolean>>();
        expect(examActionService.examMonitoringStatusObservables).toEqual(examMonitoringStatusObservables);

        examActionService.notifyExamMonitoringUpdateSubscribers(exam, status);

        examMonitoringStatusObservables.set(exam.id!, new BehaviorSubject(status));

        expect(examActionService.examMonitoringStatusObservables).toEqual(examMonitoringStatusObservables);
    });

    // Additional methods
    it.each(createActions())('should prepare action', (examAction: ExamAction) => {
        const now = dayjs().set('seconds', 8).set('ms', 0);
        examAction.timestamp = now;

        examActionService.prepareAction(examAction);

        expect(examAction.timestamp).toEqual(now);
        examAction.ceiledTimestamp!.set('ms', 0);
        expect(examAction.ceiledTimestamp).toEqual(now.set('seconds', 15));
    });

    it('should load initial actions', () => {
        const spy = jest.spyOn(httpClient, 'get');
        const initialActionsLoaded = new Map<number, boolean>();

        expect(examActionService.initialActionsLoaded).toEqual(initialActionsLoaded);

        examActionService.loadInitialActions(exam);

        initialActionsLoaded.set(exam.id!, true);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(`api/exam-monitoring/${exam.id}/load-actions`);
        expect(examActionService.initialActionsLoaded).toEqual(initialActionsLoaded);
    });
});
