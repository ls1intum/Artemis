import { Component } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseFaqAccordionComponent } from 'app/overview/course-faq/course-faq-accordion-component';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: [],
    standalone: true,
    imports: [ArtemisSharedModule, CustomExerciseCategoryBadgeComponent, ArtemisSharedComponentModule, CourseFaqAccordionComponent, SearchFilterComponent],
})
export class CourseLearnerProfileComponent {}
