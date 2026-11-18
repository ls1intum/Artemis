import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter, faSync } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { BuildJobSearchComponent } from './build-job-search.component';

@Component({
    selector: 'jhi-finished-builds-toolbar',
    templateUrl: './finished-builds-toolbar.component.html',
    imports: [BuildJobSearchComponent, TumAetUiButtonComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    host: { class: 'flex min-w-0 flex-1 items-center gap-2' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinishedBuildsToolbarComponent {
    readonly searchTerm = model<string | undefined>();
    readonly loading = input(false);
    readonly appliedFilterCount = input(0);
    readonly searchRequested = output<void>();
    readonly refreshRequested = output<void>();
    readonly filterRequested = output<void>();

    protected readonly faSync = faSync;
    protected readonly faFilter = faFilter;
}
