import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackAnalysisComponent, FeedbackAnalysisState } from 'app/programming/manage/grading/feedback-analysis/feedback-analysis.component';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { FeedbackFilterModalComponent, FilterData } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-filter/feedback-filter-modal.component';
import { FeedbackDetailChannelModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-detail-channel/feedback-detail-channel-modal.component';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

/**
 * Typed view onto the private members the spec drives directly, so the tests can read/invoke
 * them without a blanket `(component as any)` cast. The shape mirrors the component declaration.
 */
type ComponentInternalsOverrides = {
    loadData(): Promise<void>;
    debounceLoadData(): void;
    isFeedbackDetailChannelModalOpen: boolean;
};
type ComponentInternals = Omit<FeedbackAnalysisComponent, keyof ComponentInternalsOverrides> & ComponentInternalsOverrides;
const internals = (c: FeedbackAnalysisComponent): ComponentInternals => c as unknown as ComponentInternals;

describe('FeedbackAnalysisComponent', () => {
    let fixture: ComponentFixture<FeedbackAnalysisComponent>;
    let component: FeedbackAnalysisComponent;
    let feedbackAnalysisService: FeedbackAnalysisService;
    let searchSpy: ReturnType<typeof vi.spyOn>;
    let localStorageService: LocalStorageService;
    let alertService: AlertService;
    let createChannelSpy: ReturnType<typeof vi.spyOn>;

    const feedbackMock: FeedbackDetail[] = [
        {
            feedbackIds: [1],
            detailTexts: ['Test feedback 1 detail'],
            testCaseName: 'test1',
            count: 10,
            relativeCount: 50,
            taskName: '1',
            errorCategory: 'Student Error',
            hasLongFeedbackText: false,
        },
        {
            feedbackIds: [2],
            detailTexts: ['Test feedback 2 detail'],
            testCaseName: 'test2',
            count: 5,
            relativeCount: 25,
            taskName: '2',
            errorCategory: 'AST Error',
            hasLongFeedbackText: false,
        },
    ];

    const feedbackResponseMock: FeedbackAnalysisResponse = {
        feedbackDetails: { resultsOnPage: feedbackMock, numberOfPages: 1 },
        totalItems: 2,
        taskNames: ['task1', 'task2'],
        testCaseNames: ['test1', 'test2'],
        errorCategories: ['Student Error', 'Ares Error', 'AST Error'],
        highestOccurrenceOfGroupedFeedback: 0,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackAnalysisComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                FeedbackAnalysisService,
                LocalStorageService,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackAnalysisComponent);
        component = fixture.componentInstance;
        feedbackAnalysisService = fixture.debugElement.injector.get(FeedbackAnalysisService);
        localStorageService = fixture.debugElement.injector.get(LocalStorageService);
        alertService = fixture.debugElement.injector.get(AlertService);

        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([]);
        searchSpy = vi.spyOn(feedbackAnalysisService, 'search').mockResolvedValue(feedbackResponseMock);
        createChannelSpy = vi.spyOn(feedbackAnalysisService, 'createChannel').mockResolvedValue({ id: 123 } as ChannelDTO);

        vi.spyOn(fixture.debugElement.injector.get(AlertService), 'success');
        vi.spyOn(fixture.debugElement.injector.get(AlertService), 'error');

        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('exerciseTitle', 'Sample Exercise Title');
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('isCommunicationEnabled', true);
        const previousState: FeedbackAnalysisState = {
            page: 1,
            pageSize: 25,
            searchTerm: '',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: 'count',
            filterErrorCategories: ['Student Error', 'Ares Error', 'AST Error'],
        };
        fixture.componentRef.instance.previousRequestState.set(previousState);
        const previousFilters: FilterData = {
            tasks: [],
            testCases: [],
            occurrence: [],
            errorCategories: [],
        };
        fixture.componentRef.instance.previousRequestFilters.set(previousFilters);
        fixture.componentRef.instance.previousRequestGroupFeedback.set(false);

        fixture.changeDetectorRef.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('on init', () => {
        it('should load data on initialization', async () => {
            await fixture.whenStable();
            expect(searchSpy).toHaveBeenCalledOnce();
            expect(component.content().resultsOnPage).toEqual(feedbackMock);
            expect(component.totalItems()).toBe(2);
            expect(component.errorCategories()).toEqual(feedbackResponseMock.errorCategories);
        });
    });

    describe('loadData', () => {
        it('should load feedback details and update state correctly', async () => {
            await internals(component).loadData();
            expect(searchSpy).toHaveBeenCalledTimes(2);
            expect(component.content().resultsOnPage).toEqual(feedbackMock);
            expect(component.totalItems()).toBe(2);
            expect(component.errorCategories()).toEqual(feedbackResponseMock.errorCategories);
        });

        it('should handle error while loading feedback details', async () => {
            searchSpy.mockRejectedValueOnce(new Error('Error loading feedback details'));

            try {
                await internals(component).loadData();
            } catch {
                expect(component.content().resultsOnPage).toEqual([]);
                expect(component.totalItems()).toBe(0);
            }
        });
    });

    describe('openFilterModal', () => {
        it('should open filter modal and pass correct form values and properties', async () => {
            const getMaxCountSpy = vi.spyOn(feedbackAnalysisService, 'getMaxCount').mockResolvedValue(10);
            vi.spyOn(localStorageService, 'retrieve')
                .mockReturnValueOnce(['task1'])
                .mockReturnValueOnce(['testCase1'])
                .mockReturnValueOnce([component.minCount(), 5])
                .mockReturnValueOnce(['Student Error']);

            component.maxCount.set(5);
            component.selectedFiltersCount.set(1);
            await component.openFilterModal();

            expect(getMaxCountSpy).toHaveBeenCalledWith(1);
            expect(component.filterModalFilters()).toEqual({
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [component.minCount(), 5],
                errorCategories: ['Student Error'],
            });

            fixture.detectChanges();
            const filterModal = fixture.debugElement.query(By.directive(FeedbackFilterModalComponent)).componentInstance as FeedbackFilterModalComponent;
            expect(filterModal.taskArray()).toBe(component.taskNames());
            expect(filterModal.testCaseNames()).toBe(component.testCaseNames());
            expect(filterModal.maxCount()).toBe(component.maxCount());
            expect(filterModal.filters).toBe(component.filterModalFilters());

            filterModal.closeModal();
            fixture.detectChanges();
            expect(component.filterModalFilters()).toBeUndefined();
        });

        it('should open filter modal and pass correct form values and properties when grouped feedback is active', async () => {
            vi.spyOn(localStorageService, 'retrieve')
                .mockReturnValueOnce(['task1'])
                .mockReturnValueOnce(['testCase1'])
                .mockReturnValueOnce([component.minCount(), 5])
                .mockReturnValueOnce(['Student Error']);

            component.maxCount.set(5);
            component.selectedFiltersCount.set(1);
            component.groupFeedback.set(true);
            await component.openFilterModal();

            expect(component.filterModalFilters()).toEqual({
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [component.minCount(), 5],
                errorCategories: ['Student Error'],
            });

            fixture.detectChanges();
            const filterModal = fixture.debugElement.query(By.directive(FeedbackFilterModalComponent)).componentInstance as FeedbackFilterModalComponent;
            expect(filterModal.taskArray()).toBe(component.taskNames());
            expect(filterModal.testCaseNames()).toBe(component.testCaseNames());
            expect(filterModal.maxCount()).toBe(component.maxCount());
            expect(filterModal.filters).toBe(component.filterModalFilters());

            filterModal.closeModal();
            fixture.detectChanges();
            expect(component.filterModalFilters()).toBeUndefined();
        });
    });

    describe('applyFilters', () => {
        it('should apply filters, update filter count, and reload data', () => {
            const loadDataSpy = vi.spyOn(internals(component), 'loadData');
            const countAppliedFiltersSpy = vi.spyOn(component, 'countAppliedFilters').mockReturnValue(2);

            const filters = {
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [component.minCount(), 10],
                errorCategories: ['Student Error'],
            };

            component.applyFilters(filters);
            expect(countAppliedFiltersSpy).toHaveBeenCalledWith(filters);
            expect(component.selectedFiltersCount()).toBe(2);
            expect(loadDataSpy).toHaveBeenCalledOnce();
        });
    });

    describe('countAppliedFilters', () => {
        it('should count the applied filters correctly', () => {
            component.maxCount.set(10);
            const filters = {
                tasks: ['task1', 'task2'],
                testCases: ['testCase1'],
                occurrence: [component.minCount(), component.maxCount() - 1],
                errorCategories: ['AST Error'],
            };
            const count = component.countAppliedFilters(filters);

            expect(count).toBe(5);
        });

        it('should return 0 if no filters are applied', () => {
            const filters = {
                tasks: [],
                testCases: [],
                occurrence: [component.minCount(), component.maxCount()],
                errorCategories: [],
            };
            const count = component.countAppliedFilters(filters);
            expect(count).toBe(0);
        });
    });

    describe('setPage', () => {
        it('should update page and reload data', async () => {
            const loadDataSpy = vi.spyOn(internals(component), 'loadData');

            component.setPage(2);
            expect(component.page()).toBe(2);
            expect(loadDataSpy).toHaveBeenCalledOnce();
        });

        it('onPageChange converts the 0-indexed paginator page to a 1-indexed page', () => {
            const setPageSpy = vi.spyOn(component, 'setPage');
            component.onPageChange(1);
            expect(setPageSpy).toHaveBeenCalledWith(2);
        });

        it('onPageSizeChange stores the page size and returns to the first page', () => {
            const setPageSpy = vi.spyOn(component, 'setPage');
            component.onPageSizeChange(50);
            expect(component.pageSize()).toBe(50);
            expect(setPageSpy).toHaveBeenCalledWith(1);
        });
    });

    it('should expose sorting as native buttons and announce the active sort direction', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const header: HTMLTableCellElement = fixture.nativeElement.querySelector('thead th');
        const sortButton = header.querySelector('button');
        expect(sortButton?.type).toBe('button');
        expect(header.getAttribute('aria-sort')).toBe('descending');
        expect(fixture.nativeElement.querySelectorAll('thead th[scope="col"]')).toHaveLength(6);

        sortButton!.click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(header.getAttribute('aria-sort')).toBe('ascending');
    });

    describe('onTableSort', () => {
        it('should update sortedColumn and sortingOrder, and reload data', async () => {
            const loadDataSpy = vi.spyOn(internals(component), 'loadData');

            component.onTableSort({ field: 'testCaseName', order: 1 });
            expect(component.sortedColumn()).toBe('testCaseName');
            expect(component.sortingOrder()).toBe('ASCENDING');
            expect(loadDataSpy).toHaveBeenCalledOnce();

            component.onTableSort({ field: 'testCaseName', order: -1 });
            expect(component.sortingOrder()).toBe('DESCENDING');
            expect(loadDataSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('onTableSort without a field', () => {
        it('should ignore a sort event that names no column', () => {
            const loadDataSpy = vi.spyOn(internals(component), 'loadData');
            const sortedColumn = component.sortedColumn();
            const sortingOrder = component.sortingOrder();

            component.onTableSort({ field: '', order: 1 });

            expect(component.sortedColumn()).toBe(sortedColumn);
            expect(component.sortingOrder()).toBe(sortingOrder);
            expect(loadDataSpy).not.toHaveBeenCalled();
        });
    });

    describe('search', () => {
        beforeEach(() => {
            vi.spyOn(internals(component), 'debounceLoadData').mockImplementation(() => {
                internals(component).loadData();
            });
        });

        it('should reset page and load data when searching', async () => {
            const loadDataSpy = vi.spyOn(internals(component), 'loadData');
            component.searchTerm.set('test');
            await component.search(component.searchTerm());
            expect(component.page()).toBe(1);
            expect(loadDataSpy).toHaveBeenCalledOnce();
        });
    });

    describe('openFeedbackModal', () => {
        it('should open feedback modal with correct feedback detail', () => {
            component.openFeedbackModal(feedbackMock[0]);

            expect(component.feedbackModalDetail()).toBe(feedbackMock[0]);
        });
    });

    describe('openAffectedStudentsModal', () => {
        it('should open affected students modal with the correct feedback detail', () => {
            component.openAffectedStudentsModal(feedbackMock[1]);

            expect(component.affectedStudentsModalDetail()).toBe(feedbackMock[1]);
        });
    });

    it('should open the feedback detail channel modal and remove it once it is closed', () => {
        component.openFeedbackDetailChannelModal(feedbackMock[0]);
        fixture.detectChanges();

        const channelModal = fixture.debugElement.query(By.directive(FeedbackDetailChannelModalComponent)).componentInstance as FeedbackDetailChannelModalComponent;
        expect(channelModal.feedbackDetail()).toBe(feedbackMock[0]);

        channelModal.closeModal();
        fixture.detectChanges();
        expect(component.channelModalDetail()).toBeUndefined();
        expect(fixture.debugElement.query(By.directive(FeedbackDetailChannelModalComponent))).toBeNull();
    });

    it('should create the channel the dialog submits', async () => {
        component.openFeedbackDetailChannelModal(feedbackMock[0]);
        fixture.detectChanges();
        const channelModal = fixture.debugElement.query(By.directive(FeedbackDetailChannelModalComponent)).componentInstance as FeedbackDetailChannelModalComponent;

        channelModal.formSubmitted.emit({ channelDto: { name: 'Test Channel' } as ChannelDTO, navigate: false });
        await fixture.whenStable();

        expect(createChannelSpy).toHaveBeenCalledExactlyOnceWith(1, 1, {
            channel: { name: 'Test Channel' },
            feedbackDetailTexts: feedbackMock[0].detailTexts,
            testCaseName: feedbackMock[0].testCaseName,
        });
        expect(alertService.success).toHaveBeenCalledOnce();
    });

    it('should handle errors during channel creation gracefully', async () => {
        createChannelSpy.mockRejectedValue(new Error('Error creating channel'));
        await component.createFeedbackChannel(feedbackMock[0], { channelDto: { name: 'Test Channel' } as ChannelDTO, navigate: true });
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should not replace the feedback detail if the channel modal is already open', () => {
        component.openFeedbackDetailChannelModal(feedbackMock[0]);
        component.openFeedbackDetailChannelModal(feedbackMock[1]);
        expect(component.channelModalDetail()).toBe(feedbackMock[0]);
    });

    describe('toggleGroupFeedback', () => {
        it('should toggle groupFeedback and call loadData', () => {
            const loadDataSpy = vi.spyOn(internals(component), 'loadData');
            const initialGroupFeedback = component.groupFeedback();

            component.toggleGroupFeedback();

            expect(component.groupFeedback()).toBe(!initialGroupFeedback);
            expect(loadDataSpy).toHaveBeenCalledOnce();
        });
    });

    describe('updateCache', () => {
        it('should restore cache if no new response is provided', () => {
            component.previousResponseData.set(feedbackResponseMock);

            const previousState: FeedbackAnalysisState = {
                page: 1,
                pageSize: 25,
                searchTerm: '',
                sortingOrder: SortingOrder.DESCENDING,
                sortedColumn: 'count',
                filterErrorCategories: ['Student Error', 'Ares Error', 'AST Error'],
            };
            component.previousRequestState.set(previousState);

            const previousFilters: FilterData = {
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [0, 10],
                errorCategories: ['Student Error'],
            };
            component.previousRequestFilters.set(previousFilters);
            component.previousRequestGroupFeedback.set(false);

            component.updateCache(component.previousResponseData()!, component.previousRequestState()!, component.previousRequestFilters()!);

            expect(component.content().resultsOnPage).toEqual(feedbackResponseMock.feedbackDetails.resultsOnPage);
            expect(component.totalItems()).toBe(feedbackResponseMock.totalItems);
            expect(component.taskNames()).toEqual(feedbackResponseMock.taskNames);
            expect(component.testCaseNames()).toEqual(feedbackResponseMock.testCaseNames);
            expect(component.errorCategories()).toEqual(feedbackResponseMock.errorCategories);
            expect(component.maxCount()).toBe(feedbackResponseMock.highestOccurrenceOfGroupedFeedback);

            expect(component.previousResponseData()).toEqual(component.currentResponseData());
            expect(component.previousRequestState()).toEqual(component.currentRequestState());
            expect(previousFilters).toEqual(component.currentRequestFilters());
            expect(component.previousRequestGroupFeedback()).toEqual(component.currentRequestGroupFeedback());
        });
    });
});
