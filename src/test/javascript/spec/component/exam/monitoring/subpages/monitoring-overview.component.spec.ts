import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { MonitoringOverviewComponent } from 'app/exam/monitoring/subpages/overview/monitoring-overview.component';
import { BehaviorSubject, of } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { ActivatedRoute } from '@angular/router';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExamMonitoringWebsocketService } from 'app/exam/monitoring/exam-monitoring-websocket.service';
import { EndedExamAction } from 'app/entities/exam-user-activity.model';

describe('Monitoring Overview Component', () => {
    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;

    let comp: MonitoringOverviewComponent;
    let fixture: ComponentFixture<MonitoringOverviewComponent>;
    let examMonitoringService: ExamMonitoringService;
    let examMonitoringWebsocketService: ExamMonitoringWebsocketService;

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MonitoringOverviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonitoringOverviewComponent);
                comp = fixture.componentInstance;
                examMonitoringService = TestBed.inject(ExamMonitoringService);
                examMonitoringWebsocketService = TestBed.inject(ExamMonitoringWebsocketService);
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

    it('should call subscribeForLatestExamAction of examMonitoringWebsocketService to get the latest actions on init', () => {
        // GIVEN
        jest.spyOn(examMonitoringService, 'getExamBehaviorSubject').mockReturnValue(new BehaviorSubject(exam));
        const action = new EndedExamAction();
        jest.spyOn(examMonitoringWebsocketService, 'subscribeForLatestExamAction').mockReturnValue(new BehaviorSubject(action));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examMonitoringWebsocketService.subscribeForLatestExamAction).toHaveBeenCalledOnce();
        expect(examMonitoringWebsocketService.subscribeForLatestExamAction).toHaveBeenCalledWith(exam);
        expect(comp.examActions).toEqual([action]);
    });
});
