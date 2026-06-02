import { Component, ElementRef, ViewEncapsulation, input, linkedSignal, output, viewChild } from '@angular/core';
import { ColorSelectorComponent } from 'app/shared-ui/color-selector/color-selector.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MatAutocomplete, MatAutocompleteSelectedEvent, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { COMMA, ENTER, TAB } from '@angular/cdk/keycodes';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatChipGrid, MatChipInput, MatChipInputEvent, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, combineLatest, map, startWith } from 'rxjs';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { MatFormField } from '@angular/material/form-field';
import { AsyncPipe, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MatOption } from '@angular/material/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-category-selector-primeng',
    templateUrl: './category-selector-primeng.component.html',
    styleUrls: ['./category-selector-primeng.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        MatFormField,
        MatChipGrid,
        MatChipRow,
        NgStyle,
        MatChipRemove,
        FaIconComponent,
        FormsModule,
        MatAutocompleteTrigger,
        MatChipInput,
        ReactiveFormsModule,
        MatAutocomplete,
        MatOption,
        ColorSelectorComponent,
        AsyncPipe,
        ArtemisTranslatePipe,
    ],
})
export class CategorySelectorPrimengComponent {
    protected readonly faTimes = faTimes;
    protected readonly separatorKeysCodes = [ENTER, COMMA, TAB];
    private readonly COLOR_SELECTOR_HEIGHT = 150;

    /** the selected categories passed in by the parent */
    readonly categories = input<ExerciseCategory[] | FaqCategory[]>();
    /** the existing categories used for auto-completion, might include duplicates */
    readonly existingCategories = input<ExerciseCategory[] | FaqCategory[]>();

    /** local working copy the user manipulates in the UI; re-seeds whenever the parent passes new categories */
    readonly selectedCategoryItems = linkedSignal<ExerciseCategory[] | FaqCategory[]>(() => this.categories() ?? []);

    readonly colorSelector = viewChild.required(ColorSelectorComponent);
    readonly categoryInput = viewChild.required<ElementRef<HTMLInputElement>>('categoryInput');
    readonly autocompleteTrigger = viewChild.required(MatAutocompleteTrigger);

    readonly selectedCategories = output<ExerciseCategory[]>();

    categoryColors = DEFAULT_COLORS;
    selectedCategory: ExerciseCategory;

    categoryCtrl = new FormControl<string | undefined>(undefined);

    // Re-emit when the user types, but also when the parent updates the existing or selected categories, so the options never go stale after the initial render.
    readonly uniqueCategoriesForAutocomplete: Observable<string[]> = combineLatest([
        this.categoryCtrl.valueChanges.pipe(startWith(undefined)),
        toObservable(this.existingCategories),
        toObservable(this.selectedCategoryItems),
    ]).pipe(
        map(([userInput]) => (userInput ? this.filterCategories(userInput) : this.existingCategoriesAsStringArray().slice())),
        // remove duplicated values
        map((categories: string[]) => [...new Set(categories)]),
        // remove categories that have already been selected in the exercise
        map((categories: string[]) => categories.filter((category) => !this.categoriesAsStringArray().includes(category.toLowerCase()))),
    );

    private categoriesAsStringArray(): string[] {
        return this.selectedCategoryItems().map((exerciseCategory) => exerciseCategory.category?.toLowerCase() ?? '');
    }

    private existingCategoriesAsStringArray(): string[] {
        const existingCategories = this.existingCategories();
        if (!existingCategories) {
            return [];
        }
        return existingCategories.map((exerciseCategory) => exerciseCategory.category?.toLowerCase() ?? '');
    }

    // if the user types in something, we need to filter for the matching categories
    private filterCategories(value: string): string[] {
        const filterValue = value.toLowerCase();
        return (this.existingCategories() ?? []).filter((category) => category.category!.toLowerCase().includes(filterValue)).map((category) => category.category!.toLowerCase());
    }

    /**
     * open colorSelector for tagItem
     * @param {MouseEvent} event
     * @param {ExerciseCategory} tagItem
     */
    openColorSelector(event: MouseEvent, tagItem: ExerciseCategory) {
        this.selectedCategory = tagItem;
        this.colorSelector().openColorSelector(event, undefined, this.COLOR_SELECTOR_HEIGHT);
    }

    /**
     * set color of selected category
     * @param {string} selectedColor
     */
    onSelectedColor(selectedColor: string): void {
        this.selectedCategory.color = selectedColor;
        const updated = this.selectedCategoryItems().map((category) => (category.category === this.selectedCategory.category ? this.selectedCategory : category));
        this.selectedCategoryItems.set(updated);
        this.selectedCategories.emit(updated);
    }

    /**
     * set color if not selected and add exerciseCategory
     * @param event a new category was added
     */
    onItemAdd(event: MatChipInputEvent) {
        const categoryString = (event.value || '').trim();
        // prevent adding duplicated categories
        const categoryArray = this.categoriesAsStringArray();
        if (categoryString && !categoryArray.includes(categoryString) && categoryArray.length < 2) {
            let category = this.findExistingCategory(categoryString);
            if (!category) {
                category = this.createCategory(categoryString);
            }
            category.category = categoryString;
            if (!category.color) {
                category.color = this.chooseRandomColor();
            }
            const updated = [...this.selectedCategoryItems(), category];
            this.selectedCategoryItems.set(updated);
            this.selectedCategories.emit(updated);
        }
        // Clear the input value
        event.chipInput!.clear();
        this.categoryCtrl.setValue(null);
        this.autocompleteTrigger().closePanel();
    }

    private createCategory(categoryString: string): ExerciseCategory {
        return new ExerciseCategory(categoryString, this.chooseRandomColor());
    }

    private chooseRandomColor(): string {
        const randomIndex = Math.floor(Math.random() * this.categoryColors.length);
        return this.categoryColors[randomIndex];
    }

    // only invoked for autocomplete
    onItemSelect(event: MatAutocompleteSelectedEvent): void {
        const categoryString = (event.option.value || '').trim();
        const categoryArray = this.categoriesAsStringArray();
        if (categoryString && !categoryArray.includes(categoryString) && categoryArray.length < 2) {
            // check if there is an existing category and reuse the same color
            let category = this.findExistingCategory(categoryString);
            if (!category) {
                category = this.createCategory(categoryString);
            }

            const updated = [...this.selectedCategoryItems(), category];
            this.selectedCategoryItems.set(updated);
            this.selectedCategories.emit(updated);
        }
        this.categoryInput().nativeElement.value = '';
        this.categoryCtrl.setValue(null);
    }

    private findExistingCategory(categoryString: string): ExerciseCategory | undefined {
        return (this.existingCategories() ?? []).find((existingCategory) => existingCategory.category?.toLowerCase() === categoryString.toLowerCase());
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {ExerciseCategory} categoryToRemove
     */
    onItemRemove(categoryToRemove: ExerciseCategory): void {
        this.colorSelector().cancelColorSelector();
        const updated = this.selectedCategoryItems().filter((exerciseCategory) => exerciseCategory.category !== categoryToRemove.category);
        this.selectedCategoryItems.set(updated);
        this.selectedCategories.emit(updated);
    }
}
