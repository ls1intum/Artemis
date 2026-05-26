import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faExclamationTriangle, faSort } from '@fortawesome/free-solid-svg-icons';
import { SortDirective } from '../../sort/directive/sort.directive';
import { SortByDirective } from '../../sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TooltipModule } from 'primeng/tooltip';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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

    readonly faSort = faSort;
    readonly faExclamationTriangle = faExclamationTriangle;

    constructor() {
        // Re-sorts whenever the data, predicate or sort direction changes. Sorts in place; signal
        // identity equality prevents an infinite loop because the array reference does not change.
        effect(() => {
            const data = this.tutorsData();
            this.sortService.sortByProperty(data, this.sortPredicate(), this.reverseOrder());
        });
    }
}
