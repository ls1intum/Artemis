import { Component, EventEmitter, OnInit, Output, ViewChild, inject } from '@angular/core';
import { NgbActiveModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { faBackward, faFilter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { Observable, OperatorFunction, Subject, merge } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import {
    DifficultyFilterOption,
    ExerciseCategoryFilterOption,
    ExerciseFilterOptions,
    ExerciseFilterResults,
    ExerciseTypeFilterOption,
    FilterDetails,
    FilterOption,
    RangeFilter,
} from 'app/types/exercise-filter';
import { satisfiesFilters } from 'app/shared/exercise-filter/exercise-filter-modal.helper';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { isRangeFilterApplied } from 'app/shared/sidebar/sidebar.helper';

@Component({
    selector: 'jhi-exercise-filter-modal',
    templateUrl: './exercise-filter-modal.component.html',
    styleUrls: ['./exercise-filter-modal.component.scss'],
    standalone: true,
    imports: [
        FormsModule,
        ReactiveFormsModule,
        FontAwesomeModule,
        ArtemisSharedCommonModule,
        ArtemisSharedComponentModule,
        CustomExerciseCategoryBadgeComponent,
        RangeSliderComponent,
    ],
})
export class ExerciseFilterModalComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);

    readonly faFilter = faFilter;
    readonly faBackward = faBackward;

    @Output() filterApplied = new EventEmitter<ExerciseFilterResults>();

    @ViewChild('categoriesFilterSelection', { static: false }) instance: NgbTypeahead;

    selectedCategoryOptions: ExerciseCategoryFilterOption[] = [];
    selectableCategoryOptions: ExerciseCategoryFilterOption[] = [];

    noFiltersAvailable: boolean = false;

    focus$ = new Subject<string>();
    click$ = new Subject<string>();

    form: FormGroup;

    model?: string;

    sidebarData?: SidebarData;

    categoryFilter?: FilterOption<ExerciseCategoryFilterOption>;
    typeFilter?: FilterOption<ExerciseTypeFilterOption>;
    difficultyFilter?: FilterOption<DifficultyFilterOption>;
    achievablePoints?: RangeFilter;
    achievedScore?: RangeFilter;

    exerciseFilters?: ExerciseFilterOptions;

    ngOnInit() {
        this.categoryFilter = this.exerciseFilters?.categoryFilter;
        this.typeFilter = this.exerciseFilters?.exerciseTypesFilter;
        this.difficultyFilter = this.exerciseFilters?.difficultyFilter;
        this.achievablePoints = this.exerciseFilters?.achievablePoints;
        this.achievedScore = this.exerciseFilters?.achievedScore;

        this.noFiltersAvailable = !(
            this.categoryFilter?.isDisplayed ||
            this.typeFilter?.isDisplayed ||
            this.difficultyFilter?.isDisplayed ||
            this.achievedScore?.isDisplayed ||
            this.achievablePoints?.isDisplayed
        );

        this.updateCategoryOptionsStates();
    }

    closeModal(): void {
        this.activeModal.close();
    }

    search: OperatorFunction<string, readonly ExerciseCategoryFilterOption[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.click$.pipe(filter(() => !this.instance.isPopupOpen()));
        const inputFocus$ = this.focus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) =>
                term === ''
                    ? this.selectableCategoryOptions
                    : this.selectableCategoryOptions.filter((categoryFilter: ExerciseCategoryFilterOption) => {
                          if (categoryFilter.category.category !== undefined) {
                              return categoryFilter.category.category?.toLowerCase().indexOf(term.toLowerCase()) > -1;
                          }

                          return false;
                      }),
            ),
        );
    };
    resultFormatter = (exerciseCategory: ExerciseCategoryFilterOption) => exerciseCategory.category.category ?? '';

    onSelectItem(event: any) {
        const isEnterPressedForNotExistingItem = !event.item;
        if (isEnterPressedForNotExistingItem) {
            event.preventDefault();
            event.stopPropagation();
            return;
        }

        event.preventDefault(); // otherwise clearing the input field will not work https://stackoverflow.com/questions/39783936/how-to-clear-the-typeahead-input-after-a-result-is-selected
        const filterOption: ExerciseCategoryFilterOption = event.item;
        filterOption.searched = true;
        this.updateCategoryOptionsStates();
        this.model = undefined; // Clear the input field after selection
    }

    removeItem(item: ExerciseCategoryFilterOption): () => void {
        return () => {
            item.searched = false;
            this.updateCategoryOptionsStates();
        };
    }

    applyFilter(): void {
        if (!this.sidebarData?.groupedData) {
            return;
        }

        const appliedFilterDetails = this.getAppliedFilterDetails();
        for (const groupedDataKey in this.sidebarData.groupedData) {
            this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter((sidebarElement) =>
                satisfiesFilters(sidebarElement, appliedFilterDetails),
            );
        }
        this.sidebarData.ungroupedData = this.sidebarData.ungroupedData?.filter((sidebarElement: SidebarCardElement) => satisfiesFilters(sidebarElement, appliedFilterDetails));

        this.filterApplied.emit({
            filteredSidebarData: this.sidebarData,
            appliedExerciseFilters: this.exerciseFilters,
            isFilterActive: this.isFilterActive(appliedFilterDetails),
        });

        this.closeModal();
    }

    private getAppliedFilterDetails(): FilterDetails {
        return {
            searchedTypes: this.getSearchedTypes(),
            selectedCategories: this.getSelectedCategories(),
            searchedDifficulties: this.getSearchedDifficulties(),
            isScoreFilterApplied: isRangeFilterApplied(this.achievedScore),
            isPointsFilterApplied: isRangeFilterApplied(this.achievablePoints),
            achievedScore: this.achievedScore,
            achievablePoints: this.achievablePoints,
        };
    }

    private getSearchedTypes(): ExerciseType[] | undefined {
        return this.typeFilter?.options.filter((type) => type.checked).map((type) => type.value);
    }

    private getSelectedCategories(): ExerciseCategory[] {
        return this.selectedCategoryOptions
            .filter((categoryOption: ExerciseCategoryFilterOption) => categoryOption.searched)
            .map((categoryOption: ExerciseCategoryFilterOption) => categoryOption.category);
    }

    private getSearchedDifficulties(): DifficultyLevel[] | undefined {
        return this.difficultyFilter?.options.filter((difficulty) => difficulty.checked).map((difficulty) => difficulty.value);
    }

    private isFilterActive(filterDetails: FilterDetails): boolean {
        return (
            !!filterDetails.selectedCategories.length ||
            !!filterDetails.searchedTypes?.length ||
            !!filterDetails.searchedDifficulties?.length ||
            filterDetails.isScoreFilterApplied ||
            filterDetails.isPointsFilterApplied
        );
    }

    clearFilter() {
        this.categoryFilter?.options.forEach((categoryOption) => (categoryOption.searched = false));
        this.typeFilter?.options.forEach((typeOption) => (typeOption.checked = false));
        this.difficultyFilter?.options.forEach((difficultyOption) => (difficultyOption.checked = false));

        this.resetRangeFilter(this.achievedScore);
        this.resetRangeFilter(this.achievablePoints);

        this.applyFilter();
    }

    private resetRangeFilter(rangeFilter?: RangeFilter) {
        if (!rangeFilter?.filter) {
            return;
        }

        const filter = rangeFilter.filter;
        filter.selectedMin = filter.generalMin;
        filter.selectedMax = filter.generalMax;
    }

    private updateCategoryOptionsStates() {
        this.selectedCategoryOptions = this.getUpdatedSelectedCategoryOptions();
        this.selectableCategoryOptions = this.getSelectableCategoryOptions();
    }

    private getUpdatedSelectedCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilter?.options.filter((categoryFilter) => categoryFilter.searched) ?? [];
    }

    private getSelectableCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilter?.options.filter((categoryFilter) => !categoryFilter.searched) ?? [];
    }
}
