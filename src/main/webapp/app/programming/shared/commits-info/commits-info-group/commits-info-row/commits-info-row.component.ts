import { Component, EventEmitter, Input, Output } from '@angular/core';
import type { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { faAngleDown, faAngleLeft } from '@fortawesome/free-solid-svg-icons';
import { RouterLink } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';

@Component({
    selector: 'jhi-commits-info-row',
    templateUrl: './commits-info-row.component.html',
    imports: [RouterLink, NgbTooltip, ResultComponent, FaIconComponent, TranslateDirective, ArtemisDatePipe, TruncatePipe],
})
export class CommitsInfoRowComponent {
    @Input() commit: CommitInfo;
    @Input() currentSubmissionHash?: string;
    @Input() previousSubmissionHash?: string;
    @Input() exerciseProjectKey?: string;
    @Input() isRepositoryView = false;
    @Input() rowNumber: number;
    @Input() isExpanded: boolean;
    @Input() pushNumber: number;
    @Input() firstCommit: boolean;
    @Input() groupCommitCount: number;
    @Input() groupCommitIndex: number;
    @Output() toggleExpandEvent = new EventEmitter<void>();

    onToggleExpand() {
        this.toggleExpandEvent.emit();
    }

    readonly faCircle = faCircle;
    readonly faAngleLeft = faAngleLeft;
    readonly faAngleDown = faAngleDown;
}
