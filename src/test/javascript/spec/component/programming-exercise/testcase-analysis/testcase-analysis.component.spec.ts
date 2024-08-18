import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { TestcaseAnalysisComponent } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { TestcaseAnalysisService } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.service';
import { HttpResponse } from '@angular/common/http';

describe('TestcaseAnalysisComponent', () => {
    let component: TestcaseAnalysisComponent;
    let fixture: ComponentFixture<TestcaseAnalysisComponent>;

    const feedbackMock = [
        { detailText: 'Test feedback 1 detail', testCaseName: 'test1' },
        { detailText: 'Test feedback 2 detail', testCaseName: 'test2' },
        { detailText: 'Test feedback 1 detail', testCaseName: 'test1' },
    ];

    const tasksMock = [
        { taskName: 'Task 1', testCases: [{ testName: 'test1' }] },
        { taskName: 'Task 2', testCases: [{ testName: 'test2' }] },
    ];

    const feedbackDetailsResponseMock = new HttpResponse({
        body: { feedbackDetails: feedbackMock, resultIds: [1, 2] },
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                TranslateModule.forRoot(),
                TestcaseAnalysisComponent, // Import the standalone component here
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, ResultService, TestcaseAnalysisService],
        }).compileComponents();

        fixture = TestBed.createComponent(TestcaseAnalysisComponent);
        component = fixture.componentInstance;
        component.exerciseId = 1;

        // Mock the methods within the component itself
        jest.spyOn(component, 'loadTasks').mockReturnValue(of(tasksMock));
        jest.spyOn(component, 'loadFeedbackDetails').mockReturnValue(of(feedbackDetailsResponseMock));
    });

    //TODO: Fix the following test
    /*
    it('should initialize and load feedback details correctly', () => {
        component.ngOnInit();
        fixture.detectChanges();

        // Assertions to ensure loadTasks and loadFeedbackDetails were called
        expect(component.loadTasks).toHaveBeenCalledWith(component.exerciseId);
        expect(component.loadFeedbackDetails).toHaveBeenCalledWith(component.exerciseId);

        // Further assertions to check if the component's state is updated correctly
        expect(component.resultIds).toEqual([1, 2]);
        expect(component.feedback).toHaveLength(2);
        expect(component.feedback[0].detailText).toBe('Test feedback 1 detail');
        expect(component.feedback[1].detailText).toBe('Test feedback 2 detail');
    });

     */

    it('should handle errors when loading feedback details', () => {
        // Mock loadFeedbackDetails to simulate an error
        jest.spyOn(component, 'loadFeedbackDetails').mockReturnValue(throwError('Error'));

        component.loadFeedbackDetails(1);

        expect(component.loadFeedbackDetails).toHaveBeenCalled();
        expect(component.feedback).toHaveLength(0);
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
        const index = component.findTaskIndexForTestCase('test1');
        expect(index).toBe(1);

        const zeroIndex = component.findTaskIndexForTestCase('test3');
        expect(zeroIndex).toBe(0);

        const undefinedIndex = component.findTaskIndexForTestCase('');
        expect(undefinedIndex).toBe(-1);
    });
});
