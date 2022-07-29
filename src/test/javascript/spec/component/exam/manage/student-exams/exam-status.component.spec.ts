import { ArtemisTestModule } from '../../../../test.module';
import { ExamConductionState, ExamReviewState, ExamStatusComponent } from 'app/exam/manage/exam-status.component';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockExamChecklistService } from '../../../../helpers/mocks/service/mock-exam-checklist.service';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';

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
        testExam.maxPoints = 0;
        component.exam = exam;
    };

    const prepareForTestExamConductionStateTest = (startDate: dayjs.Dayjs, endDateOffset: number, offsetType: DateOffsetType) => {
        testExam.startDate = startDate;
        testExam.endDate = dayjs().add(endDateOffset, offsetType);
        testExam.maxPoints = 10;
        testExam.testExam = true;
        component.exam = testExam;
    };

    const prepareForExamReviewStateTest = (endDate: dayjs.Dayjs) => {
        exam.examStudentReviewEnd = endDate;
        component.exam = exam;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamStatusComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExamChecklistService, useClass: MockExamChecklistService },
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
        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.FINISHED);
    });

    it('should set examReviewState correctly if exam review phase is not defined', () => {
        component.exam = exam;

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.UNSET);
    });

    it('should set examReviewState correctly if exam review phase is currently running', () => {
        prepareForExamReviewStateTest(dayjs().add(3, DateOffsetType.HOURS));

        component.ngOnChanges();

        expect(component.examReviewState).toBe(ExamReviewState.RUNNING);

        exam.examStudentReviewStart = dayjs().add(-1, DateOffsetType.DAYS);
        component.exam = exam;

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
        component.exam = exam;
        component.course = { isAtLeastInstructor: true } as Course;

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

    it('should set examConductionState correctly if TestExam is started but not finished yet AND preparation is not finished', () => {
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished = false;
        component.ngOnChanges();

        expect(component.examConductionState).toBe(ExamConductionState.ERROR);
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
        component.course = { isAtLeastInstructor: true } as Course;

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
