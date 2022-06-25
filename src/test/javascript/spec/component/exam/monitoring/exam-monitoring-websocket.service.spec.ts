import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { createActions } from './exam-monitoring-helper';
import { BehaviorSubject } from 'rxjs';
import { ExamMonitoringWebsocketService } from 'app/exam/monitoring/exam-monitoring-websocket.service';
import dayjs from 'dayjs/esm';

describe('ExamMonitoringWebsocketService', () => {
    let examMonitoringWebsocketService: ExamMonitoringWebsocketService;
    let exam: Exam;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [ArtemisServerDateService, ExamMonitoringWebsocketService, { provide: JhiWebsocketService, useClass: MockWebsocketService }],
        })
            .compileComponents()
            .then(() => {
                examMonitoringWebsocketService = TestBed.inject(ExamMonitoringWebsocketService);
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

    it.each(createActions())('should notify exam subscribers', (examAction: ExamAction) => {
        const spy = jest.spyOn(examMonitoringWebsocketService, 'prepareAction').mockImplementation((action) => action);

        const examActionObservables = new Map<number, BehaviorSubject<ExamAction | undefined>>();
        expect(examMonitoringWebsocketService.examActionObservables).toEqual(examActionObservables);

        examMonitoringWebsocketService.notifyExamActionSubscribers(exam, examAction);

        examActionObservables.set(exam.id!, new BehaviorSubject(examAction));

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(examAction);
        expect(examMonitoringWebsocketService.examActionObservables).toEqual(examActionObservables);

        examMonitoringWebsocketService.notifyExamActionSubscribers(exam, examAction);

        examActionObservables.get(exam.id!)?.next(examAction);
        expect(examMonitoringWebsocketService.examActionObservables).toEqual(examActionObservables);
    });

    it.each(createActions())('should prepare action', (examAction: ExamAction) => {
        const now = dayjs().set('seconds', 8).set('ms', 0);
        examAction.timestamp = now;

        examMonitoringWebsocketService.prepareAction(examAction);

        expect(examAction.timestamp).toEqual(now);
        examAction.ceiledTimestamp!.set('ms', 0);
        expect(examAction.ceiledTimestamp).toEqual(now.set('seconds', 15));
    });
});
