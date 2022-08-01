import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { LiveStatisticsOverviewComponent } from 'app/exam/statistics/subpages/overview/live-statistics-overview.component';
import { BehaviorSubject, of } from 'rxjs';
import { ExamLiveStatisticsService } from 'app/exam/statistics/exam-live-statistics.service';
import { ActivatedRoute } from '@angular/router';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

describe('Live Statistics Overview Component', () => {
    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;

    let comp: LiveStatisticsOverviewComponent;
    let fixture: ComponentFixture<LiveStatisticsOverviewComponent>;
    let examLiveStatisticsService: ExamLiveStatisticsService;

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LiveStatisticsOverviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LiveStatisticsOverviewComponent);
                comp = fixture.componentInstance;
                examLiveStatisticsService = TestBed.inject(ExamLiveStatisticsService);
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should call getExamBehaviorSubject of examLiveStatisticsService to get the exam on init', () => {
        // GIVEN
        jest.spyOn(examLiveStatisticsService, 'getExamBehaviorSubject').mockReturnValue(new BehaviorSubject(exam));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examLiveStatisticsService.getExamBehaviorSubject).toHaveBeenCalledOnce();
        expect(examLiveStatisticsService.getExamBehaviorSubject).toHaveBeenCalledWith(exam.id);
        expect(comp.exam).toEqual(exam);
    });
});
