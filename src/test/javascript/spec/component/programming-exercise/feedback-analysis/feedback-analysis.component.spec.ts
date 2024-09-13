import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { FeedbackAnalysisComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.component';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { signal } from '@angular/core';

describe('FeedbackAnalysisComponent', () => {
    let fixture: ComponentFixture<FeedbackAnalysisComponent>;
    let component: FeedbackAnalysisComponent;
    let feedbackAnalysisService: FeedbackAnalysisService;
    let searchSpy: jest.SpyInstance;

    const feedbackMock: FeedbackDetail[] = [
        { detailText: 'Test feedback 1 detail', testCaseName: 'test1', count: 10, relativeCount: 50, taskNumber: 1 },
        { detailText: 'Test feedback 2 detail', testCaseName: 'test2', count: 5, relativeCount: 25, taskNumber: 2 },
    ];

    const feedbackResponseMock: FeedbackAnalysisResponse = {
        feedbackDetails: { resultsOnPage: feedbackMock, numberOfPages: 1 },
        totalItems: 2,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), FeedbackAnalysisComponent],
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
        feedbackAnalysisService = fixture.debugElement.injector.get(FeedbackAnalysisService);
        searchSpy = jest.spyOn(feedbackAnalysisService, 'search').mockResolvedValue(feedbackResponseMock);

        (component.exerciseId as any) = signal<number>(1);
        (component.exerciseTitle as any) = signal<string>('Sample Exercise Title');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('on init', () => {
        it('should load data on initialization', async () => {
            await fixture.whenStable();
            expect(searchSpy).toHaveBeenCalled();
            expect(component.content().resultsOnPage).toEqual(feedbackMock);
            expect(component.totalItems()).toBe(2);
        });
    });

    describe('loadData', () => {
        it('should load feedback details and update state correctly', async () => {
            await component['loadData']();
            expect(searchSpy).toHaveBeenCalled();
            expect(component.content().resultsOnPage).toEqual(feedbackMock);
            expect(component.totalItems()).toBe(2);
        });

        it('should handle error while loading feedback details', async () => {
            searchSpy.mockRejectedValueOnce(new Error('Error loading feedback details'));

            try {
                await component['loadData']();
            } catch {
                expect(component.content().resultsOnPage).toEqual([]);
                expect(component.totalItems()).toBe(0);
            }
        });
    });

    describe('setPage', () => {
        it('should update page and reload data', async () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);

            component.setPage(2);
            expect(component.page()).toBe(2);
            expect(loadDataSpy).toHaveBeenCalled();
        });
    });

    describe('setSortedColumn', () => {
        it('should update sortedColumn and sortingOrder, and reload data', async () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);

            component.setSortedColumn('testCaseName');
            expect(component.sortedColumn()).toBe('testCaseName');
            expect(component.sortingOrder()).toBe('ASCENDING');
            expect(loadDataSpy).toHaveBeenCalled();

            component.setSortedColumn('testCaseName');
            expect(component.sortingOrder()).toBe('DESCENDING');
            expect(loadDataSpy).toHaveBeenCalled();
        });
    });

    describe('search', () => {
        it('should reset page and load data when searching', async () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);

            component.searchTerm.set('test');
            component.search();
            expect(component.page()).toBe(1);
            expect(loadDataSpy).toHaveBeenCalled();
        });
    });

    describe('openFeedbackModal', () => {
        it('should open feedback modal with correct feedback detail', () => {
            const modalService = fixture.debugElement.injector.get(NgbModal);
            const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({ componentInstance: {} } as any);

            const feedbackDetail = feedbackMock[0];
            component.openFeedbackModal(feedbackDetail);

            expect(modalSpy).toHaveBeenCalled();
        });
    });
});
