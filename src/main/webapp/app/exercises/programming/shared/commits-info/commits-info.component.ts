import { Component, Input, OnInit } from '@angular/core';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
    styleUrls: ['./commits-info.component.scss'],
})
export class CommitsInfoComponent implements OnInit {
    @Input() commits?: CommitInfo[];
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() participationId?: number;
    @Input() submissions?: ProgrammingSubmission[];
    @Input() exerciseProjectKey?: string;
    private commitHashURLTemplate: string;
    localVC = false;

    constructor(
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private profileService: ProfileService,
    ) {}

    ngOnInit(): void {
        if (!this.commits) {
            if (this.participationId) {
                this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
                    this.commits = this.sortCommitsByTimestampDesc(commits);
                });
            }
        }
        // Get active profiles, to distinguish between Bitbucket and GitLab, and to check if localVC is enabled
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.commitHashURLTemplate = profileInfo.commitHashURLTemplate;
            this.localVC = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });
    }

    sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => dayjs(b.timestamp!).unix() - dayjs(a.timestamp!).unix());
    }

    getCommitUrl(commitInfo: CommitInfo) {
        const submission = this.findSubmissionForCommit(commitInfo, this.submissions);
        return createCommitUrl(this.commitHashURLTemplate, this.exerciseProjectKey, submission?.participation, submission);
    }

    private findSubmissionForCommit(commitInfo: CommitInfo, submissions: ProgrammingSubmission[] | undefined) {
        if (submissions) {
            return submissions.find((submission) => submission.commitHash === commitInfo.hash);
        }
    }
}
