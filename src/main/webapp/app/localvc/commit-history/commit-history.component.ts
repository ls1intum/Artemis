import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { tap } from 'rxjs/operators';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    styleUrl: './commit-history.component.scss',
})
export class CommitHistoryComponent implements OnInit, OnDestroy {
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;

    studentParticipation: StudentParticipation;
    participationId: number;
    paramSub: Subscription;
    commits: CommitInfo[];
    commitsInfoSubscription: Subscription;

    constructor(
        private route: ActivatedRoute,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.participationId = Number(params['participationId']);
            this.loadParticipation();
        });
    }

    ngOnDestroy() {
        this.paramSub?.unsubscribe();
        this.commitsInfoSubscription?.unsubscribe();
    }

    private loadParticipation() {
        this.programmingExerciseParticipationService
            .getStudentParticipationWithAllResults(this.participationId)
            .pipe(
                tap((participation) => {
                    this.studentParticipation = participation;
                    this.studentParticipation.results?.forEach((result) => {
                        result.participation = participation!;
                    });
                }),
            )
            .subscribe({
                next: () => {
                    this.handleCommits();
                },
            });
    }

    private handleCommits() {
        this.commitsInfoSubscription = this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
            this.commits = [];
            const sortedCommits = this.sortCommitsByTimestampDesc(commits);
            for (let i = 0; i < sortedCommits.length - 1; i++) {
                const hasSubmission = this.studentParticipation.submissions?.some((submission) => {
                    const programmingSubmission = submission as ProgrammingSubmission;
                    return programmingSubmission.commitHash === sortedCommits[i].hash;
                });
                if (hasSubmission) {
                    this.commits.push(sortedCommits[i]);
                }
            }
            // push template commit extra as it has no submission
            this.commits.push(sortedCommits[sortedCommits.length - 1]);
            this.setCommitDetails();
        });
    }

    private setCommitDetails() {
        this.commits.forEach((commit) => {
            if (this.studentParticipation.student?.name! === commit.author) {
                commit.user = this.studentParticipation.student!;
            }
            this.studentParticipation.team?.students?.forEach((student) => {
                if (student.name! === commit.author) {
                    commit.user = student;
                }
            });
            this.studentParticipation.results?.forEach((result) => {
                const submission = result.submission as ProgrammingSubmission;
                if (submission) {
                    if (submission.commitHash === commit.hash) {
                        commit.result = result;
                    }
                }
            });
        });
    }

    private sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => (dayjs(b.timestamp!).isAfter(dayjs(a.timestamp!)) ? 1 : -1));
    }
}
