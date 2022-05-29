import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { ExamMonitoringComponent, TableContent } from 'app/exam/monitoring/exam-monitoring.component';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import dayjs from 'dayjs/esm';

describe('Exam Monitoring Component', () => {
    // Course
    const course = new Course();
    course.id = 1;

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
    let pipe: ArtemisDatePipe;

    const route = { params: of({ courseId: course.id, examId: exam.id }) };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExamMonitoringComponent,
                ArtemisDatePipe,
                MockDirective(HasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: ActivatedRoute, useValue: route }, { provide: ArtemisDatePipe }],
        })
            .compileComponents()
            .then(() => {
                pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
                fixture = TestBed.createComponent(ExamMonitoringComponent);
                comp = fixture.componentInstance;
                examMonitoringService = TestBed.inject(ExamMonitoringService);
                examManagementService = TestBed.inject(ExamManagementService);
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

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
});
