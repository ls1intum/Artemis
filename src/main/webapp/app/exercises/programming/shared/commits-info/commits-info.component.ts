import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
})
export class CommitsInfoComponent implements OnInit, OnDestroy {
    @Input() commits?: CommitInfo[];
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() participationId?: number;
    @Input() submissions?: ProgrammingSubmission[];
    @Input() exerciseProjectKey?: string;
    @Input() isRepositoryView = false;

    private commitHashURLTemplate: string;
    private commitsInfoSubscription: Subscription;
    private profileInfoSubscription: Subscription;
    protected isGroupsExpanded = true;
    protected groupedCommits: { key: string; commits: CommitInfo[]; date: string }[] = [];

    localVC = false;

    constructor(
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private profileService: ProfileService,
    ) {}

    ngOnInit(): void {
        if (!this.commits) {
            if (this.participationId) {
                this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
                    if (commits) {
                        this.commits = commits;
                    }
                });
            }
        }
        // Get active profiles, to distinguish between VC systems, and to check if localVC is enabled
        this.profileInfoSubscription = this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.commitHashURLTemplate = profileInfo.commitHashURLTemplate;
            this.localVC = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });

        this.setCommitDetails();
        this.groupCommits();
    }

    ngOnDestroy(): void {
        this.commitsInfoSubscription?.unsubscribe();
        this.profileInfoSubscription?.unsubscribe();
    }

    private setCommitDetails() {
        if (this.commits && this.submissions) {
            for (const commit of this.commits) {
                const submission = this.findSubmissionForCommit(commit, this.submissions);
                commit.commitUrl = createCommitUrl(this.commitHashURLTemplate, this.exerciseProjectKey, submission?.participation, submission);
            }
        }
    }

    private findSubmissionForCommit(commitInfo: CommitInfo, submissions: ProgrammingSubmission[] | undefined) {
        return submissions?.find((submission) => submission.commitHash === commitInfo.hash);
    }

    /**
     * Groups commits together that were pushed in one batch.
     * As we don't have a direct indicator whether commits were pushed together,
     * we infer groups based on the presence of a 'result' on a commit.
     */
    private groupCommits() {
        if (!this.commits) {
            return;
        }

        const commitGroups: { key: string; commits: CommitInfo[]; date: string }[] = [];
        let tempGroup: CommitInfo[] = [];

        this.commits = this.commits?.sort((a, b) => (dayjs(b.timestamp).isAfter(dayjs(a.timestamp)) ? -1 : 1));

        for (let i = 0; i < this.commits.length; i++) {
            const commit = this.commits[i];
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

        this.groupedCommits = commitGroups.reverse();
    }

    protected toggleAllExpanded() {
        this.isGroupsExpanded = !this.isGroupsExpanded;
    }
}
