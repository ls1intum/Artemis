import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathDerivationWorkspaceComponent } from 'app/math/manage/update/math-derivation-workspace/math-derivation-workspace.component';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathNode } from 'app/math/shared/entities/math-node.model';

describe('MathDerivationWorkspaceComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathDerivationWorkspaceComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathDerivationWorkspaceComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any, descriptorFor: () => undefined }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
            ],
        }).overrideComponent(MathDerivationWorkspaceComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathDerivationWorkspaceComponent);
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
