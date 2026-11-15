import { ChangeDetectionStrategy, Component, ViewEncapsulation, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

@Component({
    selector: 'tr[jhi-build-jobs-header]',
    templateUrl: './build-jobs-header.component.html',
    styleUrls: ['./build-jobs-table.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, HelpIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildJobsHeaderComponent {
    readonly running = input(false);
    readonly showCourseId = input(false);
}
