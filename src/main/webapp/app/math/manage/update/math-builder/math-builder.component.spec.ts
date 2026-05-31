import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathBuilderComponent } from 'app/math/manage/update/math-builder/math-builder.component';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';

describe('MathBuilderComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathBuilderComponent;

    const sampleBlocks: BlockDefinitionModel[] = [
        { type: 'number', displaySymbol: 'n', layoutCategory: 'LEAF', slots: [] } as unknown as BlockDefinitionModel,
        { type: 'add', displaySymbol: '+', layoutCategory: 'BINARY_INFIX', slots: ['lhs', 'rhs'] } as unknown as BlockDefinitionModel,
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathBuilderComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of(sampleBlocks) as any, descriptorFor: (t: string) => sampleBlocks.find((b) => b.type === t) }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(MathBuilderComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathBuilderComponent);
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
