import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
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
    protected readonly groupedCommits = signal<{ key: string; commits: CommitInfo[]; date: string }[]>([]);
    /**
     * Locally-managed commits state. Defaults to the input value; updated when fetched via the service.
     */
    protected readonly internalCommits = signal<CommitInfo[] | undefined>(undefined);

    ngOnInit(): void {
        const initialCommits = this.commits();
        if (!initialCommits) {
            const participationId = this.participationId();
            if (participationId) {
                this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitHistoryForParticipation(participationId).subscribe((commits) => {
                    if (commits) {
                        this.internalCommits.set(commits);
                    }
                    this.groupCommits();
                });
            }
        } else {
            this.internalCommits.set(initialCommits);
            this.groupCommits();
        }
    }

    ngOnDestroy(): void {
        this.commitsInfoSubscription?.unsubscribe();
    }

    /**
     * Groups commits together that were pushed in one batch.
     * As we don't have a direct indicator whether commits were pushed together,
     * we infer groups based on the presence of a 'result' on a commit.
     */
    private groupCommits() {
        const commits = this.internalCommits();
        if (!commits) {
            return;
        }

        const commitGroups: { key: string; commits: CommitInfo[]; date: string }[] = [];
        let tempGroup: CommitInfo[] = [];

        const sorted = [...commits].sort((a, b) => (dayjs(b.timestamp).isAfter(dayjs(a.timestamp)) ? -1 : 1));
        this.internalCommits.set(sorted);

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

        this.groupedCommits.set(commitGroups.reverse());
    }

    protected toggleAllExpanded() {
        this.isGroupsExpanded.update((expanded) => !expanded);
    }
}
