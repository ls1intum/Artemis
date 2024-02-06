import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { Subscription } from 'rxjs';
import { Result } from 'app/entities/result.model';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Router } from '@angular/router';
import { faCircle } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
})
export class CommitsInfoComponent implements OnInit, OnDestroy {
    @Input() commits?: CommitInfo[];
    @Input() users?: Map<string, User>;
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() participationId?: number;
    @Input() submissions?: ProgrammingSubmission[];
    @Input() exerciseProjectKey?: string;
    @Input() resultsMap?: Map<string, Result>;
    @Input() isRepositoryView = false;
    private commitHashURLTemplate: string;
    private commitsInfoSubscription: Subscription;
    private profileInfoSubscription: Subscription;
    localVC = false;
    courseId: number;
    exerciseId: number;
    paramSub: Subscription;
    routerLink: string;

    faCircle = faCircle;

    constructor(
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private profileService: ProfileService,
        private route: ActivatedRoute,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.routerLink = this.router.url;
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.exerciseId = Number(params['exerciseId']);
        });
        if (!this.commits) {
            if (this.participationId) {
                this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
                    this.commits = this.sortCommitsByTimestampDesc(commits);
                    this.setCommitDetails();
                });
            }
        }
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

    private setCommitDetails() {
        for (const commit of this.commits!) {
            commit.user = this.users?.get(commit.author!);
            commit.result = this.resultsMap?.get(commit.hash!);
            const submission = this.findSubmissionForCommit(commit, this.submissions);
            commit.commitUrl = createCommitUrl(this.commitHashURLTemplate, this.exerciseProjectKey, submission?.participation, submission);
        }
    }

    private sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
    }

    private findSubmissionForCommit(commitInfo: CommitInfo, submissions: ProgrammingSubmission[] | undefined) {
        return submissions?.find((submission) => submission.commitHash === commitInfo.hash);
    }
}
