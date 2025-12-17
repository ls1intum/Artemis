import { ExamConductionState, ExamReviewState, ExamStatusComponent } from 'app/exam/manage/exam-status/exam-status.component';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockExamChecklistService } from 'test/helpers/mocks/service/mock-exam-checklist.service';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

enum DateOffsetType {
    HOURS = 'hours',
    DAYS = 'days',
}

describe('ExamStatusComponent', () => {
    let fixture: ComponentFixture<ExamStatusComponent>;
    let component: ExamStatusComponent;
    let examChecklistService: ExamChecklistService;

    let getExamStatisticsStub: jest.SpyInstance;

    let calculateExercisePointsStub: jest.SpyInstance;

    let exam: Exam;

    let testExam: Exam;

    const prepareForExamConductionStateTest = (startDate: dayjs.Dayjs, endDateOffset: number, offsetType: DateOffsetType) => {
        exam.startDate = startDate;
        exam.endDate = dayjs().add(endDateOffset, offsetType);
        testExam.examMaxPoints = 0;

        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('course', {} as Course);
    };

    const prepareForTestExamConductionStateTest = (startDate: dayjs.Dayjs, endDateOffset: number, offsetType: DateOffsetType) => {
        testExam.startDate = startDate;
        testExam.endDate = dayjs().add(endDateOffset, offsetType);
        testExam.examMaxPoints = 10;
        testExam.testExam = true;
        fixture.componentRef.setInput('exam', testExam);
        fixture.componentRef.setInput('course', {} as Course);
    };

    const prepareForExamReviewStateTest = (endDate: dayjs.Dayjs) => {
        exam.examStudentReviewEnd = endDate;
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('course', {} as Course);
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExamStatusComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExamChecklistService, useClass: MockExamChecklistService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamStatusComponent);
                component = fixture.componentInstance;
                examChecklistService = TestBed.inject(ExamChecklistService);
            });

        exam = new Exam();
        testExam = new Exam();
    });

    it('should set examConductionState correctly if exam is started but not finished yet', () => {
        prepareForExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished = true;

        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.RUNNING);
    });

    it('should set examConductionState correctly if exam not started yet', () => {
        prepareForExamConductionStateTest(dayjs().add(1, DateOffsetType.DAYS), 2, DateOffsetType.DAYS);

        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.PLANNED);
    });

    it('should set examConductionState correctly if exam is finished', () => {
        prepareForExamConductionStateTest(dayjs().add(-2, DateOffsetType.DAYS), -1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished = true;
        const course = { isAtLeastInstructor: true } as Course;
        fixture.componentRef.setInput('course', course);
        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.FINISHED);
    });

    it('should set examReviewState correctly if exam review phase is not defined', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('course', {} as Course);

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.UNSET);
    });

    it('should set examReviewState correctly if exam review phase is currently running', () => {
        prepareForExamReviewStateTest(dayjs().add(3, DateOffsetType.HOURS));

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.RUNNING);

        exam.examStudentReviewStart = dayjs().add(-1, DateOffsetType.DAYS);
        fixture.componentRef.setInput('exam', exam);

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.RUNNING);
    });

    it('should set examReviewState correctly if exam review phase is finished', () => {
        prepareForExamReviewStateTest(dayjs().add(-1, DateOffsetType.DAYS));

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.FINISHED);
    });

    it('should set examReviewState correctly if exam review phase is defined in future', () => {
        exam.examStudentReviewStart = dayjs().add(4, DateOffsetType.DAYS);
        prepareForExamReviewStateTest(dayjs().add(5, DateOffsetType.DAYS));

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.PLANNED);
    });

    it('should set flags for exam preparation steps correctly', () => {
        const examChecklist = new ExamChecklist();
        examChecklist.allExamExercisesAllStudentsPrepared = true;
        examChecklist.numberOfGeneratedStudentExams = 42;
        getExamStatisticsStub = jest.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));
        fixture.componentRef.setInput('exam', exam);

        const course = { isAtLeastInstructor: true } as Course;
        fixture.componentRef.setInput('course', course);
        component.ngOnChanges();

        expect(component.configuredExercises).toBeTrue();
        expect(component.registeredStudents).toBeTrue();
        expect(component.generatedStudentExams).toBeTrue();
        expect(component.preparedExerciseStart).toBeTrue();
        expect(component.numberOfGeneratedStudentExams).toBe(42);
        expect(component.examPreparationFinished).toBeTrue();
        expect(component.mandatoryPreparationFinished).toBeTrue();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);

        examChecklist.numberOfGeneratedStudentExams = undefined;
        component.ngOnChanges();

        expect(component.numberOfGeneratedStudentExams).toBe(0);
    });

    it('should set examConductionState correctly if TestExam is started but not finished yet', () => {
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished = true;
        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.RUNNING);
    });

    it('should set examConductionState correctly if TestExam is started but not finished yet AND preparation is not finished AND user is editor', () => {
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished = false;
        component.ngOnChanges();
        // Editors and TAs have no access to the required data to determine, if the preparation is not yet finished -> use RUNNING in this case
        expect(component.examConductionState).toBe(ExamConductionState.RUNNING);
    });

    it('should set examConductionState correctly if TestExam not started yet', () => {
        prepareForTestExamConductionStateTest(dayjs().add(1, DateOffsetType.DAYS), 2, DateOffsetType.DAYS);

        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.PLANNED);
    });

    it('should set flags for TestExam preparation steps correctly', () => {
        const examChecklist = new ExamChecklist();
        examChecklist.allExamExercisesAllStudentsPrepared = undefined;
        examChecklist.numberOfGeneratedStudentExams = undefined;
        getExamStatisticsStub = jest.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));
        calculateExercisePointsStub = jest.spyOn(examChecklistService, 'calculateExercisePoints').mockReturnValue(10);
        prepareForTestExamConductionStateTest(dayjs().add(1, DateOffsetType.DAYS), 2, DateOffsetType.DAYS);
        const course = { isAtLeastInstructor: true } as Course;
        fixture.componentRef.setInput('course', course);

        component.ngOnChanges();

        expect(component.configuredExercises).toBeTrue();
        expect(component.registeredStudents).toBeFalse();
        expect(component.generatedStudentExams).toBeFalse();
        expect(component.preparedExerciseStart).toBeFalse();
        expect(component.numberOfGeneratedStudentExams).toBe(0);
        expect(component.examPreparationFinished).toBeTrue();
        expect(component.mandatoryPreparationFinished).toBeTrue();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(testExam);
        expect(calculateExercisePointsStub).toHaveBeenCalledWith(true, testExam);
    });
});
