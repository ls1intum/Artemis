import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';

import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { createActions, createSingleSeriesDataEntriesWithTimestamps } from '../exam-monitoring-helper';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { TotalActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/total-actions-chart.component';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

class MockExamActionService {
    cachedExamActionsGroupedByTimestamp = new Map();
}

describe('Total Actions Chart Comp', () => {
    let comp: TotalActionsChartComponent;
    let examActionService: ExamActionService;

    const course = new Course();
    course.id = 1;
    const exam = new Exam();
    exam.id = 1;
    const now = dayjs();
    let ceiledNow: dayjs.Dayjs;
    let pipe: ArtemisDatePipe;

    beforeEach(() => {
        const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

        TestBed.configureTestingModule({
            providers: [
                TotalActionsChartComponent,
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ExamActionService, useClass: MockExamActionService },
            ],
        });

        comp = TestBed.inject(TotalActionsChartComponent);
        examActionService = TestBed.inject(ExamActionService);
        ceiledNow = ceilDayjsSeconds(now, comp.timeStampGapInSeconds);
        pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
    });

    it('should call initData on init without actions', () => {
        expect(comp.ngxData).toEqual([]);

        const series = createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe);

        comp.ngOnInit();

        expect(comp.ngxData).toEqual([{ name: 'actions', series }]);
    });

    it('should call initData on init with actions', () => {
        const series = createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe);

        const length = createActions().length;
        const groupedByTimestamp = new Map();
        groupedByTimestamp.set(ceiledNow.toString(), length);
        examActionService.cachedExamActionsGroupedByTimestamp!.set(exam.id!, groupedByTimestamp);

        series.filter((data) => data.name === pipe.transform(ceiledNow, 'time', true).toString())[0].value = length;

        comp.ngOnInit();

        expect(comp.ngxData).toEqual([{ name: 'actions', series }]);
    });
});
