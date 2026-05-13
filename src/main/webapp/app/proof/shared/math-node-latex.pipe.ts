import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import katex from 'katex';

import { MathNode, mathNodeToLatex } from './entities/math-node.model';

/**
 * Renders a {@link MathNode} AST as KaTeX HTML.
 * Usage: {{ node | mathNodeLatex }}  or  [innerHTML]="node | mathNodeLatex"
 */
@Pipe({ name: 'mathNodeLatex', standalone: true })
export class MathNodeLatexPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    transform(node: MathNode | undefined): SafeHtml {
        if (!node) {
            return '';
        }
        const latex = mathNodeToLatex(node);
        try {
            const html = katex.renderToString(latex, { throwOnError: false, displayMode: false });
            return this.sanitizer.bypassSecurityTrustHtml(html);
        } catch {
            return latex;
        }
    }
}
