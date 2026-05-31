import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ProofBuilderComponent } from 'app/proof/manage/update/proof-builder/proof-builder.component';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';
import { BlockDefinitionModel } from 'app/proof/shared/entities/block-definition.model';

describe('ProofBuilderComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofBuilderComponent;

    const sampleBlocks: BlockDefinitionModel[] = [
        { type: 'number', displaySymbol: 'n', layoutCategory: 'LEAF', slots: [] } as unknown as BlockDefinitionModel,
        { type: 'add', displaySymbol: '+', layoutCategory: 'BINARY_INFIX', slots: ['lhs', 'rhs'] } as unknown as BlockDefinitionModel,
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProofBuilderComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ProofBlockRegistryService, { getBlockRegistry: () => of(sampleBlocks) as any, descriptorFor: (t: string) => sampleBlocks.find((b) => b.type === t) }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(ProofBuilderComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(ProofBuilderComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('loads the block registry on init', () => {
        expect(component.blocks().length).toBe(2);
    });

    it('isTerminal returns true for leaf blocks', () => {
        component.selectedBlockType.set('number');
        expect(component.isTerminal).toBe(true);
    });

    it('isTerminal returns false for binary infix blocks', () => {
        component.selectedBlockType.set('add');
        expect(component.isTerminal).toBe(false);
    });

    it('selectNode updates the selected root index and path', () => {
        component.selectNode(0, [1, 2]);
        expect(component.selectedRootIndex()).toBe(0);
        expect(component.selectedPath()).toEqual([1, 2]);
    });
});
