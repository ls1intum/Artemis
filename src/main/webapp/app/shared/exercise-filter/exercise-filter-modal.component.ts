import { Component, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { NgbActiveModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { Observable, OperatorFunction, Subject, merge } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge.component';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';
import {
    DifficultyFilterOptions,
    ExerciseCategoryFilterOption,
    ExerciseFilterOptions,
    ExerciseFilterResults,
    ExerciseTypeFilterOptions,
    RangeFilter,
} from 'app/types/exercise-filter';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Component({
    selector: 'jhi-exercise-filter-modal',
    templateUrl: './exercise-filter-modal.component.html',
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

    @ViewChild('categoriesFilterSelection', { static: true }) instance: NgbTypeahead;

    get selectedCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilters.filter((categoryFilter) => categoryFilter.searched);
    }

    get selectableCategoryOptions(): ExerciseCategoryFilterOption[] {
        return this.categoryFilters.filter((categoryFilter) => !categoryFilter.searched);
    }

    focus$ = new Subject<string>();
    click$ = new Subject<string>();

    // TODO x has a light blue border when opening the modal (the first item in the modal is auto focussed)

    form: FormGroup;

    model?: string;

    sidebarData?: SidebarData;

    categoryFilters: ExerciseCategoryFilterOption[] = [];
    typeFilters?: ExerciseTypeFilterOptions;
    difficultyFilters?: DifficultyFilterOptions;
    achievablePoints?: RangeFilter;
    achievedScore?: RangeFilter;

    exerciseFilters?: ExerciseFilterOptions;

    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit() {
        this.categoryFilters = this.exerciseFilters?.categoryFilters ?? [];
        this.typeFilters = this.exerciseFilters?.exerciseTypesFilter;
        this.difficultyFilters = this.exerciseFilters?.difficultyFilters;
        this.achievablePoints = this.exerciseFilters?.achievablePoints;
        this.achievedScore = this.exerciseFilters?.achievedScore;
    }

    // TODO reset filter state when closing the modal without saving

    // TODO filter exercise types to exercise types that are used

    // TODO do not display difficulties selection if not enough selection options

    // ngAfterViewInit(): void {
    //     // otherwise the close button will be autofocused, leading to a blue border around the button
    //     this.firstCheckbox.nativeElement.focus();
    // }

    closeModal(): void {
        this.activeModal.close();
    }

    search: OperatorFunction<string, readonly ExerciseCategoryFilterOption[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.click$.pipe(filter(() => !this.instance.isPopupOpen()));
        const inputFocus$ = this.focus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) =>
                (term === ''
                    ? this.selectableCategoryOptions
                    : this.selectableCategoryOptions.filter(
                          (categoryFilter: ExerciseCategoryFilterOption) => categoryFilter.category.category!.toLowerCase().indexOf(term.toLowerCase()) > -1,
                      )
                ).slice(0, 10),
            ),
        );
    };
    inputFormatter = (exerciseCategory: ExerciseCategoryFilterOption) => exerciseCategory.category.category ?? '';
    resultFormatter = (exerciseCategory: ExerciseCategoryFilterOption) => exerciseCategory.category.category ?? '';

    onSelectItem(event: any) {
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

    applyFilter(): void {
        if (!this.sidebarData?.groupedData) {
            return;
        }

        this.applyDifficultyFilter();

        const searchedTypes: ExerciseType[] | undefined = this.typeFilters?.filter((type) => type.checked).map((type) => type.value);
        const selectedCategories = this.selectedCategoryOptions.map((categoryOption: ExerciseCategoryFilterOption) => categoryOption.category);

        for (const groupedDataKey in this.sidebarData.groupedData) {
            this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter(
                (sidebarElement) => this.applyTypeFilter(sidebarElement, searchedTypes) && this.applyCategoryFilter(sidebarElement, selectedCategories),
            );
        }

        // this.applyPointsFilter();
        // this.applyScoreFilter();

        this.filterApplied.emit({ filteredSidebarData: this.sidebarData!, appliedExerciseFilters: this.exerciseFilters });

        this.closeModal();
    }

    private applyTypeFilter(sidebarElement: SidebarCardElement, searchedTypes?: ExerciseType[]): boolean {
        if (!searchedTypes?.length) {
            return true;
        }
        if (!sidebarElement.exercise?.type) {
            return false;
        }

        return searchedTypes.includes(sidebarElement.exercise.type);
    }

    private applyCategoryFilter(sidebarElement: SidebarCardElement, selectedCategories: ExerciseCategory[]): boolean {
        if (!selectedCategories.length) {
            return true;
        }
        if (!sidebarElement?.exercise?.categories) {
            return false;
        }

        return sidebarElement.exercise.categories.some((category) => selectedCategories.some((selectedCategory) => selectedCategory.equals(category)));
    }

    private applyDifficultyFilter() {
        if (!this.difficultyFilters) {
            return;
        }
        const searchedDifficulties: DifficultyLevel[] = this.difficultyFilters?.filter((difficulty) => difficulty.checked).map((difficulty) => difficulty.value);
        if (searchedDifficulties.length === 0) {
            return;
        }

        // TODO do we need to filter the ungrouped data as well?
        if (this.sidebarData?.groupedData) {
            for (const groupedDataKey in this.sidebarData.groupedData) {
                this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter(
                    (sidebarElement) => sidebarElement.difficulty && searchedDifficulties.includes(sidebarElement.difficulty),
                );
            }
        }
    }

    private applyScoreFilter() {
        if (!this.achievedScore) {
            return;
        }

        if (this.sidebarData?.groupedData) {
            for (const groupedDataKey in this.sidebarData.groupedData) {
                this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter((sidebarElement) => {
                    if (!sidebarElement.studentParticipation) {
                        return false;
                    }

                    const latestResult = getLatestResultOfStudentParticipation(sidebarElement.studentParticipation, true);
                    if (!latestResult?.score) {
                        return false;
                    }
                    return latestResult.score <= this.achievedScore!.selectedMax && latestResult.score >= this.achievedScore!.selectedMin;
                });
            }
        }
    }

    private applyPointsFilter() {
        if (!this.achievablePoints) {
            return;
        }

        if (this.sidebarData?.groupedData) {
            for (const groupedDataKey in this.sidebarData.groupedData) {
                this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter(
                    (sidebarElement) =>
                        (sidebarElement?.exercise?.maxPoints ?? 0) <= (this.achievablePoints?.selectedMax ?? Number.MAX_SAFE_INTEGER) &&
                        (sidebarElement?.exercise?.maxPoints ?? 0) >= (this.achievablePoints?.selectedMin ?? Number.MIN_SAFE_INTEGER),
                );
            }
        }
    }
}
