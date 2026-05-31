import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { MathNodeComponent, MathNodeContext } from 'app/proof/manage/update/proof-math-node/proof-math-node.component';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';

describe('MathNodeComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathNodeComponent;

    const ctx: MathNodeContext = {
        draggingBlockType: undefined,
        editable: true,
        selectFn: () => {},
        dropFn: () => {},
        valueChangeFn: () => {},
        removeFn: () => {},
        isSelectedFn: () => false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathNodeComponent],
            providers: [
                MockProvider(ProofBlockRegistryService, {
                    descriptorFor: () => ({ type: 'var', displaySymbol: 'x', layoutCategory: 'LEAF' }) as any,
                }),
            ],
        }).overrideComponent(MathNodeComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathNodeComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('node', { type: 'var', value: 'x' });
        fixture.componentRef.setInput('ctx', ctx);
    });

    it('resolves descriptor metadata from the registry', () => {
        expect(component.descriptor()?.type).toBe('var');
        expect(component.layoutCategory()).toBe('LEAF');
        expect(component.displaySymbol()).toBe('x');
    });

    it('computes isSelected by delegating to ctx.isSelectedFn', () => {
        expect(component.isSelected()).toBe(false);
    });
});
