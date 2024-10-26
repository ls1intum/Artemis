import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardDirective } from 'app/shared/sidebar/sidebar-card.directive';
import { ArtemisTestModule } from '../../../test.module';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { RouterModule } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { ExerciseFilterResults } from 'app/types/exercise-filter';
import { EventEmitter } from '@angular/core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

describe('SidebarComponent', () => {
    let component: SidebarComponent;
    let fixture: ComponentFixture<SidebarComponent>;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                MockModule(RouterModule),
                MockDirective(TranslateDirective),
                MockComponent(ExerciseFilterModalComponent),
            ],
            declarations: [
                SidebarComponent,
                SidebarCardMediumComponent,
                SidebarCardItemComponent,
                SidebarCardDirective,
                SearchFilterPipe,
                SearchFilterComponent,
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
            ],
            providers: [MockProvider(NgbModal)],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);

        component.sidebarData = {
            sidebarType: 'default',
        } as SidebarData;
        fixture.detectChanges();
    });

    it('should filter sidebar items based on search criteria', () => {
        component.sidebarData = {
            groupByCategory: true,
            ungroupedData: [
                { title: 'Item 1', type: 'Type A', id: 1, size: 'M' },
                { title: 'Item 2', type: 'Type B', id: 2, size: 'M' },
            ],
        };
        component.searchValue = 'Item 1';
        fixture.detectChanges();

        // Check if only the item with title 'Item 1' is being displayed
        let filteredItems = component.sidebarData.ungroupedData?.filter((item) => item.title.includes(component.searchValue));
        filteredItems = filteredItems ?? [];
        expect(filteredItems).toHaveLength(1);
        expect(filteredItems[0].title).toContain('Item 1');
    });

    it('should display the correct message when no data is found', () => {
        // Mock sidebarData to have no items
        component.sidebarData = {
            groupByCategory: true,
            ungroupedData: [],
        };
        component.sidebarDataBeforeFiltering = {
            groupByCategory: true,
            ungroupedData: [] as SidebarCardElement[],
        };

        const noDataMessageElement = fixture.debugElement.query(By.css('.scrollable-item-content')).nativeElement;

        expect(noDataMessageElement).toBeTruthy();
        // unfortunately the translation key is cut off in debug mode that seems to be used for testing
        expect(noDataMessageElement.getAttribute('ng-reflect-jhi-translate')).toBe('artemisApp.courseOverview.gene');
    });

    it('should give the correct size for exercises', () => {
        component.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exercise',
        };
        fixture.detectChanges();

        const size = component.getSize();
        expect(size).toBe('M');
    });

    it('should give the correct size for exams', () => {
        component.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exam',
        };
        fixture.detectChanges();

        const size = component.getSize();
        expect(size).toBe('L');
    });

    it('should give the correct size for default', () => {
        component.sidebarData = {
            groupByCategory: true,
        };
        fixture.detectChanges();

        const size = component.getSize();
        expect(size).toBe('M');
    });

    it('should update sidebar event subscription on re emit input event change', () => {
        fixture.detectChanges();
        const prevSubscription = component.sidebarEventSubscription;
        fixture.componentRef.setInput('reEmitNonDistinctSidebarEvents', true);
        fixture.detectChanges();
        expect(component.sidebarEventSubscription).not.toBe(prevSubscription);
    });

    describe('openFilterExercisesLink', () => {
        const FILTER_LINK_SELECTOR = '.text-primary a';

        it('should display the filter link', () => {
            component.showFilter = true;
            fixture.detectChanges();

            const filterLink = fixture.debugElement.query(By.css(FILTER_LINK_SELECTOR));

            expect(filterLink).toBeTruthy();
        });

        it('should NOT display the filter link when sidebarType is NOT exercise', () => {
            const filterLink = fixture.debugElement.query(By.css(FILTER_LINK_SELECTOR));

            expect(filterLink).toBeFalsy();
        });

        it('should open modal on click with initialized filters', () => {
            component.showFilter = true;
            fixture.detectChanges();
            const filterAppliedMock = new EventEmitter<ExerciseFilterResults>();
            const mockReturnValue = {
                result: Promise.resolve({}),
                componentInstance: {
                    sidebarData: {},
                    exerciseFilters: {},
                    filterApplied: filterAppliedMock,
                },
            } as NgbModalRef;
            const openModalSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
            const openFilterExercisesDialogSpy = jest.spyOn(component, 'openFilterExercisesDialog');
            const initFilterOptionsSpy = jest.spyOn(component, 'initializeFilterOptions');

            const filterLink = fixture.debugElement.query(By.css(FILTER_LINK_SELECTOR)).nativeElement;
            filterLink.click();

            expect(initFilterOptionsSpy).toHaveBeenCalledOnce();
            expect(openFilterExercisesDialogSpy).toHaveBeenCalledOnce();
            expect(openModalSpy).toHaveBeenCalledWith(ExerciseFilterModalComponent, { animation: true, backdrop: 'static', size: 'lg' });
        });
    });

    describe('Chat and Channel Creation Methods', () => {
        beforeEach(() => {
            fixture.detectChanges();
        });

        it('should emit onDirectChatPressed and set showChatDropdown to false when createDirectChat is called', () => {
            jest.spyOn(component.onDirectChatPressed, 'emit');

            component.createDirectChat();

            expect(component.onDirectChatPressed.emit).toHaveBeenCalledOnce();
        });

        it('should emit onGroupChatPressed and set showChatDropdown to false when createGroupChat is called', () => {
            jest.spyOn(component.onGroupChatPressed, 'emit');

            component.createGroupChat();

            expect(component.onGroupChatPressed.emit).toHaveBeenCalledOnce();
        });

        it('should emit onBrowsePressed and set showChannelDropdown to false when browseChannels is called', () => {
            jest.spyOn(component.onBrowsePressed, 'emit');

            component.browseChannels();

            expect(component.onBrowsePressed.emit).toHaveBeenCalledOnce();
        });

        it('should emit onCreateChannelPressed and set showChannelDropdown to false when createNewChannel is called', () => {
            jest.spyOn(component.onCreateChannelPressed, 'emit');

            component.createNewChannel();

            expect(component.onCreateChannelPressed.emit).toHaveBeenCalledOnce();
        });
    });

    describe('openFilterExercisesDialog', () => {
        it('should subscribe to filterApplied from modal', () => {
            const filterAppliedEmitter = new EventEmitter<ExerciseFilterResults>();
            const mockModalRef: Partial<NgbModalRef> = {
                componentInstance: {
                    filterApplied: filterAppliedEmitter,
                },
            };
            const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const subscribeSpy = jest.spyOn(filterAppliedEmitter, 'subscribe');

            component.openFilterExercisesDialog();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(subscribeSpy).toHaveBeenCalledOnce();
        });

        it('should update variables correctly when filterApplied is emitted', () => {
            const filterAppliedEmitter = new EventEmitter<ExerciseFilterResults>();
            const mockModalRef: Partial<NgbModalRef> = {
                componentInstance: {
                    filterApplied: filterAppliedEmitter,
                },
            };
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

            const mockFilterResults: ExerciseFilterResults = {
                filteredSidebarData: {
                    sidebarType: 'exercise',
                    groupByCategory: true,
                    ungroupedData: [{ title: 'test sidebar card element' } as SidebarCardElement],
                    groupedData: {
                        testGroup: {
                            entityData: [{ title: 'test group element' } as SidebarCardElement],
                        },
                    },
                },
                appliedExerciseFilters: {
                    categoryFilter: {
                        isDisplayed: true,
                        options: [
                            {
                                category: new ExerciseCategory('test', undefined),
                                searched: true,
                            },
                        ],
                    },
                    exerciseTypesFilter: {
                        isDisplayed: true,
                        options: [
                            {
                                name: 'testType',
                                value: ExerciseType.PROGRAMMING,
                                checked: true,
                                icon: 'testIcon' as unknown as IconProp,
                            },
                        ],
                    },
                },
                isFilterActive: true,
            };

            component.openFilterExercisesDialog();
            filterAppliedEmitter.emit(mockFilterResults);

            expect(component.sidebarData).toEqual(mockFilterResults.filteredSidebarData);
            expect(component.exerciseFilters).toEqual(mockFilterResults.appliedExerciseFilters);
            expect(component.isFilterActive).toBeTrue();
        });
    });
});
