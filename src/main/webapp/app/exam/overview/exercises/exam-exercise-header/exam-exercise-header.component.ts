import { Component, input } from '@angular/core';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';

/** The part of an exercise the header shows; a quiz configuration offers it as well as a full exercise does. */
export interface ExamExerciseHeaderExercise {
    exerciseGroup?: ExerciseGroup;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
}

/**
 * The title row on top of every page of a running exam: for an exercise the group's title and its points, for a page without
 * an exercise (the overview) a translated title, and in the projected slot on the right the page's primary action (the save
 * button of most exercise types). All pages share it so the title, its size and the rule below it cannot drift apart.
 *
 * Two optional slots sit inside the same rule: an element with the attribute `headerLeading` is shown before the title (the
 * toggle of a collapsed sidebar) and one with `headerTitleSuffix` right after it (a tag). To fill one conditionally, put the
 * `@if` inside an `<ng-container headerLeading>`: an `@if` that carries the element itself projects it only if the element is
 * its single root node, which preserved whitespace in the template of the host breaks (and then it lands in the action slot).
 */
@Component({
    selector: 'jhi-exam-exercise-header',
    templateUrl: './exam-exercise-header.component.html',
    imports: [TranslateDirective, IncludedInScoreBadgeComponent],
})
export class ExamExerciseHeaderComponent {
    protected readonly IncludedInOverallScore = IncludedInOverallScore;

    readonly exercise = input<ExamExerciseHeaderExercise>();
    readonly titleKey = input<string>();
}
