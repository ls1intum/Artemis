import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ProofDerivationWorkspaceComponent } from 'app/proof/manage/update/proof-derivation-workspace/proof-derivation-workspace.component';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';
import { MathNode } from 'app/proof/shared/entities/math-node.model';

describe('ProofDerivationWorkspaceComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofDerivationWorkspaceComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProofDerivationWorkspaceComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ProofBlockRegistryService, { getBlockRegistry: () => of([]) as any, descriptorFor: () => undefined }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(ProofDerivationWorkspaceComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(ProofDerivationWorkspaceComponent);
        component = fixture.componentInstance;
        const x: MathNode = { type: 'var', value: 'x' };
        fixture.componentRef.setInput('sourceExpression', x);
        fixture.componentRef.setInput('targetExpression', x);
        component.ngOnInit();
    });

    it('initialises the current expression from the source input', () => {
        expect(component.currentExpression()?.type).toBe('var');
    });

    it('rootNodes mirrors the current expression', () => {
        expect(component.rootNodes().length).toBe(1);
    });

    it('filteredBlocks returns an empty list when no blocks are loaded', () => {
        expect(component.filteredBlocks()).toEqual([]);
    });
});
