import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { MathNodeLatexPipe } from 'app/proof/shared/math-node-latex.pipe';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';
import { MathNode } from 'app/proof/shared/entities/math-node.model';

describe('MathNodeLatexPipe', () => {
    setupTestBed({ zoneless: true });

    let pipe: MathNodeLatexPipe;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MathNodeLatexPipe, MockProvider(ProofBlockRegistryService, { descriptorFor: () => undefined })],
        });
        pipe = TestBed.inject(MathNodeLatexPipe);
    });

    it('returns empty string for undefined input', () => {
        expect(pipe.transform(undefined)).toBe('');
    });

    it('renders a leaf variable node to SafeHtml', () => {
        const leaf: MathNode = { type: 'var', value: 'x' };
        const result = pipe.transform(leaf) as { changingThisBreaksApplicationSecurity?: string };
        expect(result).toBeTruthy();
        if (typeof result === 'object') {
            expect(result.changingThisBreaksApplicationSecurity).toMatch(/katex/);
        }
    });
});
