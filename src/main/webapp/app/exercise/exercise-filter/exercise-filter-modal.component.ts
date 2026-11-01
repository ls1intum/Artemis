import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { Component, OnInit, inject, output, signal, viewChild } from '@angular/core';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteOptionEvent, TumAetUiDialogRef } from '@tumaet/ui-angular';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RangeSliderComponent } from 'app/shared-ui/range-slider/range-slider.component';
import {
    DifficultyFilterOption,
    ExerciseCategoryFilterOption,
    ExerciseFilterOptions,
    ExerciseFilterResults,
    ExerciseTypeFilterOption,
    FilterDetails,
    FilterOption,
    RangeFilter,
} from 'app/foundation/types/exercise-filter';
import { satisfiesFilters } from 'app/exercise/exercise-filter/exercise-filter-modal.helper';
import { DifficultyLevel, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { isRangeFilterApplied } from 'app/course/sidebar/sidebar.helper';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { SidebarCardElement, SidebarData } from 'app/foundation/types/sidebar';

@Component({
    selector: 'jhi-exercise-filter-modal',
    templateUrl: './exercise-filter-modal.component.html',
    styleUrls: ['./exercise-filter-modal.component.scss'],
    imports: [
        TumAetUiCheckboxComponent,
        TumAetUiButtonDirective,
        FormsModule,
        ReactiveFormsModule,
        FontAwesomeModule,
        CustomExerciseCategoryBadgeComponent,
        RangeSliderComponent,
        TumAetUiAutoCompleteComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class ExerciseFilterModalComponent implements OnInit {
    private activeModal = inject(TumAetUiDialogRef);

    protected readonly faFilter = faFilter;

    readonly filterApplied = output<ExerciseFilterResults>();

    readonly instance = viewChild<TumAetUiAutoCompleteComponent>('categoriesFilterSelection');
    readonly categorySuggestions = signal<{ label: string; value: ExerciseCategoryFilterOption }[]>([]);

    readonly selectedCategoryOptions = signal<ExerciseCategoryFilterOption[]>([]);
    readonly selectableCategoryOptions = signal<ExerciseCategoryFilterOption[]>([]);

    readonly noFiltersAvailable = signal(false);

    form?: FormGroup;

    private readonly searchModel = signal<string | undefined>(undefined);
    get model(): string | undefined {
        return this.searchModel();
    }
    set model(value: string | undefined) {
        this.searchModel.set(value);
    }

    sidebarData?: SidebarData;

    categoryFilter?: FilterOption<ExerciseCategoryFilterOption>;
    readonly typeFilter = signal<FilterOption<ExerciseTypeFilterOption> | undefined>(undefined);
    readonly difficultyFilter = signal<FilterOption<DifficultyFilterOption> | undefined>(undefined);

    // achievablePoints / achievedScore are deep two-way binding targets (jhi-range-slider writes back to
    // achievablePoints.filter.selectedMin/Max etc.), so they are backed by a signal via a getter/setter
    // facade. Reads stay reactive while the slider can still mutate the backing object in place.
    private readonly _achievablePoints = signal<RangeFilter | undefined>(undefined);
    get achievablePoints(): RangeFilter | undefined {
        return this._achievablePoints();
    }
    set achievablePoints(value: RangeFilter | undefined) {
        this._achievablePoints.set(value);
    }

    private readonly _achievedScore = signal<RangeFilter | undefined>(undefined);
    get achievedScore(): RangeFilter | undefined {
        return this._achievedScore();
    }
    set achievedScore(value: RangeFilter | undefined) {
        this._achievedScore.set(value);
    }

    exerciseFilters?: ExerciseFilterOptions;

    ngOnInit() {
        this.categoryFilter = this.exerciseFilters?.categoryFilter;
        this.typeFilter.set(this.exerciseFilters?.exerciseTypesFilter);
        this.difficultyFilter.set(this.exerciseFilters?.difficultyFilter);
        this.achievablePoints = this.exerciseFilters?.achievablePoints;
        this.achievedScore = this.exerciseFilters?.achievedScore;

        this.noFiltersAvailable.set(
            !(
                this.categoryFilter?.isDisplayed ||
                this.typeFilter()?.isDisplayed ||
                this.difficultyFilter()?.isDisplayed ||
                this.achievedScore?.isDisplayed ||
                this.achievablePoints?.isDisplayed
            ),
        );

        this.updateCategoryOptionsStates();
    }

    closeModal(): void {
        this.activeModal.close();
    }

    requestCategorySearch(query: string): void {
        const matches = this.selectableCategoryOptions().filter((option) => this.resultFormatter(option).toLocaleLowerCase().includes(query.toLocaleLowerCase()));
        this.categorySuggestions.set(matches.map((value) => ({ label: this.resultFormatter(value), value })));
    }

    selectCategory(event: TumAetUiAutoCompleteOptionEvent): void {
        const option = this.categorySuggestions().find((suggestion) => suggestion === event.value);
        if (option) {
            this.onSelectItem({ item: option.value, preventDefault: () => {} });
        }
    }

    resultFormatter = (exerciseCategory: ExerciseCategoryFilterOption) => exerciseCategory.category.category ?? '';

    onSelectItem(event: { item: ExerciseCategoryFilterOption; preventDefault: () => void } | Event) {
        const isEnterPressedForNotExistingItem = !('item' in event) || !event.item;
        if (isEnterPressedForNotExistingItem) {
            event.preventDefault();
            if (event instanceof KeyboardEvent) {
                event.stopPropagation();
            }
            return;
        }

        event.preventDefault(); // otherwise clearing the input field will not work https://stackoverflow.com/questions/39783936/how-to-clear-the-typeahead-input-after-a-result-is-selected
        const filterOption: ExerciseCategoryFilterOption = event.item;
        filterOption.searched = true;
        this.updateCategoryOptionsStates();
        this.model = undefined;
        this.instance()?.clear();
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
        return this.typeFilter()
            ?.options.filter((type) => type.checked)
            .map((type) => type.value);
    }

    private getSelectedCategories(): ExerciseCategory[] {
        return this.selectedCategoryOptions()
            .filter((categoryOption: ExerciseCategoryFilterOption) => categoryOption.searched)
            .map((categoryOption: ExerciseCategoryFilterOption) => categoryOption.category);
    }

    private getSearchedDifficulties(): DifficultyLevel[] | undefined {
        return this.difficultyFilter()
            ?.options.filter((difficulty) => difficulty.checked)
            .map((difficulty) => difficulty.value);
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
        this.typeFilter()?.options.forEach((typeOption) => (typeOption.checked = false));
        this.difficultyFilter()?.options.forEach((difficultyOption) => (difficultyOption.checked = false));

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
        this.selectedCategoryOptions.set(this.getUpdatedSelectedCategoryOptions());
        this.selectableCategoryOptions.set(this.getSelectableCategoryOptions());
    }

    private getUpdatedSelectedCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilter?.options.filter((categoryFilter) => categoryFilter.searched) ?? [];
    }

    private getSelectableCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilter?.options.filter((categoryFilter) => !categoryFilter.searched) ?? [];
    }
}
