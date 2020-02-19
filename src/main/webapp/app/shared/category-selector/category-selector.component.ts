import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { ExerciseCategory } from 'app/entities/exercise.model';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';

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

    ngOnChanges(changes: SimpleChanges) {
        if (!this.existingCategories) {
            return;
        }
        this.existingCategories.forEach(category => {
            const categoryIsInArray = (el: ExerciseCategory, index: number, categories: ExerciseCategory[]): boolean => {
                return el.category === category.category;
            };
            if (!this.uniqueCategories.find(categoryIsInArray)) {
                this.uniqueCategories.push(category);
            }
        });
    }

    openColorSelector(event: MouseEvent, tagItem: ExerciseCategory) {
        this.selectedCategory = tagItem;
        this.colorSelector.openColorSelector(event);
    }

    onSelectedColor(selectedColor: string) {
        this.selectedCategory.color = selectedColor;
        this.exerciseCategories = this.exerciseCategories.map(el => {
            if (el.category === this.selectedCategory.category) {
                return this.selectedCategory;
            }
            return el;
        });
        this.selectedCategories.emit(this.exerciseCategories);
    }

    onItemAdded(exerciseCategory: ExerciseCategory) {
        if (!exerciseCategory.color) {
            const randomIndex = Math.floor(Math.random() * this.categoryColors.length);
            exerciseCategory.color = this.categoryColors[randomIndex];
        }
        this.exerciseCategories.push(exerciseCategory);
        this.selectedCategories.emit(this.exerciseCategories);
    }

    onItemRemove(tagItem: ExerciseCategory) {
        this.colorSelector.cancelColorSelector();
        this.exerciseCategories = this.exerciseCategories.filter(el => el !== tagItem);
        this.selectedCategories.emit(this.exerciseCategories);
    }
}
