import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { Subscription } from 'rxjs';
import { Result } from 'app/entities/result.model';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
})
export class CommitsInfoComponent implements OnInit, OnDestroy {
    @Input() commits?: CommitInfo[];
    @Input() users: Map<string, User>;
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() participationId?: number;
    @Input() submissions?: ProgrammingSubmission[];
    @Input() exerciseProjectKey?: string;
    @Input() results?: Result[];
    @Input() isRepositoryView = false;
    private commitHashURLTemplate: string;
    private commitsInfoSubscription: Subscription;
    private profileInfoSubscription: Subscription;
    localVC = false;
    courseId: number;
    exerciseId: number;
    paramSub: Subscription;

    constructor(
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private profileService: ProfileService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.exerciseId = Number(params['exerciseId']);
        });
        if (!this.commits) {
            if (this.participationId) {
                this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
                    this.commits = this.sortCommitsByTimestampDesc(commits);
                });
            }
        }
        this.results = this.results?.sort((a, b) => (dayjs(b.completionDate).isAfter(dayjs(a.completionDate)) ? 1 : -1));
        // Get active profiles, to distinguish between Bitbucket and GitLab, and to check if localVC is enabled
        this.profileInfoSubscription = this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.commitHashURLTemplate = profileInfo.commitHashURLTemplate;
            this.localVC = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });
    }

    ngOnDestroy(): void {
        this.commitsInfoSubscription?.unsubscribe();
        this.profileInfoSubscription?.unsubscribe();
        this.paramSub.unsubscribe();
    }

    sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
    }

    getCommitUrl(commitInfo: CommitInfo) {
        const submission = this.findSubmissionForCommit(commitInfo, this.submissions);
        return createCommitUrl(this.commitHashURLTemplate, this.exerciseProjectKey, submission?.participation, submission);
    }

    getCommitUrlForRepositoryView(commitInfo: CommitInfo) {
        return `/courses/${this.courseId}/programming-exercises/${this.exerciseId}/repository/${this.participationId}/commit-history/` + commitInfo.hash;
    }

    private findSubmissionForCommit(commitInfo: CommitInfo, submissions: ProgrammingSubmission[] | undefined) {
        return submissions?.find((submission) => submission.commitHash === commitInfo.hash);
    }

    protected readonly RepositoryViewComponent = RepositoryViewComponent;
}
