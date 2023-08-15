import { Component, Input } from '@angular/core';
import { CommitInfo } from 'app/entities/programming-submission.model';

@Component({
    selector: 'jhi-commits-info',
    templateUrl: './commits-info.component.html',
    styleUrls: ['./commits-info.component.scss'],
})
export class CommitsInfoComponent {
    @Input() commits: CommitInfo[];
    private sortByTimestamp(commits: CommitInfo[]): CommitInfo[] {
        return commits.sort((a, b) => b.timestamp!.unix() - a.timestamp!.unix());
    }
}
