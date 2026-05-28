import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TutorLeaderboardElement } from 'app/ui/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SortService } from 'app/foundation/service/sort.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faExclamationTriangle, faSort } from '@fortawesome/free-solid-svg-icons';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TooltipModule } from 'primeng/tooltip';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutor-leaderboard',
    templateUrl: './tutor-leaderboard.component.html',
    imports: [SortDirective, SortByDirective, TranslateDirective, FaIconComponent, TooltipModule, RouterLink, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorLeaderboardComponent {
    private sortService = inject(SortService);

    readonly tutorsData = input<TutorLeaderboardElement[]>([]);
    readonly courseInput = input<Course | undefined>(undefined, { alias: 'course' }); // eslint-disable-line @angular-eslint/no-input-rename
    readonly exercise = input<Exercise | undefined>();
    readonly exam = input<Exam | undefined>();

    /** Resolved course: derived from the exercise when available, otherwise falls back to the input course. */
    readonly course = computed<Course | undefined>(() => {
        const exercise = this.exercise();
        if (exercise) {
            return getCourseFromExercise(exercise);
        }
        return this.courseInput();
    });

    readonly isExerciseDashboard = computed(() => !!(this.exercise() && this.course()));
    readonly isExamMode = computed(() => !!this.exam());

    readonly sortPredicate = signal<string>('points');
    readonly reverseOrder = signal<boolean>(false);

    /**
     * Rows sorted by the current predicate and direction. Returns a fresh array (rather than sorting
     * the input in place) so the new reference re-renders the `@for` and the parent-supplied input is
     * not mutated as a side effect.
     */
    readonly sortedTutorsData = computed<TutorLeaderboardElement[]>(() => this.sortService.sortByProperty([...this.tutorsData()], this.sortPredicate(), this.reverseOrder()));

    readonly faSort = faSort;
    readonly faExclamationTriangle = faExclamationTriangle;
}
