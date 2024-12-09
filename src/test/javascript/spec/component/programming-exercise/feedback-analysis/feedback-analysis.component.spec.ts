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
import { AffectedStudentsModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-affected-students-modal.component';
import { FeedbackDetailChannelModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-detail-channel-modal.component';
import { Subject } from 'rxjs';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { AlertService } from 'app/core/util/alert.service';

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
            concatenatedFeedbackIds: [1, 2],
            detailText: 'Test feedback 1 detail',
            testCaseName: 'test1',
            count: 10,
            relativeCount: 50,
            taskName: '1',
            errorCategory: 'Student Error',
        },
        {
            concatenatedFeedbackIds: [3, 4],
            detailText: 'Test feedback 2 detail',
            testCaseName: 'test2',
            count: 5,
            relativeCount: 25,
            taskName: '2',
            errorCategory: 'AST Error',
        },
    ];

    const feedbackResponseMock: FeedbackAnalysisResponse = {
        feedbackDetails: { resultsOnPage: feedbackMock, numberOfPages: 1 },
        totalItems: 2,
        taskNames: ['task1', 'task2'],
        testCaseNames: ['test1', 'test2'],
        errorCategories: ['Student Error', 'AST Error', 'Ares Error'],
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
        modalService = fixture.debugElement.injector.get(NgbModal);
        alertService = fixture.debugElement.injector.get(AlertService);

        jest.spyOn(localStorageService, 'retrieve').mockReturnValue([]);
        searchSpy = jest.spyOn(feedbackAnalysisService, 'search').mockResolvedValue(feedbackResponseMock);
        const mockFormSubmitted = new Subject<{ channelDto: ChannelDTO; navigate: boolean }>();
        modalSpy = jest.spyOn(TestBed.inject(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                formSubmitted: mockFormSubmitted,
                affectedStudentsCount: null,
                feedbackDetail: null,
            },
            result: Promise.resolve(),
        } as any);

        jest.spyOn(feedbackAnalysisService, 'getAffectedStudentCount').mockResolvedValue(10);
        createChannelSpy = jest.spyOn(feedbackAnalysisService, 'createChannel').mockResolvedValue({ id: 123 } as ChannelDTO);

        jest.spyOn(fixture.debugElement.injector.get(AlertService), 'success');
        jest.spyOn(fixture.debugElement.injector.get(AlertService), 'error');

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
                occurrence: [component.minCount(), component.maxCount()],
                errorCategories: ['AST Error'],
            };
            const count = component.countAppliedFilters(filters);

            expect(count).toBe(4);
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
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should not proceed if modal is already open', async () => {
        component['isFeedbackDetailChannelModalOpen'] = true;
        const feedbackDetail = feedbackMock[0];
        await component.openFeedbackDetailChannelModal(feedbackDetail);
        expect(component['isFeedbackDetailChannelModalOpen']).toBeTrue();
        expect(modalSpy).not.toHaveBeenCalled();
    });
});
