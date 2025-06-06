import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MatAutocomplete, MatAutocompleteSelectedEvent, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { COMMA, ENTER, TAB } from '@angular/cdk/keycodes';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatChipGrid, MatChipInput, MatChipInputEvent, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { Observable, map, startWith } from 'rxjs';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { MatFormField } from '@angular/material/form-field';
import { AsyncPipe, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MatOption } from '@angular/material/core';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-category-selector',
    templateUrl: './category-selector.component.html',
    styleUrls: ['./category-selector.component.scss'],
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
export class CategorySelectorComponent implements OnChanges {
    protected readonly faTimes = faTimes;
    protected readonly separatorKeysCodes = [ENTER, COMMA, TAB];
    private readonly COLOR_SELECTOR_HEIGHT = 150;

    /** the selected categories, which can be manipulated by the user in the UI */
    @Input() categories: ExerciseCategory[] | FaqCategory[];
    /** the existing categories used for auto-completion, might include duplicates */
    @Input() existingCategories: ExerciseCategory[] | FaqCategory[];

    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;
    @ViewChild('categoryInput') categoryInput: ElementRef<HTMLInputElement>;
    @ViewChild(MatAutocompleteTrigger) autocompleteTrigger: MatAutocompleteTrigger;

    @Output() selectedCategories = new EventEmitter<ExerciseCategory[]>();

    categoryColors = DEFAULT_COLORS;
    selectedCategory: ExerciseCategory;
    uniqueCategoriesForAutocomplete: Observable<string[]>;

    categoryCtrl = new FormControl<string | undefined>(undefined);

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
        return this.categories.map((exerciseCategory) => exerciseCategory.category?.toLowerCase() ?? '');
    }

    private existingCategoriesAsStringArray(): string[] {
        if (!this.existingCategories) {
            return [];
        }
        return this.existingCategories.map((exerciseCategory) => exerciseCategory.category?.toLowerCase() ?? '');
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
        this.colorSelector.openColorSelector(event, undefined, this.COLOR_SELECTOR_HEIGHT);
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
        this.autocompleteTrigger.closePanel();
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

            this.categories.push(category);
            this.selectedCategories.emit(this.categories);
        }
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
