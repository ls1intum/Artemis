import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * The link from the toolbar of the programming exercise detail page to the comprehension check dashboard. The toolbar is still a row of Bootstrap btn-sm
 * buttons, which stretch to the height of the tallest one, so the link takes their size, corners and white label, as the Hyperion button in the same row
 * does. The utilities live here because the detail page is not a Tailwind source.
 */
@Component({
    selector: 'jhi-metis-dashboard-link',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, FaIconComponent, TumAetUiButtonDirective, TranslateDirective],
    template: `
        <a
            tumAetUiButton
            severity="info"
            size="small"
            class="[--tumaet-ui-state-info-contrast:var(--white)] gap-1! rounded-[0.1rem]! px-2! py-1! text-[0.7875rem]! leading-normal!"
            [routerLink]="link()"
            data-testid="comprehension-check-button"
        >
            <fa-icon [icon]="faGraduationCap" />
            <span jhiTranslate="artemisApp.metis.dashboard.button"></span>
        </a>
    `,
})
export class MetisDashboardLinkComponent {
    /** The route of the dashboard of the exercise. */
    readonly link = input.required<string[]>();

    protected readonly faGraduationCap = faGraduationCap;
}
