import {
    ApplicationRef,
    Component,
    ComponentRef,
    DestroyRef,
    ElementRef,
    EnvironmentInjector,
    afterRenderEffect,
    createComponent,
    effect,
    inject,
    input,
    output,
    signal,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { PostingContentPart } from 'app/communication/communication.util';
import { PostingContentPartComponent } from 'app/communication/posting-content/posting-content-part/posting-content-part.components';
import { renderMarkdownToHtml } from 'app/foundation/util/markdown-render.util';
import { setInputs } from 'app/foundation/util/set-inputs.util';

const REFERENCE_ATTRIBUTE = 'data-posting-reference';

/** Marks the spot of the reference with this index in the Markdown that {@link PostingMarkdownComponent} renders. */
export function postingReferencePlaceholder(index: number): string {
    // The index is a number the posting computed itself, so no user text enters the markup.
    return ['<span ', REFERENCE_ATTRIBUTE, '="', String(index), '"></span>'].join('');
}

/**
 * Renders the Markdown of a posting as one document and mounts each of its references where the Markdown placed it.
 * Lists, quotes and line breaks that surround a reference therefore render as they do in the message editor;
 * rendering the text before and after each reference separately would break them apart.
 */
@Component({
    selector: 'jhi-posting-markdown',
    // A span like the one the posting always rendered in: global.scss keeps its last paragraph inline, so a label such as "(edited)" follows the text.
    template: '<span class="markdown-preview posting-markdown" [innerHTML]="rendered().html"></span>',
})
export class PostingMarkdownComponent {
    private readonly sanitizer = inject(DomSanitizer);
    private readonly applicationRef = inject(ApplicationRef);
    private readonly environmentInjector = inject(EnvironmentInjector);
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    /** Markdown in which every reference is replaced by its {@link postingReferencePlaceholder}. */
    readonly markdown = input.required<string>();
    readonly references = input<PostingContentPart[]>([]);
    readonly userReferenceClicked = output<string>();
    readonly channelReferenceClicked = output<number>();

    // The references travel with the HTML they belong to, so a placeholder never meets the reference of a newer posting content.
    protected readonly rendered = signal<{ html: SafeHtml; references: PostingContentPart[] }>({ html: '', references: [] });

    private readonly allowedHtmlTags = [
        'a',
        'b',
        'br',
        'blockquote',
        'code',
        'del',
        'em',
        'hr',
        'h1',
        'h2',
        'h3',
        'h4',
        'h5',
        'h6',
        'i',
        'ins',
        'li',
        'mark',
        'p',
        'pre',
        'small',
        's',
        'span',
        'strong',
        'sub',
        'sup',
        'ul',
        'ol',
    ];
    private readonly allowedHtmlAttributes = ['href', REFERENCE_ATTRIBUTE];

    private renderGeneration = 0;
    private mountedReferences: ComponentRef<PostingContentPartComponent>[] = [];

    constructor() {
        effect(() => {
            const markdown = this.markdown();
            const references = this.references();
            // An earlier render that is still pending must not overwrite this one.
            const generation = ++this.renderGeneration;
            renderMarkdownToHtml(markdown, [], this.allowedHtmlTags, this.allowedHtmlAttributes, true)
                .then((html) => {
                    if (generation === this.renderGeneration) {
                        this.rendered.set({ html: this.sanitizer.bypassSecurityTrustHtml(html), references });
                    }
                })
                .catch(() => {
                    // The lazy markdown chunk failed to load: show nothing rather than leak an unhandled rejection.
                    if (generation === this.renderGeneration) {
                        this.rendered.set({ html: '', references: [] });
                    }
                });
        });
        // Runs after Angular has written the HTML, which replaced the elements of the previously mounted references.
        afterRenderEffect(() => this.mountReferences(this.rendered().references));
        inject(DestroyRef).onDestroy(() => this.unmountReferences());
    }

    private mountReferences(references: PostingContentPart[]): void {
        this.unmountReferences();
        this.host.nativeElement.querySelectorAll<HTMLElement>(`[${REFERENCE_ATTRIBUTE}]`).forEach((placeholder) => {
            const reference = references[Number(placeholder.getAttribute(REFERENCE_ATTRIBUTE))];
            if (!reference) {
                return;
            }
            // A reference inside a link label would nest its anchor in the link and lead off site when clicked, so the link shows its text.
            if (placeholder.closest('a')) {
                placeholder.textContent = reference.referenceStr ?? '';
                return;
            }
            const component = createComponent(PostingContentPartComponent, { environmentInjector: this.environmentInjector, hostElement: placeholder });
            setInputs(component, { postingContentPart: reference });
            component.instance.userReferenceClicked.subscribe((login) => this.userReferenceClicked.emit(login));
            component.instance.channelReferenceClicked.subscribe((channelId) => this.channelReferenceClicked.emit(channelId));
            this.applicationRef.attachView(component.hostView);
            this.mountedReferences.push(component);
        });
    }

    private unmountReferences(): void {
        this.mountedReferences.forEach((component) => component.destroy());
        this.mountedReferences = [];
    }
}
