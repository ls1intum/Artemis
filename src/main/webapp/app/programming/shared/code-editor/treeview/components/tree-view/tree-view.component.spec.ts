import { Component } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
    items = items;
}

describe('TreeViewComponent', () => {
    setupTestBed({ zoneless: true });

    it('should render the built-in default item template when itemTemplate is omitted', () => {
        const fixture = createGenericTestComponent('<treeview [items]="items" />', TestHostComponent) as ComponentFixture<TestHostComponent>;
        const label = fixture.debugElement.query(By.css('.form-check-label'));
        expect(label.nativeElement.textContent.trim()).toBe('Root item');
    });
});
