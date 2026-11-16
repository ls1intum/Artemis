import { ExamConductionState, ExamReviewState, ExamStatusComponent } from 'app/exam/manage/exam-status/exam-status.component';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockExamChecklistService } from 'test/helpers/mocks/service/mock-exam-checklist.service';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { Subject, of } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

enum DateOffsetType {
    HOURS = 'hours',
    DAYS = 'days',
}

describe('ExamStatusComponent', () => {
    let fixture: ComponentFixture<ExamStatusComponent>;
    let component: ExamStatusComponent;
    let examChecklistService: ExamChecklistService;

    let getExamStatisticsStub: ReturnType<typeof vi.spyOn>;

    let calculateExercisePointsStub: ReturnType<typeof vi.spyOn>;

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamStatusComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExamChecklistService, useClass: MockExamChecklistService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStatusComponent);
        component = fixture.componentInstance;
        examChecklistService = TestBed.inject(ExamChecklistService);

        exam = new Exam();
        testExam = new Exam();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set examConductionState correctly if exam is started but not finished yet', () => {
        prepareForExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished.set(true);

        fixture.detectChanges();

        expect(component.examConductionState()).toBe(ExamConductionState.RUNNING);
    });

    it('should set examConductionState correctly if exam not started yet', () => {
        prepareForExamConductionStateTest(dayjs().add(1, DateOffsetType.DAYS), 2, DateOffsetType.DAYS);

        fixture.detectChanges();

        expect(component.examConductionState()).toBe(ExamConductionState.PLANNED);
    });

    it('should set examConductionState correctly if exam is finished', () => {
        prepareForExamConductionStateTest(dayjs().add(-2, DateOffsetType.DAYS), -1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished.set(true);
        const course = { isAtLeastInstructor: true } as Course;
        fixture.componentRef.setInput('course', course);
        fixture.detectChanges();

        expect(component.examConductionState()).toBe(ExamConductionState.FINISHED);
    });

    it('should set examReviewState correctly if exam review phase is not defined', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('course', {} as Course);

        fixture.detectChanges();

        expect(component.examReviewState()).toBe(ExamReviewState.UNSET);
    });

    it('should set examReviewState correctly if exam review phase is currently running', () => {
        prepareForExamReviewStateTest(dayjs().add(3, DateOffsetType.HOURS));

        fixture.detectChanges();

        expect(component.examReviewState()).toBe(ExamReviewState.RUNNING);

        exam.examStudentReviewStart = dayjs().add(-1, DateOffsetType.DAYS);
        fixture.componentRef.setInput('exam', exam);

        fixture.detectChanges();

        expect(component.examReviewState()).toBe(ExamReviewState.RUNNING);
    });

    it('should set examReviewState correctly if exam review phase is finished', () => {
        prepareForExamReviewStateTest(dayjs().add(-1, DateOffsetType.DAYS));

        fixture.detectChanges();

        expect(component.examReviewState()).toBe(ExamReviewState.FINISHED);
    });

    it('should set examReviewState correctly if exam review phase is defined in future', () => {
        exam.examStudentReviewStart = dayjs().add(4, DateOffsetType.DAYS);
        prepareForExamReviewStateTest(dayjs().add(5, DateOffsetType.DAYS));

        fixture.detectChanges();

        expect(component.examReviewState()).toBe(ExamReviewState.PLANNED);
    });

    it('should set flags for exam preparation steps correctly', () => {
        const examChecklist = new ExamChecklist();
        examChecklist.allExamExercisesAllStudentsPrepared = true;
        examChecklist.numberOfGeneratedStudentExams = 42;
        getExamStatisticsStub = vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));
        fixture.componentRef.setInput('exam', exam);

        const course = { isAtLeastInstructor: true } as Course;
        fixture.componentRef.setInput('course', course);
        fixture.detectChanges();

        expect(component.configuredExercises()).toBe(true);
        expect(component.registeredStudents()).toBe(true);
        expect(component.generatedStudentExams()).toBe(true);
        expect(component.preparedExerciseStart()).toBe(true);
        expect(component.numberOfGeneratedStudentExams()).toBe(42);
        expect(component.examPreparationFinished()).toBe(true);
        expect(component.mandatoryPreparationFinished()).toBe(true);
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);

        examChecklist.numberOfGeneratedStudentExams = undefined;
        // Force the effect to re-run by re-setting the input reference.
        fixture.componentRef.setInput('exam', { ...exam });
        fixture.detectChanges();

        expect(component.numberOfGeneratedStudentExams()).toBe(0);
    });

    it.each([
        { description: 'already generated on the coverpage', initialGenerated: 1, generatedAfterStart: 1 },
        { description: 'created after the initial statistics', initialGenerated: 0, generatedAfterStart: 2 },
    ])('should use the authoritative total for a test exam $description', ({ initialGenerated, generatedAfterStart }) => {
        const initialStats = Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: initialGenerated, numberOfExamsStarted: 0, numberOfExamsSubmitted: 0 });
        const startedStats = Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: generatedAfterStart, numberOfExamsStarted: 0, numberOfExamsSubmitted: 0 });
        vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValueOnce(of(initialStats)).mockReturnValue(of(startedStats));
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        testExam.id = 7;
        fixture.detectChanges();
        const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
        expect(component.numberOfGeneratedStudentExams()).toBe(initialGenerated);

        websocketService.emit('/topic/exam/7/started', undefined);
        fixture.detectChanges();

        expect(component.numberOfStarted()).toBe(1);
        expect(component.numberOfGeneratedStudentExams()).toBe(generatedAfterStart);
    });

    it('should cancel superseded test-exam statistics and pending requests on destroy', () => {
        const initialStats = Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: 1, numberOfExamsStarted: 0, numberOfExamsSubmitted: 0 });
        const earlierRefresh = new Subject<ExamChecklist>();
        const latestRefresh = new Subject<ExamChecklist>();
        const destroyedRefresh = new Subject<ExamChecklist>();
        vi.spyOn(examChecklistService, 'getExamStatistics')
            .mockReturnValueOnce(of(initialStats))
            .mockReturnValueOnce(earlierRefresh)
            .mockReturnValueOnce(latestRefresh)
            .mockReturnValue(destroyedRefresh);
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        testExam.id = 7;
        fixture.detectChanges();
        const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;

        websocketService.emit('/topic/exam/7/started', undefined);
        expect(earlierRefresh.observed).toBe(true);
        websocketService.emit('/topic/exam/7/started', undefined);
        expect(earlierRefresh.observed).toBe(false);
        websocketService.emit('/topic/exam/7/submitted', undefined);
        // The event is published before the started state is persisted, so the server can still report zero starts.
        latestRefresh.next(Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: 3, numberOfExamsStarted: 0, numberOfExamsSubmitted: 0 }));
        earlierRefresh.next(initialStats);
        expect(component.numberOfGeneratedStudentExams()).toBe(3);
        expect(component.numberOfStarted()).toBe(2);
        expect(component.numberOfSubmitted()).toBe(1);

        websocketService.emit('/topic/exam/7/started', undefined);
        expect(destroyedRefresh.observed).toBe(true);
        fixture.destroy();
        expect(destroyedRefresh.observed).toBe(false);
        destroyedRefresh.next(initialStats);
        expect(component.numberOfGeneratedStudentExams()).toBe(3);
    });

    it('should keep the number of generated student exams of a regular exam when a student starts', () => {
        const examChecklist = Object.assign(new ExamChecklist(), { numberOfGeneratedStudentExams: 12, numberOfExamsStarted: 3, numberOfExamsSubmitted: 0 });
        vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));
        prepareForExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        exam.id = 7;
        fixture.detectChanges();
        const websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;

        websocketService.emit('/topic/exam/7/started', undefined);

        expect(component.numberOfStarted()).toBe(4);
        expect(component.numberOfGeneratedStudentExams()).toBe(12);
        expect(examChecklistService.getExamStatistics).toHaveBeenCalledExactlyOnceWith(exam);
    });

    it('should set examConductionState correctly if TestExam is started but not finished yet', () => {
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished.set(true);
        fixture.detectChanges();

        expect(component.examConductionState()).toBe(ExamConductionState.RUNNING);
    });

    it('should set examConductionState correctly if TestExam is started but not finished yet AND preparation is not finished AND user is editor', () => {
        prepareForTestExamConductionStateTest(dayjs().add(-1, DateOffsetType.HOURS), 1, DateOffsetType.DAYS);
        component.mandatoryPreparationFinished.set(false);
        fixture.detectChanges();
        // Editors and TAs have no access to the required data to determine, if the preparation is not yet finished -> use RUNNING in this case
        expect(component.examConductionState()).toBe(ExamConductionState.RUNNING);
    });

    it('should set examConductionState correctly if TestExam not started yet', () => {
        prepareForTestExamConductionStateTest(dayjs().add(1, DateOffsetType.DAYS), 2, DateOffsetType.DAYS);

        fixture.detectChanges();

        expect(component.examConductionState()).toBe(ExamConductionState.PLANNED);
    });

    it('should set flags for TestExam preparation steps correctly', () => {
        const examChecklist = new ExamChecklist();
        examChecklist.allExamExercisesAllStudentsPrepared = undefined;
        examChecklist.numberOfGeneratedStudentExams = undefined;
        getExamStatisticsStub = vi.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));
        calculateExercisePointsStub = vi.spyOn(examChecklistService, 'calculateExercisePoints').mockReturnValue(10);
        prepareForTestExamConductionStateTest(dayjs().add(1, DateOffsetType.DAYS), 2, DateOffsetType.DAYS);
        const course = { isAtLeastInstructor: true } as Course;
        fixture.componentRef.setInput('course', course);

        fixture.detectChanges();

        expect(component.configuredExercises()).toBe(true);
        expect(component.registeredStudents()).toBe(false);
        expect(component.generatedStudentExams()).toBe(false);
        expect(component.preparedExerciseStart()).toBe(false);
        expect(component.numberOfGeneratedStudentExams()).toBe(0);
        expect(component.examPreparationFinished()).toBe(true);
        expect(component.mandatoryPreparationFinished()).toBe(true);
        expect(getExamStatisticsStub).toHaveBeenCalledWith(testExam);
        expect(calculateExercisePointsStub).toHaveBeenCalledWith(true, testExam);
    });
});
