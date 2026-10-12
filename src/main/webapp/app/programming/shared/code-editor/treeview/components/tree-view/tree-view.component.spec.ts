import { Component, signal } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { By } from '@angular/platform-browser';

import { TreeViewComponent } from 'app/programming/shared/code-editor/treeview/components/tree-view/tree-view.component';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { createGenericTestComponent } from 'test/helpers/tree-view/common';

const items = [new TreeViewItem<number>({ text: 'Root item', value: 1, children: [] })];

@Component({
    selector: 'jhi-test-host',
    template: '',
    imports: [TreeViewComponent],
})
class TestHostComponent {
    readonly items = signal(items);
}

describe('TreeViewComponent', () => {
    it('should render the built-in default item template when itemTemplate is omitted', () => {
        const fixture = createGenericTestComponent('<treeview [items]="items()" />', TestHostComponent) as ComponentFixture<TestHostComponent>;
        const label = fixture.debugElement.query(By.css('.form-check-label'));
        expect(label.nativeElement.textContent.trim()).toBe('Root item');
    });
    it('should label each checkbox directly and expose a named expand button', () => {
        const fixture = createGenericTestComponent('<treeview [items]="items()" />', TestHostComponent) as ComponentFixture<TestHostComponent>;
        const root = new TreeViewItem<number>({ text: 'Folder', value: 1, children: [{ text: 'File', value: 2, children: [] }] });
        fixture.componentInstance.items.set([root]);
        fixture.detectChanges();

        const labels: NodeListOf<HTMLLabelElement> = fixture.nativeElement.querySelectorAll('label');
        expect(labels).toHaveLength(2);
        for (const label of labels) {
            const checkbox = label.querySelector('input');
            expect(checkbox?.type).toBe('checkbox');
            expect(label.control).toBe(checkbox);
        }
        const expandButton: HTMLButtonElement = fixture.nativeElement.querySelector('button');
        expect(expandButton.type).toBe('button');
        expect(expandButton.getAttribute('aria-label')).toBe('Folder');
        expect(expandButton.getAttribute('aria-expanded')).toBe('true');
        expandButton.click();
        fixture.detectChanges();
        expect(root.collapsed).toBe(true);
        expect(expandButton.getAttribute('aria-expanded')).toBe('false');
    });
});
