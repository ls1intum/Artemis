import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ChartPlot } from './tumaet-ui-chart.frame';

/** Keeps cartesian series inside the plot while allowing axes and data labels to remain outside it. */
@Component({
    // eslint-disable-next-line @angular-eslint/component-selector -- SVG definitions must use a native SVG host rather than a custom HTML element.
    selector: 'defs[tumAetUiChartClipPath]',
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <svg:clipPath [attr.id]="clipId()">
            <svg:rect [attr.width]="plot().width" [attr.height]="plot().height" />
        </svg:clipPath>
    `,
})
export class TumAetUiChartClipPathComponent {
    readonly clipId = input.required<string>();
    readonly plot = input.required<ChartPlot>();
}
