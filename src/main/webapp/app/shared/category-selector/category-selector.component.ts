import { Component, EventEmitter, Input, OnChanges, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { TagModel } from 'ngx-chips/core/accessor';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-category-selector',
    templateUrl: './category-selector.component.html',
    styleUrls: ['./category-selector.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class CategorySelectorComponent implements OnChanges {
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;
    categoryColors = DEFAULT_COLORS;
    selectedCategory: ExerciseCategory;
    @Input() exerciseCategories: ExerciseCategory[];
    @Input() existingCategories: ExerciseCategory[];
    @Output() selectedCategories = new EventEmitter<ExerciseCategory[]>();
    uniqueCategories: ExerciseCategory[] = [];
    private readonly colorSelectorHeight = 150;

    /**
     * set unique categories on changes
     */
    ngOnChanges() {
        if (!this.existingCategories) {
            return;
        }
        this.existingCategories.forEach((category) => {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const categoryIsInArray = (el: ExerciseCategory, index: number, categories: ExerciseCategory[]): boolean => {
                return el.category === category.category;
            };
            if (!this.uniqueCategories.find(categoryIsInArray)) {
                this.uniqueCategories.push(category);
            }
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
        this.exerciseCategories = this.exerciseCategories.map((el) => {
            if (el.category === this.selectedCategory.category) {
                return this.selectedCategory;
            }
            return el;
        });
        this.selectedCategories.emit(this.exerciseCategories);
    }

    /**
     * set color if not selected and add exerciseCategory
     * @param categoryTag the tag of the exercise category
     */
    onItemAdded(categoryTag: TagModel) {
        const exerciseCategory = categoryTag as ExerciseCategory;
        if (!exerciseCategory.color) {
            const randomIndex = Math.floor(Math.random() * this.categoryColors.length);
            exerciseCategory.color = this.categoryColors[randomIndex];
        }
        if (this.exerciseCategories) {
            this.exerciseCategories.push(exerciseCategory);
        } else {
            this.exerciseCategories = [exerciseCategory];
        }
        this.selectedCategories.emit(this.exerciseCategories);
    }

    /**
     * cancel colorSelector and remove exerciseCategory
     * @param {ExerciseCategory} tagItem
     */
    onItemRemove(tagItem: TagModel) {
        const categoryToRemove = tagItem as ExerciseCategory;
        this.colorSelector.cancelColorSelector();
        this.exerciseCategories = this.exerciseCategories.filter((category) => category !== categoryToRemove);
        this.selectedCategories.emit(this.exerciseCategories);
    }
}
