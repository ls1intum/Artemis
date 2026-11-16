import { vi } from 'vitest';
import { Component, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { KnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { KnowledgeAreaTreeComponent, KnowledgeAreaTreeDataSource } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

/**
 * Host component that provides the consumer content templates (knowledgeArea + competency) the same way the real
 * consumers do, so the projected tree node templates can be asserted.
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

    it('should render the tree', () => {
        hostFixture.detectChanges();
        const tree = hostFixture.debugElement.query(By.css('[data-testid="knowledge-area-tree"]'));
        expect(tree).not.toBeNull();
    });

    it('should render knowledge areas and project the knowledge area template', () => {
        host.dataSource = { data: [ka({ id: 1, title: 'Algorithms' }), ka({ id: 2, title: 'Databases' })] };
        hostFixture.detectChanges();

        const knowledgeAreaNodes = hostFixture.debugElement.queryAll(By.css('[data-testid="knowledge-area-node"]'));
        expect(knowledgeAreaNodes).toHaveLength(2);
        const projected = hostFixture.debugElement.queryAll(By.css('.ka-content')).map((el) => el.nativeElement.textContent.trim());
        expect(projected).toEqual(['Algorithms', 'Databases']);
    });

    it('should filter out hidden knowledge areas', () => {
        host.dataSource = { data: [ka({ id: 1 }), ka({ id: 2, isVisible: false })] };
        hostFixture.detectChanges();

        const knowledgeAreaNodes = hostFixture.debugElement.queryAll(By.css('[data-testid="knowledge-area-node"]'));
        expect(knowledgeAreaNodes).toHaveLength(1);
    });

    it('should expose expansion on the focusable row for keyboard and programmatic changes', () => {
        host.dataSource = { data: [ka({ id: 1 })] };
        hostFixture.detectChanges();
        const title = () => hostFixture.nativeElement.querySelector('[role="treeitem"]') as HTMLElement;
        expect(title().getAttribute('aria-expanded')).toBe('false');

        title().dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
        hostFixture.detectChanges();
        expect(title().getAttribute('aria-expanded')).toBe('true');

        title().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        hostFixture.detectChanges();
        expect(title().getAttribute('aria-expanded')).toBe('false');

        host.tree().expandAll();
        hostFixture.detectChanges();
        expect(title().getAttribute('aria-expanded')).toBe('true');

        host.tree().collapseAll();
        hostFixture.detectChanges();
        expect(title().getAttribute('aria-expanded')).toBe('false');
    });

    it('should support the arrow, home and end keys on the visible tree rows', () => {
        host.dataSource = { data: [ka({ id: 1, children: [ka({ id: 11, level: 1 })] }), ka({ id: 2 })] };
        hostFixture.detectChanges();
        const titles = () => Array.from((hostFixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[role="treeitem"]'));
        const press = (element: HTMLElement, key: string) => {
            const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
            element.dispatchEvent(event);
            hostFixture.detectChanges();
            return event;
        };
        expect(titles()).toHaveLength(2);

        // ArrowRight expands, ArrowLeft collapses, and ArrowLeft on a collapsed root does nothing
        expect(press(titles()[0], 'ArrowRight').defaultPrevented).toBe(true);
        expect(titles()[0].getAttribute('aria-expanded')).toBe('true');
        expect(titles()).toHaveLength(3);
        press(titles()[0], 'ArrowLeft');
        expect(titles()[0].getAttribute('aria-expanded')).toBe('false');
        press(titles()[0], 'ArrowLeft');
        expect(titles()[0].getAttribute('aria-expanded')).toBe('false');

        // Down, Up, End and Home move the focus between the visible rows
        press(titles()[0], 'ArrowDown');
        expect(document.activeElement).toBe(titles()[1]);
        press(titles()[1], 'ArrowUp');
        expect(document.activeElement).toBe(titles()[0]);
        press(titles()[0], 'End');
        expect(document.activeElement).toBe(titles()[1]);
        press(titles()[1], 'Home');
        expect(document.activeElement).toBe(titles()[0]);

        // Other keys keep their default behaviour
        expect(press(titles()[0], 'a').defaultPrevented).toBe(false);

        // Keys pressed in an element inside of the row belong to that element
        const inner = document.createElement('span');
        titles()[0].appendChild(inner);
        expect(press(inner, 'ArrowRight').defaultPrevented).toBe(false);
        expect(titles()[0].getAttribute('aria-expanded')).toBe('false');
    });

    it('should render an empty-state node for a knowledge area without children or competencies', () => {
        host.dataSource = { data: [ka({ id: 1 })] };
        hostFixture.detectChanges();
        host.tree().expandAll();
        hostFixture.detectChanges();

        const emptyNode = hostFixture.debugElement.query(By.css('[data-testid="empty-node"]'));
        expect(emptyNode).not.toBeNull();
    });

    it('navigates into child areas and competency leaves and back to their parents', () => {
        host.dataSource = {
            data: [
                ka({
                    id: 1,
                    children: [ka({ id: 11, competencies: [{ id: 110, title: 'Searching', isVisible: true }] })],
                    competencies: [{ id: 10, title: 'Sorting', isVisible: true }],
                }),
                ka({ id: 2 }),
            ],
        };
        hostFixture.detectChanges();
        const rows = () => Array.from((hostFixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[role="treeitem"]'));
        const press = (key: string) => {
            document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
            hostFixture.detectChanges();
        };
        rows()[0].focus();
        press('ArrowRight'); // Expand the root, keeping focus there.
        expect(rows()[0].getAttribute('aria-expanded')).toBe('true');
        expect(document.activeElement).toBe(rows()[0]);
        press('ArrowRight'); // Enter its first child area.
        expect(document.activeElement).toBe(rows()[1]);
        press('ArrowRight'); // Expand the child.
        press('ArrowRight'); // Enter its competency leaf.
        expect(document.activeElement).toBe(rows()[2]);
        expect(rows()[2].textContent).toContain('Searching');
        press('ArrowLeft'); // A leaf returns to its parent.
        expect(document.activeElement).toBe(rows()[1]);
        press('ArrowLeft'); // Collapse the child, keeping focus there.
        expect(rows()[1].getAttribute('aria-expanded')).toBe('false');
        expect(document.activeElement).toBe(rows()[1]);
        press('ArrowLeft'); // A collapsed child returns to its parent.
        expect(document.activeElement).toBe(rows()[0]);
        press('ArrowDown');
        expect(document.activeElement).toBe(rows()[1]);
        press('ArrowDown');
        expect(document.activeElement).toBe(rows()[2]);
        expect(rows()[2].textContent).toContain('Sorting');
        press('ArrowDown');
        expect(document.activeElement).toBe(rows()[3]);
        press('ArrowUp');
        expect(document.activeElement).toBe(rows()[2]);
    });

    it('keeps one tree row in the tab order as focus moves and the tree is filtered', () => {
        host.dataSource = { data: [ka({ id: 1 }), ka({ id: 2 })] };
        hostFixture.detectChanges();
        const rows = () => Array.from((hostFixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[role="treeitem"]'));
        expect(rows().map((row) => row.tabIndex)).toEqual([0, -1]);
        rows()[0].focus();
        rows()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
        hostFixture.detectChanges();
        expect(document.activeElement).toBe(rows()[1]);
        expect(rows().map((row) => row.tabIndex)).toEqual([-1, 0]);
        rows()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
        hostFixture.detectChanges();
        expect(document.activeElement).toBe(rows()[0]);
        host.dataSource.data[0].isVisible = false;
        host.tree().rebuild();
        hostFixture.detectChanges();
        expect(rows()).toHaveLength(1);
        expect(rows()[0].tabIndex).toBe(0);
    });

    it('does not enter a sibling when filtering leaves an expanded area without visible children', () => {
        host.dataSource = { data: [ka({ id: 1, children: [ka({ id: 11, isVisible: false })] }), ka({ id: 2 })] };
        hostFixture.detectChanges();
        host.tree().expandAll();
        hostFixture.detectChanges();
        const first = hostFixture.nativeElement.querySelector('[role="treeitem"]') as HTMLElement;
        first.focus();
        first.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
        hostFixture.detectChanges();
        expect(document.activeElement).toBe(first);
    });

    it('should not render an empty-state node for a knowledge area whose only competency is hidden', () => {
        host.dataSource = { data: [ka({ id: 1, competencies: [{ id: 10, title: 'Hidden', isVisible: false }] })] };
        hostFixture.detectChanges();
        host.tree().expandAll();
        hostFixture.detectChanges();

        const emptyNode = hostFixture.debugElement.query(By.css('[data-testid="empty-node"]'));
        expect(emptyNode).toBeNull();
        const competencyNodes = hostFixture.debugElement.queryAll(By.css('[data-testid="competency-node"]'));
        expect(competencyNodes).toHaveLength(0);
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
