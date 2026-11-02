import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PublicUsageStatistics, UsageSemester } from 'app/core/usage-statistics/usage-statistics.model';
import { UsageStatisticsService } from 'app/core/usage-statistics/usage-statistics.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Exercise types in the order they are shown. The keys match the server's exercise type values. */
const EXERCISE_TYPES = ['programming', 'quiz', 'text', 'modeling', 'file-upload'] as const;

/**
 * The public usage dashboard. Shows aggregated, anonymous numbers of the last semesters to anybody, without login.
 */
@Component({
    selector: 'jhi-usage-dashboard',
    templateUrl: './usage-dashboard.component.html',
    styleUrl: './usage-dashboard.component.scss',
    imports: [DecimalPipe, TranslateDirective, ArtemisTranslatePipe],
})
export class UsageDashboardComponent implements OnInit {
    private readonly usageStatisticsService = inject(UsageStatisticsService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly exerciseTypes = EXERCISE_TYPES;

    protected readonly statistics = signal<PublicUsageStatistics | undefined>(undefined);
    protected readonly failed = signal(false);

    /** The semester shown in the headline cards. The server returns the newest first. */
    protected readonly selectedLabel = signal<string | undefined>(undefined);
    protected readonly semesters = computed<UsageSemester[]>(() => this.statistics()?.semesters ?? []);
    protected readonly selected = computed<UsageSemester | undefined>(() => {
        const label = this.selectedLabel();
        return this.semesters().find((semester) => semester.label === label) ?? this.semesters()[0];
    });

    /** The total number of exercises of the selected semester across all types. */
    protected readonly totalExercises = computed(() => Object.values(this.selected()?.exercisesByType ?? {}).reduce((sum, count) => sum + count, 0));

    /** The largest value of the active students bar chart, so that every bar can be sized relative to it. */
    protected readonly maxActiveStudents = computed(() => Math.max(1, ...this.semesters().map((semester) => semester.activeStudents)));

    ngOnInit(): void {
        this.usageStatisticsService
            .getUsageStatistics()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (statistics) => this.statistics.set(statistics),
                error: () => this.failed.set(true),
            });
    }

    protected select(label: string): void {
        this.selectedLabel.set(label);
    }

    /** Bar width in percent relative to the semester with the most active students. */
    protected barWidth(semester: UsageSemester): number {
        return Math.round((semester.activeStudents / this.maxActiveStudents()) * 100);
    }
}
