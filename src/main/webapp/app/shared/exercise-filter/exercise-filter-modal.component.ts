import { Component, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { NgbActiveModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { Observable, OperatorFunction, Subject, merge } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge.component';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import {
    DifficultyFilterOption,
    ExerciseCategoryFilterOption,
    ExerciseFilterOptions,
    ExerciseFilterResults,
    ExerciseTypeFilterOption,
    FilterOption,
    RangeFilter,
} from 'app/types/exercise-filter';
import { satisfiesFilters } from 'app/shared/exercise-filter/exercise-filter-modal.helper';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

export type FilterDetails = {
    searchedTypes: ExerciseType[] | undefined;
    selectedCategories: ExerciseCategory[];
    searchedDifficulties: DifficultyLevel[] | undefined;
    isScoreFilterApplied: boolean;
    isPointsFilterApplied: boolean;
    achievedScore: RangeFilter | undefined;
    achievablePoints: RangeFilter | undefined;
};

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
    readonly faFilter = faFilter;

    @Output() filterApplied = new EventEmitter<ExerciseFilterResults>();

    @ViewChild('categoriesFilterSelection', { static: false }) instance: NgbTypeahead;

    get selectedCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilters?.options.filter((categoryFilter) => categoryFilter.searched) ?? [];
    }

    get selectableCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilters?.options.filter((categoryFilter) => !categoryFilter.searched) ?? [];
    }

    noFiltersAvailable: boolean = false;

    focus$ = new Subject<string>();
    click$ = new Subject<string>();

    // TODO x has a light blue border when opening the modal (the first item in the modal is auto focussed)

    form: FormGroup;

    model?: string;

    sidebarData?: SidebarData;

    categoryFilters?: FilterOption<ExerciseCategoryFilterOption>;
    typeFilters?: FilterOption<ExerciseTypeFilterOption>;
    difficultyFilters?: FilterOption<DifficultyFilterOption>;
    achievablePoints?: RangeFilter;
    achievedScore?: RangeFilter;

    exerciseFilters?: ExerciseFilterOptions;

    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit() {
        this.categoryFilters = this.exerciseFilters?.categoryFilter;
        this.typeFilters = this.exerciseFilters?.exerciseTypesFilter;
        this.difficultyFilters = this.exerciseFilters?.difficultyFilter;
        this.achievablePoints = this.exerciseFilters?.achievablePoints;
        this.achievedScore = this.exerciseFilters?.achievedScore;

        this.noFiltersAvailable = !(
            this.categoryFilters?.isDisplayed ||
            this.typeFilters?.isDisplayed ||
            this.difficultyFilters?.isDisplayed ||
            this.achievedScore?.isDisplayed ||
            this.achievablePoints?.isDisplayed
        );
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
                    : this.selectableCategoryOptions.filter(
                          (categoryFilter: ExerciseCategoryFilterOption) => categoryFilter.category.category!.toLowerCase().indexOf(term.toLowerCase()) > -1,
                      ),
            ),
        );
    };
    inputFormatter = (exerciseCategory: ExerciseCategoryFilterOption) => exerciseCategory.category.category ?? '';
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
        this.model = undefined; // Clear the input field after selection
    }

    removeItem(item: ExerciseCategoryFilterOption) {
        item.searched = false;
    }

    getRemoveItemFunction(item: ExerciseCategoryFilterOption): () => void {
        return () => this.removeItem(item);
    }

    getAppliedFilterDetails(): FilterDetails {
        return {
            searchedTypes: this.typeFilters?.options.filter((type) => type.checked).map((type) => type.value),
            selectedCategories: this.selectedCategoryOptions.map((categoryOption: ExerciseCategoryFilterOption) => categoryOption.category),
            searchedDifficulties: this.difficultyFilters?.options.filter((difficulty) => difficulty.checked).map((difficulty) => difficulty.value),
            isScoreFilterApplied:
                this.achievedScore?.filter.selectedMin !== this.achievedScore?.filter.generalMin ||
                this.achievedScore?.filter.selectedMax !== this.achievedScore?.filter.generalMax,
            isPointsFilterApplied:
                this.achievablePoints?.filter.selectedMin !== this.achievablePoints?.filter.generalMin ||
                this.achievablePoints?.filter.selectedMax !== this.achievablePoints?.filter.generalMax,
            achievedScore: this.achievedScore,
            achievablePoints: this.achievablePoints,
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
            filteredSidebarData: this.sidebarData!,
            appliedExerciseFilters: this.exerciseFilters,
            isFilterActive: this.isFilterActive(appliedFilterDetails),
        });

        this.closeModal();
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
}
