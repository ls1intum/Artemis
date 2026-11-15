import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CodeEditorFileBrowserFolderComponent } from './code-editor-file-browser-folder.component';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { FileBadge, FileBadgeType, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CodeEditorFileBrowserFolderComponent', () => {
    let fixture: ComponentFixture<CodeEditorFileBrowserFolderComponent>;
    let component: CodeEditorFileBrowserFolderComponent;
    let toggle: ReturnType<typeof vi.fn>;
    let item: TreeViewItem<string>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserFolderComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(CodeEditorFileBrowserFolderComponent);
        component = fixture.componentInstance;
        toggle = vi.fn();
        item = new TreeViewItem({ text: 'src', value: 'src', children: [], collapsed: true });
        fixture.componentRef.setInput('item', item);
        fixture.componentRef.setInput('onCollapseExpand', toggle);
        fixture.componentRef.setInput('disableActions', false);
        fixture.componentRef.setInput('badges', [new FileBadge(FileBadgeType.REVIEW_COMMENT, 2)]);
        fixture.detectChanges();
    });

    it('should keep folder activation separate from the four action buttons', () => {
        const row = fixture.nativeElement.querySelector('#file-browser-folder') as HTMLElement;
        const selection = row.querySelector('button[aria-label="src"]') as HTMLButtonElement;
        const create = vi.spyOn(component.onSetCreatingNodeInFolder, 'emit');
        const rename = vi.spyOn(component.onSetRenamingNode, 'emit');
        const remove = vi.spyOn(component.onDeleteNode, 'emit');
        expect(row.hasAttribute('role')).toBe(false);
        expect(row.tabIndex).toBe(-1);
        expect(selection).not.toBeNull();
        expect(selection.type).toBe('button');
        expect(selection.getAttribute('aria-expanded')).toBe('false');
        expect(selection.querySelector('button, input, [role="button"]')).toBeNull();
        const actions = Array.from(row.querySelectorAll('button')).filter((button) => button !== selection);
        expect(actions).toHaveLength(4);
        actions.forEach((button) => button.click());
        expect(create).toHaveBeenNthCalledWith(1, { item, fileType: FileType.FILE });
        expect(create).toHaveBeenNthCalledWith(2, { item, fileType: FileType.FOLDER });
        expect(rename).toHaveBeenCalledExactlyOnceWith(item);
        expect(remove).toHaveBeenCalledExactlyOnceWith(item);
        expect(toggle).not.toHaveBeenCalled();
        selection.click();
        expect(toggle).toHaveBeenCalledOnce();
    });

    it('should keep renaming outside the toggle surface', () => {
        fixture.componentRef.setInput('isBeingRenamed', true);
        fixture.detectChanges();
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
        const rename = vi.spyOn(component.onRenameNode, 'emit');
        expect(input.closest('button, [role="button"]')).toBeNull();
        input.click();
        input.value = 'source';
        input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true }));
        expect(rename).toHaveBeenCalledExactlyOnceWith('source');
        expect(toggle).not.toHaveBeenCalled();
    });
});
