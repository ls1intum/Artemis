import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { FileBadge, FileBadgeType, PROBLEM_STATEMENT_IDENTIFIER } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CodeEditorFileBrowserProblemStatementComponent } from 'app/programming/manage/code-editor/file-browser/problem-statement/code-editor-file-browser-problem-statement.component';

describe('CodeEditorFileBrowserProblemStatementComponent', () => {
    let fixture: ComponentFixture<CodeEditorFileBrowserProblemStatementComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserProblemStatementComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeEditorFileBrowserProblemStatementComponent);
        fixture.componentRef.setInput(
            'item',
            new TreeViewItem<string>({
                text: PROBLEM_STATEMENT_IDENTIFIER,
                value: PROBLEM_STATEMENT_IDENTIFIER,
                checked: false,
                children: [],
            }),
        );
    });

    it('should render badges when provided', () => {
        fixture.componentRef.setInput('badges', [new FileBadge(FileBadgeType.REVIEW_COMMENT, 2), new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 1)]);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('jhi-file-browser-badge'))).toHaveLength(2);
    });
});
