import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
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
export class CategorySelectorComponent implements OnChanges, OnInit {
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;

    @Input() exerciseCategories: ExerciseCategory[];
    @Input() existingCategories: ExerciseCategory[];

    @Output() selectedCategories = new EventEmitter<ExerciseCategory[]>();

    @ViewChild('categoryInput') categoryInput: ElementRef<HTMLInputElement>;

    categoryColors = DEFAULT_COLORS;
    selectedCategory: ExerciseCategory;
    uniqueCategories: Observable<string[]>;
    private readonly colorSelectorHeight = 150;

    separatorKeysCodes = [ENTER, COMMA, TAB];
    categoryCtrl = new FormControl();

    // Icons
    faTimes = faTimes;

    ngOnInit() {
        this.uniqueCategories = this.categoryCtrl.valueChanges.pipe(
            startWith(undefined),
            map((category: string | undefined) => (category ? this._filter(category) : this.exerciseCategories.map((exerciseCategory) => exerciseCategory.category!).slice())),
        );
    }

    private _filter(value: string): string[] {
        const filterValue = value.toLowerCase();
        return this.exerciseCategories.filter((category) => category.category!.toLowerCase().includes(filterValue)).map((category) => category.category!);
    }

    /**
     * set unique categories on changes
     */
    ngOnChanges() {
        if (!this.existingCategories) {
            return;
        }
        this.existingCategories.forEach((category) => {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const categoryIsInArray = (exerciseCategory: ExerciseCategory, index: number, categories: ExerciseCategory[]): boolean => {
                return exerciseCategory.category === category.category;
            };
        });
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
    onSelectedColor(selectedColor: string) {
        this.selectedCategory.color = selectedColor;
        this.exerciseCategories = this.exerciseCategories.map((category) => {
            if (category.category === this.selectedCategory.category) {
                return this.selectedCategory;
            }
            return category;
        });
        this.selectedCategories.emit(this.exerciseCategories);
    }

    /**
     * set color if not selected and add exerciseCategory
     * @param event a new category was added
     */
    onItemAdd(event: MatChipInputEvent) {
        const categoryToAdd = new ExerciseCategory();
        categoryToAdd.category = (event.value || '').trim();
        // TODO: handle color selection
        if (!categoryToAdd.color) {
            const randomIndex = Math.floor(Math.random() * this.categoryColors.length);
            categoryToAdd.color = this.categoryColors[randomIndex];
        }
        if (this.exerciseCategories) {
            this.exerciseCategories.push(categoryToAdd);
        } else {
            this.exerciseCategories = [categoryToAdd];
        }
        this.selectedCategories.emit(this.exerciseCategories);

        // Clear the input value
        event.chipInput!.clear();
        this.categoryCtrl.setValue(null);
    }

    onItemSelect(event: MatAutocompleteSelectedEvent): void {
        const categoryToAdd = new ExerciseCategory();
        categoryToAdd.category = event.option.viewValue;

        this.exerciseCategories.push(categoryToAdd);
        this.categoryInput.nativeElement.value = '';
        this.categoryCtrl.setValue(null);
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {ExerciseCategory} categoryToRemove
     */
    onItemRemove(categoryToRemove: ExerciseCategory) {
        this.colorSelector.cancelColorSelector();
        this.exerciseCategories = this.exerciseCategories.filter((category) => category !== categoryToRemove);
        this.selectedCategories.emit(this.exerciseCategories);
    }
}
