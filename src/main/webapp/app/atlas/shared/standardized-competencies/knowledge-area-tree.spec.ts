import { vi } from 'vitest';
import { Component, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { KnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { KnowledgeAreaTreeComponent, KnowledgeAreaTreeDataSource } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

/**
 * Host component that provides the consumer content templates (knowledgeArea + competency) the same way the real
 * consumers do, so the projected p-tree node templates can be asserted.
 */
@Component({
    template: `
        <jhi-knowledge-area-tree [dataSource]="dataSource">
            <ng-template let-knowledgeArea="knowledgeArea" #knowledgeAreaTemplate>
                <span class="ka-content" [attr.data-ka-id]="knowledgeArea.id">{{ knowledgeArea.title }}</span>
            </ng-template>
            <ng-template let-competency="competency" let-knowledgeArea="knowledgeArea" #competencyTemplate>
                <span class="competency-content" [attr.data-competency-id]="competency.id" [attr.data-ka-id]="knowledgeArea.id">{{ competency.title }}</span>
            </ng-template>
        </jhi-knowledge-area-tree>
    `,
    imports: [KnowledgeAreaTreeComponent],
})
class HostComponent {
    dataSource: KnowledgeAreaTreeDataSource = { data: [] };
    readonly tree = viewChild.required(KnowledgeAreaTreeComponent);
}

function ka(partial: Partial<KnowledgeAreaForTree> & { id: number }): KnowledgeAreaForTree {
    return { isVisible: true, level: 0, title: `ka-${partial.id}`, ...partial };
}

describe('KnowledgeAreaTreeComponent', () => {
    setupTestBed({ zoneless: true });
    let hostFixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        hostFixture = TestBed.createComponent(HostComponent);
        host = hostFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render the p-tree', () => {
        hostFixture.detectChanges();
        const tree = hostFixture.debugElement.query(By.css('[data-testid="knowledge-area-tree"]'));
        expect(tree).not.toBeNull();
    });

    it('should render knowledge areas and project the knowledge area template', () => {
        host.dataSource = { data: [ka({ id: 1, title: 'Algorithms' }), ka({ id: 2, title: 'Databases' })] };
        host.tree().rebuild();
        hostFixture.detectChanges();

        const knowledgeAreaNodes = hostFixture.debugElement.queryAll(By.css('[data-testid="knowledge-area-node"]'));
        expect(knowledgeAreaNodes).toHaveLength(2);
        const projected = hostFixture.debugElement.queryAll(By.css('.ka-content')).map((el) => el.nativeElement.textContent.trim());
        expect(projected).toEqual(['Algorithms', 'Databases']);
    });

    it('should filter out hidden knowledge areas', () => {
        host.dataSource = { data: [ka({ id: 1 }), ka({ id: 2, isVisible: false })] };
        host.tree().rebuild();
        hostFixture.detectChanges();

        const knowledgeAreaNodes = hostFixture.debugElement.queryAll(By.css('[data-testid="knowledge-area-node"]'));
        expect(knowledgeAreaNodes).toHaveLength(1);
    });

    it('should render an empty-state node for a knowledge area without children or competencies', () => {
        host.dataSource = { data: [ka({ id: 1 })] };
        hostFixture.detectChanges();
        host.tree().expandAll();
        hostFixture.detectChanges();

        const emptyNode = hostFixture.debugElement.query(By.css('[data-testid="empty-node"]'));
        expect(emptyNode).not.toBeNull();
    });

    it('should render competencies and project the competency template when expanded', () => {
        host.dataSource = {
            data: [
                ka({
                    id: 1,
                    competencies: [
                        { id: 10, title: 'Sorting', isVisible: true },
                        { id: 11, title: 'Hidden', isVisible: false },
                    ],
                }),
            ],
        };
        hostFixture.detectChanges();
        host.tree().expandAll();
        hostFixture.detectChanges();

        const competencyNodes = hostFixture.debugElement.queryAll(By.css('[data-testid="competency-node"]'));
        expect(competencyNodes).toHaveLength(1);
        const projected = hostFixture.debugElement.query(By.css('.competency-content'));
        expect(projected.nativeElement.textContent.trim()).toBe('Sorting');
        expect(projected.nativeElement.getAttribute('data-competency-id')).toBe('10');
        expect(projected.nativeElement.getAttribute('data-ka-id')).toBe('1');
    });

    describe('programmatic expansion control', () => {
        beforeEach(() => {
            host.dataSource = { data: [ka({ id: 1, children: [ka({ id: 11, level: 1 })] }), ka({ id: 2 })] };
            host.tree().rebuild();
            hostFixture.detectChanges();
        });

        it('should expand a single knowledge area', () => {
            const tree = host.tree();
            expect(tree.isExpanded(ka({ id: 1 }))).toBe(false);

            tree.expand(ka({ id: 1 }));

            expect(tree.isExpanded(ka({ id: 1 }))).toBe(true);
            expect(tree.isExpanded(ka({ id: 2 }))).toBe(false);
        });

        it('should expand and collapse all knowledge areas', () => {
            const tree = host.tree();

            tree.expandAll();
            expect(tree.isExpanded(ka({ id: 1 }))).toBe(true);
            expect(tree.isExpanded(ka({ id: 11 }))).toBe(true);
            expect(tree.isExpanded(ka({ id: 2 }))).toBe(true);

            tree.collapseAll();
            expect(tree.isExpanded(ka({ id: 1 }))).toBe(false);
            expect(tree.isExpanded(ka({ id: 11 }))).toBe(false);
            expect(tree.isExpanded(ka({ id: 2 }))).toBe(false);
        });
    });
});
