import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { BehaviorSubject, of } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { ActivatedRoute } from '@angular/router';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MonitoringActivityLogComponent } from 'app/exam/monitoring/subpages/activity-log/monitoring-activity-log.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { MockHttpService } from '../../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MonitoringCardComponent } from 'app/exam/monitoring/subpages/monitoring-card.component';
import { TotalActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/total-actions-chart.component';
import { AverageActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/average-actions-chart.component';
import { CategoryActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/category-actions-chart.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

describe('Monitoring Activity Log Component', () => {
    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;

    let comp: MonitoringActivityLogComponent;
    let fixture: ComponentFixture<MonitoringActivityLogComponent>;
    let examMonitoringService: ExamMonitoringService;

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                MonitoringActivityLogComponent,
                MonitoringCardComponent,
                TotalActionsChartComponent,
                AverageActionsChartComponent,
                CategoryActionsChartComponent,
                DataTableComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: HttpClient, useClass: MockHttpService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonitoringActivityLogComponent);
                comp = fixture.componentInstance;
                examMonitoringService = TestBed.inject(ExamMonitoringService);
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should call getExamBehaviorSubject of examMonitoringService to get the exam on init', () => {
        // GIVEN
        jest.spyOn(examMonitoringService, 'getExamBehaviorSubject').mockReturnValue(new BehaviorSubject(exam));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examMonitoringService.getExamBehaviorSubject).toHaveBeenCalledOnce();
        expect(examMonitoringService.getExamBehaviorSubject).toHaveBeenCalledWith(exam.id);
        expect(comp.exam).toEqual(exam);
    });
});
