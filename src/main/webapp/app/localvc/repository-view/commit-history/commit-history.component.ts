import { Component } from '@angular/core';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    styleUrl: './commit-history.component.scss',
})
export class CommitHistoryComponent {
    participationId?: number;
    exerciseProjectKey?: string;
}
