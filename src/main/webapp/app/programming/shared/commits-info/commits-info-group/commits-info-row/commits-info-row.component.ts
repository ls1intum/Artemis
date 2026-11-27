import { Component, computed, input, output } from '@angular/core';
import type { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { faCircle } from '@fortawesome/free-regular-svg-icons/faCircle';
import { faAngleDown } from '@fortawesome/free-solid-svg-icons/faAngleDown';
import { faAngleLeft } from '@fortawesome/free-solid-svg-icons/faAngleLeft';
import { RouterLink } from '@angular/router';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiTagComponent } from '@tumaet/ui-angular/tag';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { TruncatePipe } from 'app/foundation/pipes/truncate.pipe';

@Component({
    selector: 'jhi-commits-info-row',
    templateUrl: './commits-info-row.component.html',
    imports: [
        RouterLink,
        TumAetUiTooltipDirective,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
        ResultComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TruncatePipe,
    ],
})
export class CommitsInfoRowComponent {
    readonly commit = input.required<CommitInfo>();
    readonly currentSubmissionHash = input<string>();
    readonly previousSubmissionHash = input<string>();
    readonly exerciseProjectKey = input<string>();
    readonly isRepositoryView = input(false);
    readonly rowNumber = input<number>();
    readonly isExpanded = input.required<boolean>();
    readonly pushNumber = input.required<number>();
    readonly firstCommit = input.required<boolean>();
    readonly groupCommitCount = input.required<number>();
    readonly groupCommitIndex = input.required<number>();
    readonly toggleExpandEvent = output<void>();

    /** An expanded group is shaded, and its last commit closes the group with a line. */
    protected readonly rowClass = computed(() => {
        if (!this.isExpanded()) {
            return '';
        }
        const isLastOfGroup = this.groupCommitIndex() === this.groupCommitCount() - 1;
        return isLastOfGroup ? 'bg-(--light) dark:bg-white/5 border-b! border-(--border-color)!' : 'bg-(--light) dark:bg-white/5';
    });

    onToggleExpand() {
        this.toggleExpandEvent.emit(undefined);
    }

    readonly faCircle = faCircle;
    readonly faAngleLeft = faAngleLeft;
    readonly faAngleDown = faAngleDown;
}
