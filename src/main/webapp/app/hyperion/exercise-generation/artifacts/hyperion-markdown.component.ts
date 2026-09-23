import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, input } from '@angular/core';
import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';

/** Generated documents cannot load remote media or contain interactive controls. */
const FORBIDDEN_TAGS = ['style', 'form', 'input', 'button', 'select', 'option', 'optgroup', 'textarea', 'label', 'fieldset', 'legend', 'img', 'video', 'audio', 'source', 'track'];

/** Uses Artemis markdown rendering and feature-scoped typography, with stricter sanitisation for generated content. */
@Component({
    selector: 'jhi-hyperion-markdown',
    template: `<div class="hyperion-markdown-body" data-slot="prose" [attr.data-density]="density()" [innerHTML]="rendered() | safeHtml: forbiddenTags"></div>`,
    styleUrl: './hyperion-markdown.component.scss',
    encapsulation: ViewEncapsulation.None,
    styles: `
        /* Keep styled descendants inside the document; scrolling preserves access to wide tables and formulas. */
        .hyperion-markdown-body {
            contain: paint;
            overflow: auto;
        }
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[attr.data-slot]': '"markdown"' },
    imports: [SafeHtmlPipe],
})
export class HyperionMarkdownComponent {
    readonly markdown = input<string | undefined>();
    /** `compact` tightens the block spacing for prose inside a docked panel. */
    readonly density = input<'comfortable' | 'compact'>('comfortable');
    protected readonly forbiddenTags = FORBIDDEN_TAGS;

    protected readonly rendered = computed(() => htmlForMarkdown(this.markdown(), [], undefined, undefined, false, false));
}
