import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { TestcaseAnalysisComponent } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';

describe('TestcaseAnalysisComponent', () => {
    let component: TestcaseAnalysisComponent;
    let fixture: ComponentFixture<TestcaseAnalysisComponent>;
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
        {
            text: 'Test feedback 1',
            positive: false,
            detailText: 'Test feedback 1 detail',
            testCase: { testName: 'test1' } as ProgrammingExerciseTestCase,
        },
    ] as Feedback[];

    const tasksMock: ProgrammingExerciseTask[] = [
        { id: 1, taskName: 'Task 1', testCases: [{ testName: 'test1' } as ProgrammingExerciseTestCase] },
        { id: 2, taskName: 'Task 2', testCases: [{ testName: 'test2' } as ProgrammingExerciseTestCase] },
    ] as ProgrammingExerciseTask[];

    const feedbackDetailsResponseMock = new HttpResponse({
        body: { feedback: feedbackMock, participation: participationMock },
    });

    beforeEach(() => {
        const mockProgrammingExerciseTaskService = {
            exercise: { id: 1 }, // Mock the exercise with an id
            updateTasks: jest.fn().mockReturnValue(tasksMock),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot()],
            declarations: [TestcaseAnalysisComponent, MockComponent(ButtonComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                ResultService,
                { provide: ProgrammingExerciseTaskService, useValue: mockProgrammingExerciseTaskService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TestcaseAnalysisComponent);
        component = fixture.componentInstance;
        resultService = TestBed.inject(ResultService);

        jest.spyOn(resultService, 'getFeedbackDetailsForExercise').mockReturnValue(of(feedbackDetailsResponseMock));
    });

    it('should initialize and load feedback details correctly', () => {
        component.ngOnInit();
        fixture.detectChanges();

        expect(resultService.getFeedbackDetailsForExercise).toHaveBeenCalled();
        expect(component.participation).toEqual(participationMock);
        expect(component.feedback).toHaveLength(2);
        expect(component.feedback[0].detailText).toBe('Test feedback 1 detail');
        expect(component.feedback[1].detailText).toBe('Test feedback 2 detail');
    });

    it('should save feedbacks and sort them by count', () => {
        component.saveFeedback(feedbackMock);

        expect(component.feedback).toHaveLength(2);
        expect(component.feedback[0].count).toBe(2);
        expect(component.feedback[1].count).toBe(1);
        expect(component.feedback[0].detailText).toBe('Test feedback 1 detail');
    });

    it('should find task index for a given test case', () => {
        component.tasks = tasksMock;
        const index = component.findTaskIndexForTestCase({ testName: 'test1' } as ProgrammingExerciseTestCase);
        expect(index).toBe(1);

        const zeroIndex = component.findTaskIndexForTestCase({ testName: 'test3' } as ProgrammingExerciseTestCase);
        expect(zeroIndex).toBe(0);

        const undefinedIndex = component.findTaskIndexForTestCase(undefined);
        expect(undefinedIndex).toBe(-1);
    });

    it('should handle errors when loading feedback details', () => {
        jest.spyOn(resultService, 'getFeedbackDetailsForExercise').mockReturnValue(throwError('Error'));

        component.loadFeedbackDetails(1);

        expect(resultService.getFeedbackDetailsForExercise).toHaveBeenCalled();
        expect(component.feedback).toHaveLength(0);
    });
});
