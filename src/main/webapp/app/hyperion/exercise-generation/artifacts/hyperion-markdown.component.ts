import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import DOMPurify from 'dompurify';
import { TumUiProseComponent, TumUiProseDensity } from '@tumaet/ui-angular';

import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';

/**
 * Interactive controls a generated document has no legitimate reason to contain.
 *
 * `htmlForMarkdown()` runs markdown-it with `html: true` and then DOMPurify with its default profile, and that
 * profile permits `<form>` and its controls - verified, not assumed. For a human-authored Artemis post that is a
 * defensible default. For a document a language model wrote, a form that posts somewhere is a credential prompt
 * rendered inside an authenticated page, so it is removed here rather than app-wide: this narrows the rule to the
 * content that needs it instead of changing a sanitiser every other surface in Artemis shares.
 *
 * DOMPurify keeps a forbidden element's children, so the words inside one of these still reach the reader.
 */
const FORBIDDEN_TAGS = ['form', 'input', 'button', 'select', 'option', 'optgroup', 'textarea', 'label', 'fieldset', 'legend'];

/**
 * Markdown a generation run produced, rendered as prose.
 *
 * The split is deliberate and is the whole reason this component exists: **the application owns the markdown
 * pipeline, the design system owns the typography.** `htmlForMarkdown()` drags in markdown-it, highlight.js, KaTeX
 * and DOMPurify, none of which may become dependencies of `packages/tum-ui`; `tum-ui-prose` owns the type scale and
 * gets it to win over Bootstrap's unlayered heading rules on specificity, without a single `!important`.
 *
 * The content is **untrusted**: it is whatever a large language model decided to write, and it reaches the DOM
 * through `[innerHTML]`. It therefore passes three DOMPurify passes, and each one is there for its own reason:
 * `htmlForMarkdown()` sanitises the rendered HTML with the shared Artemis profile, {@link FORBIDDEN_TAGS} narrows
 * that profile to what a generated document may contain, and `SafeHtmlPipe` sanitises once more before marking the
 * result trusted for Angular - which is what makes this component safe on its own terms, independently of what any
 * future caller passes in.
 *
 * The remaining vector the sanitiser cannot close is layout: inline `style` survives (KaTeX needs it), so a
 * `position: fixed` overlay would otherwise escape this component and cover the page. The rendered document is
 * therefore given its own containing block, which turns any fixed descendant into an absolute one bounded by this
 * component. That is a two-line style rule doing work no allow-list can do.
 *
 * **Why the attribute form of `tum-ui-prose` and not the element.** The prose stylesheet sets the document's whole
 * vertical rhythm with direct-child selectors - `.tum-ui-prose > * + *` and `.tum-ui-prose > * + h2`. Projecting the
 * rendered document into a wrapper `<div>` inside `<tum-ui-prose>` puts one element between them, and every one of
 * those rules stops matching: the headings, paragraphs and lists lose their spacing entirely. `[tumUiProse]` puts
 * the class on the element that receives the `[innerHTML]`, so the rendered blocks are its direct children and the
 * rhythm applies. (The package's JSDoc currently shows the wrapper form; it has the same defect.)
 *
 * States: empty markdown renders nothing at all, and the caller shows its own empty state. There is deliberately no
 * "no content" copy here, because only the caller knows *why* there is none.
 */
@Component({
    selector: 'jhi-hyperion-markdown',
    template: `<div tumUiProse class="hyperion-markdown-body" [density]="density()" [innerHTML]="rendered() | safeHtml"></div>`,
    styles: `
        /*
         * A containing block for the untrusted document, not a typographic decision - typography is tum-ui-prose's.
         * Any element with a transform establishes a containing block for its fixed-position descendants, so a
         * 'position: fixed' overlay written into the markdown is bounded by this element instead of by the viewport.
         * A transform is used rather than 'contain: paint' because it does not clip: a wide table or an overflowing
         * KaTeX box still renders in full.
         */
        .hyperion-markdown-body {
            transform: translateZ(0);
        }
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[attr.data-slot]': '"markdown"' },
    imports: [SafeHtmlPipe, TumUiProseComponent],
})
export class HyperionMarkdownComponent {
    readonly markdown = input<string | undefined>();
    /** `compact` tightens the block spacing for prose inside a docked panel. */
    readonly density = input<TumUiProseDensity>('comfortable');

    /** Rendered once per distinct source string: markdown-it plus highlight.js is far too costly to repeat per pass. */
    protected readonly rendered = computed(() => DOMPurify.sanitize(htmlForMarkdown(this.markdown()), { FORBID_TAGS: FORBIDDEN_TAGS }));
}
