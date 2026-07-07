import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CardModule } from 'primeng/card';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-iris-kpi-card',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CardModule, TranslateDirective, HelpIconComponent],
    templateUrl: './iris-kpi-card.component.html',
    styleUrls: ['./iris-kpi-card.component.scss'],
})
export class IrisKpiCardComponent {
    /** i18n key for the card title. */
    readonly titleKey = input.required<string>();

    /** i18n key for the help tooltip explaining the metric. */
    readonly helpKey = input.required<string>();
}
