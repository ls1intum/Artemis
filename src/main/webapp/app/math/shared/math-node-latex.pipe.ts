import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import katex from 'katex';

import { MathBlockRegistryService } from '../manage/service/math-block-registry.service';
import { MathNode, mathNodeToLatex } from './entities/math-node.model';

/**
 * Renders a {@link MathNode} AST as KaTeX HTML.
 * Usage: {{ node | mathNodeLatex }}  or  [innerHTML]="node | mathNodeLatex"
 *
 * Uses the block registry (if loaded) for precedence-based auto-parenthesization.
 */
@Pipe({ name: 'mathNodeLatex', standalone: true })
export class MathNodeLatexPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);
    private registry = inject(MathBlockRegistryService);

    transform(node: MathNode | undefined): SafeHtml {
        if (!node) {
            return '';
        }
        const latex = mathNodeToLatex(node, (type) => this.registry.descriptorFor(type));
        try {
            const html = katex.renderToString(latex, { throwOnError: false, displayMode: false });
            return this.sanitizer.bypassSecurityTrustHtml(html);
        } catch {
            return latex;
        }
    }
}
