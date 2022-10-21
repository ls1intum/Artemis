import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { COMMA, ENTER, TAB } from '@angular/cdk/keycodes';
import { FormControl } from '@angular/forms';
import { MatChipInputEvent } from '@angular/material/chips';
import { map, Observable, startWith } from 'rxjs';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-category-selector',
    templateUrl: './category-selector.component.html',
    styleUrls: ['./category-selector.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class CategorySelectorComponent implements OnChanges {
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;

    // the selected categories, which can be manipulated by the user in the UI
    @Input() categories: ExerciseCategory[];
    // the existing categories used for auto-completion, might include duplicates
    @Input() existingCategories: ExerciseCategory[];

    @Output() selectedCategories = new EventEmitter<ExerciseCategory[]>();

    @ViewChild('categoryInput') categoryInput: ElementRef<HTMLInputElement>;

    categoryColors = DEFAULT_COLORS;
    selectedCategory: ExerciseCategory;
    uniqueCategoriesForAutocomplete: Observable<string[]>;
    private readonly colorSelectorHeight = 150;

    separatorKeysCodes = [ENTER, COMMA, TAB];
    categoryCtrl = new FormControl<string | undefined>(undefined);

    // Icons
    faTimes = faTimes;

    ngOnChanges() {
        this.uniqueCategoriesForAutocomplete = this.categoryCtrl.valueChanges.pipe(
            startWith(undefined),
            map((userInput: string | undefined) => (userInput ? this.filterCategories(userInput) : this.existingCategoriesAsStringArray().slice())),
            // remove duplicated values
            map((categories: string[]) => [...new Set(categories)]),
            // remove categories that have already been selected in the exercise
            map((categories: string[]) => categories.filter((category) => !this.categoriesAsStringArray().includes(category.toLowerCase()))),
        );
    }

    private categoriesAsStringArray(): string[] {
        if (!this.categories) {
            return [];
        }
        return this.categories.map((exerciseCategory) => exerciseCategory.category!.toLowerCase());
    }

    private existingCategoriesAsStringArray(): string[] {
        if (!this.existingCategories) {
            return [];
        }
        return this.existingCategories.map((exerciseCategory) => exerciseCategory.category!.toLowerCase());
    }

    // if the user types in something, we need to filter for the matching categories
    private filterCategories(value: string): string[] {
        const filterValue = value.toLowerCase();
        return this.existingCategories.filter((category) => category.category!.toLowerCase().includes(filterValue)).map((category) => category.category!.toLowerCase());
    }

    /**
     * open colorSelector for tagItem
     * @param {MouseEvent} event
     * @param {ExerciseCategory} tagItem
     */
    openColorSelector(event: MouseEvent, tagItem: ExerciseCategory) {
        this.selectedCategory = tagItem;
        this.colorSelector.openColorSelector(event, undefined, this.colorSelectorHeight);
    }

    /**
     * set color of selected category
     * @param {string} selectedColor
     */
    onSelectedColor(selectedColor: string): void {
        this.selectedCategory.color = selectedColor;
        this.categories = this.categories.map((category) => {
            if (category.category === this.selectedCategory.category) {
                return this.selectedCategory;
            }
            return category;
        });
        this.selectedCategories.emit(this.categories);
    }

    /**
     * set color if not selected and add exerciseCategory
     * @param event a new category was added
     */
    onItemAdd(event: MatChipInputEvent) {
        const categoryString = (event.value || '').trim();
        // prevent adding duplicated categories
        if (!this.categoriesAsStringArray().includes(categoryString)) {
            let category = this.findExistingCategory(categoryString);
            if (!category) {
                category = this.createCategory(categoryString);
            }
            category.category = categoryString;
            if (!category.color) {
                category.color = this.chooseRandomColor();
            }
            if (this.categories) {
                this.categories.push(category);
            } else {
                this.categories = [category];
            }
            this.selectedCategories.emit(this.categories);
        }
        // Clear the input value
        event.chipInput!.clear();
        this.categoryCtrl.setValue(null);
    }

    private createCategory(categoryString: string): ExerciseCategory {
        const category = new ExerciseCategory();
        category.category = categoryString;
        category.color = this.chooseRandomColor();
        return category;
    }

    private chooseRandomColor(): string {
        const randomIndex = Math.floor(Math.random() * this.categoryColors.length);
        return this.categoryColors[randomIndex];
    }

    // only invoked for autocomplete
    onItemSelect(event: MatAutocompleteSelectedEvent): void {
        const categoryString = (event.option.value || '').trim();
        // check if there is an existing category and reuse the same color
        let category = this.findExistingCategory(categoryString);
        if (!category) {
            category = this.createCategory(categoryString);
        }

        this.categories.push(category);
        this.selectedCategories.emit(this.categories);
        this.categoryInput.nativeElement.value = '';
        this.categoryCtrl.setValue(null);
    }

    private findExistingCategory(categoryString: string): ExerciseCategory | undefined {
        return this.existingCategories.find((existingCategory) => existingCategory.category?.toLowerCase() === categoryString.toLowerCase());
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {ExerciseCategory} categoryToRemove
     */
    onItemRemove(categoryToRemove: ExerciseCategory): void {
        this.colorSelector.cancelColorSelector();
        this.categories = this.categories.filter((exerciseCategory) => exerciseCategory.category !== categoryToRemove.category);
        this.selectedCategories.emit(this.categories);
    }
}
