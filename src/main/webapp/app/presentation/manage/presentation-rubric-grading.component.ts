import { Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiInputDirective, TumAetUiMessageComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationRubricCriterion } from 'app/presentation/shared/entities/presentation-assessment.model';
import { rubricLevelKey } from 'app/presentation/shared/presentation-rubric.util';
import { RatingForm, RatingsForm, rubricPreviewScore } from 'app/presentation/manage/presentation-rubric-rating-form';

@Component({
    selector: 'jhi-presentation-rubric-grading',
    templateUrl: './presentation-rubric-grading.component.html',
    imports: [ReactiveFormsModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent, TumAetUiInputDirective, TumAetUiMessageComponent],
})
export class PresentationRubricGradingComponent {
    readonly criteria = input.required<PresentationRubricCriterion[]>();
    /** The ratings, one per criterion; the enclosing dialog owns them to check their validity and read them when saving. */
    readonly form = input.required<RatingsForm>();
    /** The course, whose settings round the overall result. */
    readonly course = input<Course>();

    protected readonly faCircleInfo = faCircleInfo;
    protected readonly rubricLevelKey = rubricLevelKey;

    /** The level positions of a criterion, highest first, as the rating scale is read from best to worst. */
    protected positionsHighestFirst(criterion: PresentationRubricCriterion): number[] {
        return criterion.levels.map((_, position) => position).reverse();
    }

    protected selectLevel(rating: RatingForm, levelId: number | undefined): void {
        rating.controls.levelId.setValue(levelId);
        rating.markAsDirty();
    }

    protected overallResult(): number | undefined {
        const score = rubricPreviewScore(this.criteria(), this.form());
        return score === undefined ? undefined : roundValueSpecifiedByCourseSettings(score, this.course());
    }
}
