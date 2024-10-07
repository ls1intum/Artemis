import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { FeedbackAnalysisComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.component';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'ngx-webstorage';
import '@angular/localize/init';
import { FeedbackFilterModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';

describe('FeedbackAnalysisComponent', () => {
    let fixture: ComponentFixture<FeedbackAnalysisComponent>;
    let component: FeedbackAnalysisComponent;
    let feedbackAnalysisService: FeedbackAnalysisService;
    let searchSpy: jest.SpyInstance;
    let localStorageService: LocalStorageService;

    const feedbackMock: FeedbackDetail[] = [
        { detailText: 'Test feedback 1 detail', testCaseName: 'test1', count: 10, relativeCount: 50, taskNumber: '1', errorCategory: 'StudentError' },
        { detailText: 'Test feedback 2 detail', testCaseName: 'test2', count: 5, relativeCount: 25, taskNumber: '2', errorCategory: 'StudentError' },
    ];

    const feedbackResponseMock: FeedbackAnalysisResponse = {
        feedbackDetails: { resultsOnPage: feedbackMock, numberOfPages: 1 },
        totalItems: 2,
        totalAmountOfTasks: 1,
        testCaseNames: ['test1', 'test2'],
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
                LocalStorageService,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackAnalysisComponent);
        component = fixture.componentInstance;
        feedbackAnalysisService = fixture.debugElement.injector.get(FeedbackAnalysisService);
        localStorageService = fixture.debugElement.injector.get(LocalStorageService);

        jest.spyOn(localStorageService, 'retrieve').mockReturnValue([]);

        searchSpy = jest.spyOn(feedbackAnalysisService, 'search').mockResolvedValue(feedbackResponseMock);

        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('exerciseTitle', 'Sample Exercise Title');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('on init', () => {
        it('should load data on initialization', async () => {
            await fixture.whenStable();
            expect(searchSpy).toHaveBeenCalledOnce();
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
            expect(loadDataSpy).toHaveBeenCalledOnce();
        });
    });

    describe('setSortedColumn', () => {
        it('should update sortedColumn and sortingOrder, and reload data', async () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);

            component.setSortedColumn('testCaseName');
            expect(component.sortedColumn()).toBe('testCaseName');
            expect(component.sortingOrder()).toBe('ASCENDING');
            expect(loadDataSpy).toHaveBeenCalledOnce();

            component.setSortedColumn('testCaseName');
            expect(component.sortingOrder()).toBe('DESCENDING');
            expect(loadDataSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('search', () => {
        beforeEach(() => {
            jest.spyOn(component, 'debounceLoadData' as any).mockImplementation(() => {
                component['loadData']();
            });
        });

        it('should reset page and load data when searching', async () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);
            component.searchTerm.set('test');
            await component.search(component.searchTerm());
            expect(component.page()).toBe(1);
            expect(loadDataSpy).toHaveBeenCalledOnce();
        });
    });

    describe('openFeedbackModal', () => {
        it('should open feedback modal with correct feedback detail', () => {
            const modalService = fixture.debugElement.injector.get(NgbModal);
            const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({ componentInstance: {} } as any);

            const feedbackDetail = feedbackMock[0];
            component.openFeedbackModal(feedbackDetail);

            expect(modalSpy).toHaveBeenCalledOnce();
        });
    });

    describe('openFilterModal', () => {
        it('should open filter modal and pass correct form values and properties', async () => {
            const modalService = fixture.debugElement.injector.get(NgbModal);
            const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: {
                    filterForm: { setValue: jest.fn() },
                    filterApplied: { subscribe: jest.fn() },
                },
            } as any);
            const getMaxCountSpy = jest.spyOn(feedbackAnalysisService, 'getMaxCount').mockResolvedValue(10);
            component.hasAppliedFilters = true;

            jest.spyOn(localStorageService, 'retrieve').mockReturnValueOnce(['task1']).mockReturnValueOnce(['testCase1']).mockReturnValueOnce([1, 5]);

            await component.openFilterModal();

            expect(getMaxCountSpy).toHaveBeenCalledWith(1);
            expect(modalSpy).toHaveBeenCalledWith(FeedbackFilterModalComponent, { centered: true, size: 'lg' });
            const modalInstance = modalSpy.mock.results[0].value.componentInstance;
            expect(modalInstance.filterForm.setValue).toHaveBeenCalledWith({
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [1, 5],
            });
            expect(modalInstance.totalAmountOfTasks).toBe(component.totalAmountOfTasks);
            expect(modalInstance.testCaseNames).toBe(component.testCaseNames);
            expect(modalInstance.exerciseId).toBe(component.exerciseId);
            expect(modalInstance.maxCount).toBe(component.maxCount);
        });
    });

    describe('applyFilters', () => {
        it('should apply filters, update filter count, and reload data', () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);
            const countAppliedFiltersSpy = jest.spyOn(component, 'countAppliedFilters').mockReturnValue(2);

            const filters = {
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [1, 10],
            };

            component.applyFilters(filters);
            expect(countAppliedFiltersSpy).toHaveBeenCalledWith(filters);
            expect(component.selectedFiltersCount()).toBe(2);
            expect(component.hasAppliedFilters).toBeTrue();
            expect(loadDataSpy).toHaveBeenCalled();
        });
    });

    describe('countAppliedFilters', () => {
        it('should count the applied filters correctly', () => {
            const filters = {
                tasks: ['task1', 'task2'],
                testCases: ['testCase1'],
                occurrence: [1, 10],
            };

            const count = component.countAppliedFilters(filters);

            expect(count).toBe(3);
        });

        it('should return 0 if no filters are applied', () => {
            const filters = {
                tasks: [],
                testCases: [],
                occurrence: [1, component.maxCount()],
            };
            const count = component.countAppliedFilters(filters);
            expect(count).toBe(0);
        });
    });
});
