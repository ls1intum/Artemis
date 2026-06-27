import { Component, ViewEncapsulation, computed, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { ColorSelectorComponent } from 'app/shared-ui/color-selector/color-selector.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { FormsModule } from '@angular/forms';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AutoComplete, AutoCompleteCompleteEvent, AutoCompleteSelectEvent, AutoCompleteUnselectEvent } from 'primeng/autocomplete';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-category-selector-primeng',
    templateUrl: './category-selector-primeng.component.html',
    styleUrls: ['./category-selector-primeng.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [AutoComplete, FormsModule, FaIconComponent, ColorSelectorComponent, ArtemisTranslatePipe],
})
export class CategorySelectorPrimengComponent {
    protected readonly faTimes = faTimes;
    private readonly COLOR_SELECTOR_HEIGHT = 150;
    protected readonly MAX_CATEGORIES = 3;

    /** the selected categories passed in by the parent */
    readonly categories = input<ExerciseCategory[] | FaqCategory[]>();
    /** the existing categories used for auto-completion, might include duplicates */
    readonly existingCategories = input<ExerciseCategory[] | FaqCategory[]>();

    /** local working copy the user manipulates in the UI; re-seeds whenever the parent passes new categories */
    readonly selectedCategoryItems = linkedSignal<ExerciseCategory[] | FaqCategory[]>(() => this.categories() ?? []);

    readonly colorSelector = viewChild.required(ColorSelectorComponent);
    readonly autoComplete = viewChild.required(AutoComplete);

    readonly selectedCategories = output<ExerciseCategory[]>();

    categoryColors = DEFAULT_COLORS;
    selectedCategory: ExerciseCategory;

    /**
     * Suggestions shown in the p-autoComplete dropdown. Updated on every keystroke via {@link onComplete}.
     * Excludes already-selected categories.
     */
    readonly categorySuggestions = signal<string[]>([]);

    /**
     * The labels of the selected categories. Used as the p-autoComplete `multiple` model so each category renders
     * as a removable chip token. The colored chip rendering is provided by the `selecteditem` template, which looks
     * the color up from {@link selectedCategoryItems} by label.
     */
    readonly selectedCategoryLabels = computed<string[]>(() => this.selectedCategoryItems().map((category) => category.category ?? ''));

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
     * Recompute the autocomplete suggestions for the current query. Mirrors the previous combineLatest logic:
     * filter the existing categories by the typed text (or show all when empty), de-duplicate, and drop categories
     * that are already selected.
     * @param event the p-autoComplete complete event carrying the current query
     */
    onComplete(event: AutoCompleteCompleteEvent): void {
        const query = event.query;
        const candidates = query ? this.filterCategories(query) : this.existingCategoriesAsStringArray().slice();
        const selected = this.categoriesAsStringArray();
        this.categorySuggestions.set([...new Set(candidates)].filter((category) => !selected.includes(category.toLowerCase())));
    }

    /**
     * Look up the color for a selected category label so the chip token can be rendered in the category color.
     * @param label the category label rendered as a chip token
     */
    colorFor(label: string): string | undefined {
        return this.selectedCategoryItems().find((category) => category.category === label)?.color;
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
     * open the color selector for the category identified by its label (used from the chip token template).
     * @param event the originating mouse event
     * @param label the category label rendered as a chip token
     */
    openColorSelectorForLabel(event: MouseEvent, label: string) {
        const category = this.selectedCategoryItems().find((item) => item.category === label);
        if (category) {
            this.openColorSelector(event, category);
        }
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
     * Adds the typed free-text value as a new category. Triggered by the Enter key on the p-autoComplete input
     * (PrimeNG does not add free text natively while typeahead is enabled, so we wire it via a keydown handler).
     * @param event the keyboard event coming from the input
     */
    onEnter(event: KeyboardEvent): void {
        event.preventDefault();
        event.stopPropagation();
        const input = event.target as HTMLInputElement;
        this.addCategoryByString(input.value);
        // Clear the input and close the suggestion overlay so the next add starts fresh.
        input.value = '';
        this.autoComplete().hide();
    }

    /**
     * set color if not selected and add exerciseCategory
     * @param categoryString a new category to add
     */
    private addCategoryByString(categoryString: string) {
        categoryString = (categoryString || '').trim();
        // prevent adding duplicated categories
        const categoryArray = this.categoriesAsStringArray();
        if (categoryString && !categoryArray.includes(categoryString.toLowerCase()) && categoryArray.length < this.MAX_CATEGORIES) {
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
    }

    private createCategory(categoryString: string): ExerciseCategory {
        return new ExerciseCategory(categoryString, this.chooseRandomColor());
    }

    private chooseRandomColor(): string {
        const randomIndex = Math.floor(Math.random() * this.categoryColors.length);
        return this.categoryColors[randomIndex];
    }

    /**
     * Adds the category picked from the autocomplete dropdown, reusing an existing category's color when available.
     * @param event the p-autoComplete select event carrying the chosen suggestion label
     */
    onItemSelect(event: AutoCompleteSelectEvent): void {
        const categoryString = (event.value || '').trim();
        const categoryArray = this.categoriesAsStringArray();
        if (categoryString && !categoryArray.includes(categoryString.toLowerCase()) && categoryArray.length < this.MAX_CATEGORIES) {
            // check if there is an existing category and reuse the same color
            let category = this.findExistingCategory(categoryString);
            if (!category) {
                category = this.createCategory(categoryString);
            }

            const updated = [...this.selectedCategoryItems(), category];
            this.selectedCategoryItems.set(updated);
            this.selectedCategories.emit(updated);
        } else {
            // The selection was rejected (duplicate or MAX_CATEGORIES reached). PrimeNG has already added the option
            // to its internal model, so resync it back to the accepted labels to avoid leaving a phantom chip.
            this.autoComplete().writeValue(this.selectedCategoryLabels());
        }
    }

    private findExistingCategory(categoryString: string): ExerciseCategory | undefined {
        return (this.existingCategories() ?? []).find((existingCategory) => existingCategory.category?.toLowerCase() === categoryString.toLowerCase());
    }

    /**
     * Removes the category whose chip token was removed via the built-in p-autoComplete remove icon.
     * @param event the p-autoComplete unselect event carrying the removed label
     */
    onItemUnselect(event: AutoCompleteUnselectEvent): void {
        this.removeCategoryByLabel(event.value);
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {ExerciseCategory} categoryToRemove
     */
    onItemRemove(categoryToRemove: ExerciseCategory): void {
        this.removeCategoryByLabel(categoryToRemove.category);
    }

    private removeCategoryByLabel(label: string | undefined): void {
        this.colorSelector().cancelColorSelector();
        const updated = this.selectedCategoryItems().filter((exerciseCategory) => exerciseCategory.category !== label);
        this.selectedCategoryItems.set(updated);
        this.selectedCategories.emit(updated);
    }
}
