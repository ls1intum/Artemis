import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TumAetUiButtonComponent, TumAetUiInputDirective, TumAetUiPaginatorComponent, TumAetUiTableDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ResultService } from 'app/exercise/result/result.service';
import { ResultTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercise/result/result.utils';
import { Course } from 'app/course/shared/entities/course.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { hasSubmittedForStatistics } from './course-statistics.utils';

type ResultState = 'graded' | 'pending' | 'notSubmitted';

/** Course result ledger; formatting is shared with the result displays elsewhere in Artemis. */
@Component({
    selector: 'jhi-course-statistics-results',
    templateUrl: './course-statistics-results.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonComponent,
        TumAetUiInputDirective,
        TumAetUiPaginatorComponent,
        TumAetUiTableDirective,
        TumAetUiTagComponent,
    ],
})
export class CourseStatisticsResultsComponent {
    private readonly resultService = inject(ResultService);
    private readonly translateService = inject(TranslateService);
    private readonly language = toSignal(this.translateService.onLangChange);

    readonly courseId = input.required<number>();
    readonly course = input<Course>();
    readonly exercises = input.required<Exercise[]>();
    readonly search = signal('');
    readonly type = signal<ExerciseType | 'all'>('all');
    readonly status = signal<ResultState | 'all'>('all');
    readonly page = signal(0);
    readonly pageSize = 20;
    readonly getIcon = getIcon;
    readonly statuses: (ResultState | 'all')[] = ['all', 'graded', 'pending', 'notSubmitted'];
    readonly types = computed(() => [
        ...new Set(
            this.exercises()
                .map((exercise) => exercise.type)
                .filter((type): type is ExerciseType => type !== undefined),
        ),
    ]);

    readonly rows = computed(() => {
        this.language();
        return this.exercises()
            .map((exercise) => {
                const participation = exercise.studentParticipations?.find((entry) => !isPracticeMode(entry));
                const result = getLatestResultOfStudentParticipation(participation, false);
                const submitted = hasSubmittedForStatistics(participation);
                const state: ResultState = result?.score !== undefined ? 'graded' : submitted ? 'pending' : 'notSubmitted';
                const context = this.course() ? cloneWith(exercise, { course: this.course() }) : exercise;
                const color = getTextColorClass(result, participation, ResultTemplateStatus.HAS_RESULT);
                return {
                    exercise,
                    scoring:
                        exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS
                            ? 'bonus'
                            : exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED
                              ? 'excluded'
                              : 'included',
                    state,
                    resultText: result?.score !== undefined ? this.resultService.getResultString(result, context, participation, true) : undefined,
                    resultColor:
                        color === 'text-state-success'
                            ? 'var(--tumaet-ui-state-success-foreground)'
                            : color === 'text-state-danger'
                              ? 'var(--tumaet-ui-state-danger-foreground)'
                              : color === 'result-orange'
                                ? 'var(--tumaet-ui-state-warning-foreground)'
                                : 'var(--tumaet-ui-muted-color)',
                    resultIcon: getResultIconClass(result, participation, result?.score !== undefined ? ResultTemplateStatus.HAS_RESULT : ResultTemplateStatus.NO_RESULT),
                    successful: result?.successful,
                };
            })
            .sort((first, second) => {
                const dueDateDifference = (first.exercise.dueDate?.valueOf() ?? Infinity) - (second.exercise.dueDate?.valueOf() ?? Infinity);
                return dueDateDifference || (first.exercise.id ?? 0) - (second.exercise.id ?? 0);
            });
    });

    readonly filteredRows = computed(() => {
        const term = this.search().trim().toLocaleLowerCase();
        return this.rows().filter((row) => {
            const searchable = [row.exercise.title ?? '', ...(row.exercise.categories ?? []).map((category) => category.category ?? '')].join(' ').toLocaleLowerCase();
            return (this.type() === 'all' || row.exercise.type === this.type()) && (this.status() === 'all' || row.state === this.status()) && (!term || searchable.includes(term));
        });
    });

    readonly currentPage = computed(() => Math.min(this.page(), Math.max(0, Math.ceil(this.filteredRows().length / this.pageSize) - 1)));
    readonly visibleRows = computed(() => this.filteredRows().slice(this.currentPage() * this.pageSize, (this.currentPage() + 1) * this.pageSize));

    constructor() {
        effect(() => {
            this.search();
            const type = this.type();
            if (type !== 'all' && !this.types().includes(type)) {
                this.type.set('all');
            }
            this.status();
            this.exercises();
            this.page.set(0);
        });
    }
}
