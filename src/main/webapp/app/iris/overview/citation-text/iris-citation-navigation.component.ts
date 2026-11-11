import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Native, named citation controls, mounted inside the generated markdown's summary. */
@Component({
    selector: 'jhi-iris-citation-navigation',
    imports: [TumAetUiButtonComponent, TumAetUiTooltipDirective, ArtemisTranslatePipe],
    template: `
        <tumaet-ui-button
            size="small"
            severity="secondary"
            variant="text"
            [rounded]="true"
            [icon]="faChevronLeft"
            [ariaLabel]="'artemisApp.iris.citation.previous' | artemisTranslate"
            [tumAetUiTooltip]="'artemisApp.iris.citation.previous' | artemisTranslate"
            [tumAetUiTooltipDescribesHost]="false"
            (clicked)="$event.stopPropagation(); select(-1)"
        />
        <span aria-live="polite">{{ currentIndex() + 1 }} / {{ summaryCount() }}</span>
        <tumaet-ui-button
            size="small"
            severity="secondary"
            variant="text"
            [rounded]="true"
            [icon]="faChevronRight"
            [ariaLabel]="'artemisApp.iris.citation.next' | artemisTranslate"
            [tumAetUiTooltip]="'artemisApp.iris.citation.next' | artemisTranslate"
            [tumAetUiTooltipDescribesHost]="false"
            (clicked)="$event.stopPropagation(); select(1)"
        />
    `,
    host: { class: 'inline-flex items-center gap-2' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisCitationNavigationComponent {
    readonly summaryCount = input.required<number>();
    readonly indexChanged = output<number>();
    protected readonly currentIndex = signal(0);
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    protected select(direction: number): void {
        const count = this.summaryCount();
        this.currentIndex.update((index) => (index + direction + count) % count);
        this.indexChanged.emit(this.currentIndex());
    }
}
