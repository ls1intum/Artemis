import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackAnalysisComponent, FeedbackAnalysisState } from 'app/programming/manage/grading/feedback-analysis/feedback-analysis.component';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { FeedbackFilterModalComponent, FilterData } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-filter/feedback-filter-modal.component';
import { AffectedStudentsModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-affected-students/feedback-affected-students-modal.component';
import { FeedbackDetailChannelModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-detail-channel/feedback-detail-channel-modal.component';
import { Subject } from 'rxjs';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { AlertService } from 'app/shared/service/alert.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('FeedbackAnalysisComponent', () => {
    let fixture: ComponentFixture<FeedbackAnalysisComponent>;
    let component: FeedbackAnalysisComponent;
    let feedbackAnalysisService: FeedbackAnalysisService;
    let searchSpy: jest.SpyInstance;
    let localStorageService: LocalStorageService;
    let modalService: NgbModal;
    let alertService: AlertService;
    let modalSpy: jest.SpyInstance;
    let createChannelSpy: jest.SpyInstance;

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
            imports: [TranslateModule.forRoot(), FeedbackAnalysisComponent],
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
        modalService = fixture.debugElement.injector.get(NgbModal);
        alertService = fixture.debugElement.injector.get(AlertService);

        jest.spyOn(localStorageService, 'retrieve').mockReturnValue([]);
        searchSpy = jest.spyOn(feedbackAnalysisService, 'search').mockResolvedValue(feedbackResponseMock);
        const mockFormSubmitted = new Subject<{ channelDto: ChannelDTO; navigate: boolean }>();
        modalSpy = jest.spyOn(fixture.debugElement.injector.get(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                formSubmitted: mockFormSubmitted,
                affectedStudentsCount: null,
                feedbackDetail: null,
            },
            result: Promise.resolve(),
        } as any);

        createChannelSpy = jest.spyOn(feedbackAnalysisService, 'createChannel').mockResolvedValue({ id: 123 } as ChannelDTO);

        jest.spyOn(fixture.debugElement.injector.get(AlertService), 'success');
        jest.spyOn(fixture.debugElement.injector.get(AlertService), 'error');

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
        jest.restoreAllMocks();
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
            await component['loadData']();
            expect(searchSpy).toHaveBeenCalledTimes(2);
            expect(component.content().resultsOnPage).toEqual(feedbackMock);
            expect(component.totalItems()).toBe(2);
            expect(component.errorCategories()).toEqual(feedbackResponseMock.errorCategories);
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

    describe('openFilterModal', () => {
        it('should open filter modal and pass correct form values and properties', async () => {
            const modalService = fixture.debugElement.injector.get(NgbModal);
            const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: {
                    filterApplied: { subscribe: jest.fn() },
                },
            } as any);
            const getMaxCountSpy = jest.spyOn(feedbackAnalysisService, 'getMaxCount').mockResolvedValue(10);
            jest.spyOn(localStorageService, 'retrieve')
                .mockReturnValueOnce(['task1'])
                .mockReturnValueOnce(['testCase1'])
                .mockReturnValueOnce([component.minCount(), 5])
                .mockReturnValueOnce(['Student Error']);

            component.maxCount.set(5);
            component.selectedFiltersCount.set(1);
            await component.openFilterModal();

            expect(getMaxCountSpy).toHaveBeenCalledWith(1);
            expect(modalSpy).toHaveBeenCalledWith(FeedbackFilterModalComponent, { centered: true, size: 'lg' });
            const modalInstance = modalSpy.mock.results[0].value.componentInstance;
            expect(modalInstance.filters).toEqual({
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [component.minCount(), 5],
                errorCategories: ['Student Error'],
            });
            expect(modalInstance.taskArray).toBe(component.taskNames);
            expect(modalInstance.testCaseNames).toBe(component.testCaseNames);
            expect(modalInstance.exerciseId).toBe(component.exerciseId);
            expect(modalInstance.maxCount).toBe(component.maxCount);
        });

        it('should open filter modal and pass correct form values and properties when grouped feedback is active', async () => {
            const modalService = fixture.debugElement.injector.get(NgbModal);
            const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: {
                    filterApplied: { subscribe: jest.fn() },
                },
            } as any);
            jest.spyOn(localStorageService, 'retrieve')
                .mockReturnValueOnce(['task1'])
                .mockReturnValueOnce(['testCase1'])
                .mockReturnValueOnce([component.minCount(), 5])
                .mockReturnValueOnce(['Student Error']);

            component.maxCount.set(5);
            component.selectedFiltersCount.set(1);
            component.groupFeedback.set(true);
            await component.openFilterModal();

            expect(modalSpy).toHaveBeenCalledWith(FeedbackFilterModalComponent, { centered: true, size: 'lg' });
            const modalInstance = modalSpy.mock.results[0].value.componentInstance;
            expect(modalInstance.filters).toEqual({
                tasks: ['task1'],
                testCases: ['testCase1'],
                occurrence: [component.minCount(), 5],
                errorCategories: ['Student Error'],
            });
            expect(modalInstance.taskArray).toBe(component.taskNames);
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

    describe('openAffectedStudentsModal', () => {
        it('should open affected students modal with the correct feedback detail', () => {
            const modalService = fixture.debugElement.injector.get(NgbModal);
            const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({ componentInstance: {} } as any);

            const feedbackDetail = feedbackMock[1];
            component.openAffectedStudentsModal(feedbackDetail);

            expect(modalSpy).toHaveBeenCalledWith(AffectedStudentsModalComponent, { centered: true, size: 'lg' });
            expect(modalSpy).toHaveBeenCalledOnce();
        });
    });

    it('should open the feedback detail channel modal', async () => {
        const formSubmitted = new Subject<{ channelDto: ChannelDTO; navigate: boolean }>();
        const modalRef = {
            result: Promise.resolve('mocked result'),
            componentInstance: {
                formSubmitted,
                affectedStudentsCount: null,
                feedbackDetail: null,
            },
        } as any;
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        await component.openFeedbackDetailChannelModal(feedbackMock[0]);
        expect(modalService.open).toHaveBeenCalledWith(FeedbackDetailChannelModalComponent, { centered: true, size: 'lg' });
    });

    it('should handle errors during channel creation gracefully', async () => {
        const formSubmitted = new Subject<{ channelDto: ChannelDTO; navigate: boolean }>();
        const modalRef = {
            result: Promise.resolve('mocked result'),
            componentInstance: {
                formSubmitted,
                affectedStudentsCount: null,
                feedbackDetail: null,
            },
        } as any;
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        createChannelSpy.mockRejectedValue(new Error('Error creating channel'));
        await component.openFeedbackDetailChannelModal(feedbackMock[0]);
        formSubmitted.next({ channelDto: { name: 'Test Channel' } as ChannelDTO, navigate: true });
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should not proceed if modal is already open', async () => {
        component['isFeedbackDetailChannelModalOpen'] = true;
        const feedbackDetail = feedbackMock[0];
        await component.openFeedbackDetailChannelModal(feedbackDetail);
        expect(component['isFeedbackDetailChannelModalOpen']).toBeTrue();
        expect(modalSpy).not.toHaveBeenCalled();
    });

    describe('toggleGroupFeedback', () => {
        it('should toggle groupFeedback and call loadData', () => {
            const loadDataSpy = jest.spyOn(component, 'loadData' as any);
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
