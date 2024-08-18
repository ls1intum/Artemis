import { ComponentFixture, TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { TestcaseAnalysisComponent } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.component';
import { TestcaseAnalysisService } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.service';
import { HttpResponse } from '@angular/common/http';

describe('TestcaseAnalysisComponent', () => {
    let fixture: ComponentFixture<TestcaseAnalysisComponent>;
    let component: TestcaseAnalysisComponent;
    let testcaseAnalysisService: TestcaseAnalysisService;
    let getSimplifiedTasksSpy: jest.SpyInstance;
    let getFeedbackDetailsSpy: jest.SpyInstance;

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
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), TestcaseAnalysisComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                TestcaseAnalysisService,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestcaseAnalysisComponent);
                component = fixture.componentInstance;
                component.exerciseId = 1;

                testcaseAnalysisService = fixture.debugElement.injector.get(TestcaseAnalysisService);

                getSimplifiedTasksSpy = jest.spyOn(testcaseAnalysisService, 'getSimplifiedTasks').mockReturnValue(of(tasksMock));
                getFeedbackDetailsSpy = jest.spyOn(testcaseAnalysisService, 'getFeedbackDetailsForExercise').mockReturnValue(of(feedbackDetailsResponseMock));
            });
    });

    describe('ngOnInit', () => {
        it('should call loadTasks and loadFeedbackDetails when exerciseId is provided', () => {
            component.ngOnInit();
            fixture.whenStable();

            expect(getSimplifiedTasksSpy).toHaveBeenCalledWith(1);
            expect(getFeedbackDetailsSpy).toHaveBeenCalledWith(1);
            expect(component.tasks).toEqual(tasksMock);
            expect(component.resultIds).toEqual([1, 2]);
        });

        it('should not call loadTasks and loadFeedbackDetails if exerciseId is not provided', () => {
            component.exerciseId = undefined;
            component.ngOnInit();

            expect(getSimplifiedTasksSpy).not.toHaveBeenCalled();
            expect(getFeedbackDetailsSpy).not.toHaveBeenCalled();
        });
    });

    describe('loadTasks', () => {
        it('should load tasks and update the component state', () => {
            firstValueFrom(component.loadTasks(1));
            expect(component.tasks).toEqual(tasksMock);
        });

        it('should handle error while loading tasks', () => {
            getSimplifiedTasksSpy.mockReturnValue(throwError(() => new Error('Error loading tasks')));

            try {
                firstValueFrom(component.loadTasks(1));
            } catch {
                expect(component.tasks).toEqual([]);
            }
        });
    });

    describe('loadFeedbackDetails', () => {
        it('should load feedback details and update the component state', () => {
            firstValueFrom(component.loadFeedbackDetails(1));
            expect(component.resultIds).toEqual([1, 2]);
            expect(component.feedback).toHaveLength(2);
        });

        it('should handle error while loading feedback details', () => {
            getFeedbackDetailsSpy.mockReturnValue(throwError(() => new Error('Error loading feedback details')));

            try {
                firstValueFrom(component.loadFeedbackDetails(1));
            } catch {
                expect(component.feedback).toEqual([]);
                expect(component.resultIds).toEqual([]);
            }
        });
    });

    describe('saveFeedback', () => {
        it('should save feedbacks and sort them by count', () => {
            component.saveFeedback(feedbackMock);

            expect(component.feedback).toHaveLength(2);
            expect(component.feedback[0].count).toBe(2);
            expect(component.feedback[1].count).toBe(1);
        });
    });

    describe('taskIndex', () => {
        it('should find the correct task index for a given test case', () => {
            component.tasks = tasksMock;

            const index1 = component.taskIndex('test1');
            expect(index1).toBe(1);

            const index2 = component.taskIndex('test2');
            expect(index2).toBe(2);

            const nonExistingIndex = component.taskIndex('non-existing');
            expect(nonExistingIndex).toBe(0);
        });

        it('should return -1 if testCaseName is not provided', () => {
            const index = component.taskIndex('');
            expect(index).toBe(0);
        });
    });
});
