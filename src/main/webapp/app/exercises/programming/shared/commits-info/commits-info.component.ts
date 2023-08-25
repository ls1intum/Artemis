import { Component, Input, OnInit } from '@angular/core';
import { CommitInfo } from 'app/entities/programming-submission.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
    styleUrls: ['./commits-info.component.scss'],
})
export class CommitsInfoComponent implements OnInit {
    @Input() commits?: CommitInfo[];
    @Input() activeCommitHash?: string;
    @Input() participationId?: number;

    constructor(private programmingExerciseParticipationService: ProgrammingExerciseParticipationService) {}

    ngOnInit(): void {
        if (!this.commits) {
            if (this.participationId) {
                this.programmingExerciseParticipationService.retrieveCommitsInfoForParticipation(this.participationId).subscribe((commits) => {
                    this.commits = this.sortCommitsByTimestampDesc(commits);
                });
            }
        }
    }
    sortCommitsByTimestampDesc(commitInfos: CommitInfo[]) {
        return commitInfos.sort((a, b) => dayjs(b.timestamp!).unix() - dayjs(a.timestamp!).unix());
    }
}
