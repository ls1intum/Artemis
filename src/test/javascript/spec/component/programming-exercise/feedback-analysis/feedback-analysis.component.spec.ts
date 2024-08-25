import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { FeedbackAnalysisComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.component';
import { FeedbackAnalysisService } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';

describe('FeedbackAnalysisComponent', () => {
    let fixture: ComponentFixture<FeedbackAnalysisComponent>;
    let component: FeedbackAnalysisComponent;
    let feedbackAnalysisService: FeedbackAnalysisService;
    let getFeedbackDetailsSpy: jest.SpyInstance;

    const feedbackMock: FeedbackDetail[] = [
        { detailText: 'Test feedback 1 detail', testCaseName: 'test1', count: 10, relativeCount: 50, taskNumber: 1 },
        { detailText: 'Test feedback 2 detail', testCaseName: 'test2', count: 5, relativeCount: 25, taskNumber: 2 },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), FeedbackAnalysisComponent],
            declarations: [],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                FeedbackAnalysisService,
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(FeedbackAnalysisComponent);
        component = fixture.componentInstance;
        component.exerciseId = 1;
        feedbackAnalysisService = fixture.debugElement.injector.get(FeedbackAnalysisService);
        getFeedbackDetailsSpy = jest.spyOn(feedbackAnalysisService, 'getFeedbackDetailsForExercise').mockResolvedValue(feedbackMock);
    });

    describe('ngOnInit', () => {
        it('should call loadFeedbackDetails when exerciseId is provided', async () => {
            component.ngOnInit();
            await fixture.whenStable();

            expect(getFeedbackDetailsSpy).toHaveBeenCalledWith(1);
            expect(component.feedbackDetails).toEqual(feedbackMock);
        });
    });

    describe('loadFeedbackDetails', () => {
        it('should load feedback details and update the component state', async () => {
            await component.loadFeedbackDetails(1);
            expect(component.feedbackDetails).toEqual(feedbackMock);
        });

        it('should handle error while loading feedback details', async () => {
            getFeedbackDetailsSpy.mockRejectedValue(new Error('Error loading feedback details'));

            try {
                await component.loadFeedbackDetails(1);
            } catch {
                expect(component.feedbackDetails).toEqual([]);
            }
        });
    });
});
