import { AfterViewInit, Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import mermaid from 'mermaid';

@Directive({
    selector: '[jhiMermaid]',
    standalone: true,
})
export class MermaidRenderDirective implements AfterViewInit, OnInit, OnDestroy {
    private el = inject(ElementRef<HTMLElement>);
    private observer: MutationObserver | null = null;
    private intersectionObserver: IntersectionObserver | null = null;
    private alertService: AlertService = inject(AlertService);
    private translateService: TranslateService = inject(TranslateService);

    constructor() {
        // Initialize mermaid globally for this directive.
        // 'startOnLoad: false' is crucial because we handle the rendering manually
        // based on the Angular lifecycle and DOM mutations, not on page load.
        mermaid.initialize({
            startOnLoad: false,
            theme: 'default',
            securityLevel: 'strict', // Prevents XSS within the diagram definitions
        });
    }

    ngOnInit(): void {
        this.setupObserver();
    }

    ngAfterViewInit(): void {
        setTimeout(() => {
            this.renderMermaidGraphs();
        }, 0);
    }

    ngOnDestroy(): void {
        if (this.observer) {
            this.observer.disconnect();
        }
        if (this.intersectionObserver) {
            this.intersectionObserver.disconnect();
        }
    }

    /**
     * Configures and starts the MutationObserver.
     */
    private setupObserver(): void {
        this.observer = new MutationObserver((mutations: MutationRecord[]) => {
            const hasAddedNodes = mutations.some((mutation) => mutation.addedNodes.length > 0);

            if (hasAddedNodes) {
                this.renderMermaidGraphs();
            }
        });

        this.observer.observe(this.el.nativeElement, {
            childList: true,
            subtree: true,
        });

        this.intersectionObserver = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    this.renderMermaidGraphs();
                }
            });
        });

        this.intersectionObserver.observe(this.el.nativeElement);
    }

    /**
     * Finds unprocessed Mermaid code blocks and compiles them into SVGs.
     */
    private async renderMermaidGraphs(): Promise<void> {
        const container = this.el.nativeElement;

        if (container.offsetWidth === 0) {
            return;
        }

        const rawMermaidBlocks = Array.from(container.querySelectorAll('pre code.language-mermaid')) as HTMLElement[];

        rawMermaidBlocks.forEach((block) => {
            const preElement = block.parentElement;

            if (preElement) {
                const mermaidDiv = document.createElement('div');
                mermaidDiv.className = 'mermaid';
                mermaidDiv.textContent = block.textContent;
                preElement.replaceWith(mermaidDiv);
            }
        });

        const targetNodes = Array.from(container.querySelectorAll('.mermaid:not([data-processed="true"])')) as HTMLElement[];

        if (targetNodes.length > 0) {
            try {
                await mermaid.run({
                    nodes: targetNodes,
                });
            } catch (error) {
                // non blocking: inform the user that something was not rendered
                this.alertService.warning(this.translateService.instant(`artemisApp.markdownEditor.mermaid.errorWhileRendering`));
            }
        }
    }
}
