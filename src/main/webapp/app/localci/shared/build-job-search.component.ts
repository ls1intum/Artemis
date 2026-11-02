import { ChangeDetectionStrategy, Component, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiButtonComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'div[jhi-build-job-search]',
    imports: [FormsModule, TumAetUiButtonComponent, TumAetUiInputDirective, ArtemisTranslatePipe, HelpIconComponent, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <input
            [attr.aria-label]="'artemisApp.buildQueue.filter.search.title' | artemisTranslate"
            tumAetUiInput
            type="text"
            name="searchTerm"
            id="field_searchTerm"
            [(ngModel)]="searchTerm"
            (focusout)="searchRequested.emit()"
        />
        <jhi-help-icon text="artemisApp.buildQueue.filter.search.tooltip" />
        <tumaet-ui-button size="small" (clicked)="searchRequested.emit()">
            <span jhiTranslate="artemisApp.buildQueue.filter.search.title"></span>
        </tumaet-ui-button>
    `,
})
export class BuildJobSearchComponent {
    readonly searchTerm = model<string | undefined>();
    readonly searchRequested = output<void>();
}
