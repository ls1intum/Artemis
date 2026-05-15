import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { CommitInfo, ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommitsInfoGroupComponent } from './commits-info-group/commits-info-group.component';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
    imports: [TranslateDirective, CommitsInfoGroupComponent, NgStyle],
})
export class CommitsInfoComponent implements OnInit, OnDestroy {
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);

    readonly commits = input<CommitInfo[]>();
    readonly currentSubmissionHash = input<string>();
    readonly previousSubmissionHash = input<string>();
    readonly participationId = input<number>();
    readonly submissions = input<ProgrammingSubmission[]>();
    readonly exerciseProjectKey = input<string>();
    readonly isRepositoryView = input(false);

    private commitsInfoSubscription: Subscription;
    protected readonly isGroupsExpanded = signal(true);

    private readonly fetchedCommits = signal<CommitInfo[] | undefined>(undefined);

    protected readonly groupedCommits = computed<{ key: string; commits: CommitInfo[]; date: string }[]>(() => {
        const commits = this.fetchedCommits() ?? this.commits();
        if (!commits) {
            return [];
        }

        const commitGroups: { key: string; commits: CommitInfo[]; date: string }[] = [];
        let tempGroup: CommitInfo[] = [];

        const sorted = [...commits].sort((a, b) => (dayjs(b.timestamp).isAfter(dayjs(a.timestamp)) ? -1 : 1));

        for (let i = 0; i < sorted.length; i++) {
            const commit = sorted[i];
            tempGroup.push(commit);

            if (commit.result) {
                const date = dayjs(tempGroup[tempGroup.length - 1].timestamp).format('YYYY-MM-DD');
                commitGroups.push({
                    key: `${date}-${commit.author}`,
                    commits: [...tempGroup].reverse(),
                    date: date ?? '',
                });
                tempGroup = [];
            }
        }

        if (tempGroup.length > 0) {
            commitGroups.push({
                key: 'no-result',
                commits: [...tempGroup].reverse(),
                date: dayjs(tempGroup[tempGroup.length - 1].timestamp).format('YYYY-MM-DD') ?? '',
            });
        }

        return commitGroups.reverse();
    });

    ngOnInit(): void {
        if (this.commits()) {
            return;
        }
        const participationId = this.participationId();
        if (!participationId) {
            return;
        }
        this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(participationId).subscribe((commits) => {
            if (commits) {
                this.fetchedCommits.set(commits);
            }
        });
    }

    ngOnDestroy(): void {
        this.commitsInfoSubscription?.unsubscribe();
    }

    protected toggleAllExpanded() {
        this.isGroupsExpanded.update((expanded) => !expanded);
    }
}
