import { Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { startWith, switchMap } from 'rxjs';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiInputDirective, TumAetUiMessageComponent, TumAetUiRadioButtonComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationRubricCriterion } from 'app/presentation/shared/entities/presentation-assessment.model';
import { rubricLevelKey } from 'app/presentation/shared/presentation-rubric.util';
import { levelPositionsHighestFirst } from 'app/presentation/shared/presentation.util';
import { RatingsForm, rubricPreviewScore } from 'app/presentation/manage/presentation-rubric-rating-form';
import { PresentationLabelDirective } from 'app/presentation/manage/presentation-label.directive';

@Component({
    selector: 'jhi-presentation-rubric-grading',
    templateUrl: './presentation-rubric-grading.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiRadioButtonComponent,
        PresentationLabelDirective,
    ],
})
export class PresentationRubricGradingComponent {
    readonly criteria = input.required<PresentationRubricCriterion[]>();
    /** The ratings, one per criterion; the enclosing dialog owns them to check their validity and read them when saving. */
    readonly form = input.required<RatingsForm>();
    /** The course, whose settings round the overall result. */
    readonly course = input<Course>();

    protected readonly faCircleInfo = faCircleInfo;

    /** Each criterion with its level positions highest first and the translation key of each level, in the order of the rubric. */
    protected readonly criterionViews = computed(() =>
        this.criteria().map((criterion) => ({
            criterion,
            positionsHighestFirst: levelPositionsHighestFirst(criterion.levels.length),
            levelKeys: criterion.levels.map((_, position) => rubricLevelKey(criterion.scale, position)),
        })),
    );
    /** Emits on every change of the ratings, so that the overall result follows the form. */
    private readonly formChange = toSignal(toObservable(this.form).pipe(switchMap((form) => form.events.pipe(startWith(undefined)))));

    protected readonly overallResult = computed(() => {
        this.formChange();
        const score = rubricPreviewScore(this.criteria(), this.form());
        return score === undefined ? undefined : roundValueSpecifiedByCourseSettings(score, this.course());
    });
}
