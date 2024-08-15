import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { TestcaseAnalysisComponent } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.component';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';

describe('TestcaseAnalysisComponent', () => {
    let component: TestcaseAnalysisComponent;
    let fixture: ComponentFixture<TestcaseAnalysisComponent>;
    let participationService: ParticipationService;
    let resultService: ResultService;

    const participationMock: Participation[] = [
        {
            id: 1,
            results: [{ id: 1 }],
        },
    ] as Participation[];

    const feedbackMock: Feedback[] = [
        {
            text: 'Test feedback 1',
            positive: false,
            detailText: 'Test feedback 1 detail',
            testCase: { testName: 'test1' } as ProgrammingExerciseTestCase,
        },
        {
            text: 'Test feedback 2',
            positive: false,
            detailText: 'Test feedback 2 detail',
            testCase: { testName: 'test2' } as ProgrammingExerciseTestCase,
        },
    ] as Feedback[];

    const tasksMock: ProgrammingExerciseTask[] = [
        { id: 1, taskName: 'Task 1', testCases: [{ testName: 'test1' } as ProgrammingExerciseTestCase] },
        { id: 2, taskName: 'Task 2', testCases: [{ testName: 'test2' } as ProgrammingExerciseTestCase] },
    ] as ProgrammingExerciseTask[];

    const participationResponseMock = new HttpResponse({ body: participationMock });
    const feedbackResponseMock = new HttpResponse({ body: feedbackMock });

    beforeEach(() => {
        const mockProgrammingExerciseTaskService = {
            exercise: { id: 1 }, // Mock the exercise with an id
            updateTasks: jest.fn().mockReturnValue(tasksMock),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot()],
            declarations: [TestcaseAnalysisComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                ParticipationService,
                ResultService,
                { provide: ProgrammingExerciseTaskService, useValue: mockProgrammingExerciseTaskService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TestcaseAnalysisComponent);
        component = fixture.componentInstance;
        participationService = TestBed.inject(ParticipationService);
        resultService = TestBed.inject(ResultService);

        jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(participationResponseMock));
        jest.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of(feedbackResponseMock));
    });

    it('should initialize and load feedbacks correctly', () => {
        component.ngOnInit();
        fixture.detectChanges();

        expect(participationService.findAllParticipationsByExercise).toHaveBeenCalled();
        expect(resultService.getFeedbackDetailsForResult).toHaveBeenCalled();
        expect(component.participation).toEqual(participationMock);
        expect(component.feedbacks).toHaveLength(2);
        expect(component.feedbacks[0].detailText).toBe('Test feedback 1 detail');
    });

    it('should save feedbacks and sort them by count', () => {
        component.saveFeedbacks(feedbackMock);

        expect(component.feedbacks).toHaveLength(2);
        expect(component.feedbacks[0].count).toBe(1);
        expect(component.feedbacks[1].count).toBe(1);
        expect(component.feedbacks[0].detailText).toBe('Test feedback 1 detail');
    });

    it('should find task index for a given test case', () => {
        component.tasks = tasksMock;
        const index = component.findTaskIndexForTestCase({ testName: 'test1' } as ProgrammingExerciseTestCase);
        expect(index).toBe(1);

        const zeroIndex = component.findTaskIndexForTestCase({ testName: 'test3' } as ProgrammingExerciseTestCase);
        expect(zeroIndex).toBe(0);

        const undefinedIndex = component.findTaskIndexForTestCase({ testName: undefined } as ProgrammingExerciseTestCase);
        expect(undefinedIndex).toBe(0);
    });

    it('should calculate relative count correctly', () => {
        component.participation = participationMock;
        const relativeCount = component.getRelativeCount(1);
        expect(relativeCount).toBe(100);

        const zeroRelativeCount = component.getRelativeCount(0);
        expect(zeroRelativeCount).toBe(0);
    });
});
