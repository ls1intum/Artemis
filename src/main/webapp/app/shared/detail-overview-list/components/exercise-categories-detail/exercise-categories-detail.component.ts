import { Component, computed, input } from '@angular/core';
import { NgStyle } from '@angular/common';
import type { ExerciseCategoriesDetail } from 'app/shared/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/components/no-data/no-data-component';

@Component({
    selector: 'jhi-exercise-categories-detail',
    templateUrl: 'exercise-categories-detail.component.html',
    styleUrls: ['exercise-categories-detail.component.scss'],
    imports: [NgStyle, NoDataComponent],
})
export class ExerciseCategoriesDetailComponent {
    detail = input.required<ExerciseCategoriesDetail>();

    hasValidCategories = computed(() => !!this.detail().data.categories?.some((category) => !!category.category));
}
