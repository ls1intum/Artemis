import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import DOMPurify from 'dompurify';
import { TumAetUiProseComponent, TumAetUiProseDensity } from '@tumaet/ui-angular';

import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';

/** Generated documents may contain prose and code, but not stylesheets or interactive controls. */
const FORBIDDEN_TAGS = ['style', 'form', 'input', 'button', 'select', 'option', 'optgroup', 'textarea', 'label', 'fieldset', 'legend'];

/** Uses Artemis markdown rendering and TUM UI typography, with stricter sanitisation for generated content. */
@Component({
    selector: 'jhi-hyperion-markdown',
    template: `<div tumAetUiProse class="hyperion-markdown-body" [density]="density()" [innerHTML]="rendered() | safeHtml"></div>`,
    styles: `
        /* Keep styled descendants inside the document; scrolling preserves access to wide tables and formulas. */
        .hyperion-markdown-body {
            contain: paint;
            overflow: auto;
        }
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[attr.data-slot]': '"markdown"' },
    imports: [SafeHtmlPipe, TumAetUiProseComponent],
})
export class HyperionMarkdownComponent {
    readonly markdown = input<string | undefined>();
    /** `compact` tightens the block spacing for prose inside a docked panel. */
    readonly density = input<TumAetUiProseDensity>('comfortable');

    protected readonly rendered = computed(() => DOMPurify.sanitize(htmlForMarkdown(this.markdown()), { FORBID_TAGS: FORBIDDEN_TAGS }));
}
