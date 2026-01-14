import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConsistencyIssueCommentComponent } from 'app/shared/monaco-editor/consistency-issue-comment/consistency-issue-comment.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { InlineConsistencyIssue } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';

@Component({
    selector: 'jhi-monaco-diff-editor',
    template: '',
})
class MockMonacoDiffEditorComponent {
    @Input() allowSplitView?: boolean;
    setFileContents = jest.fn();
}

describe('ConsistencyIssueCommentComponent', () => {
    let fixture: ComponentFixture<ConsistencyIssueCommentComponent>;
    let comp: ConsistencyIssueCommentComponent;
    let onApply: jest.Mock;

    const baseIssue: InlineConsistencyIssue = {
        filePath: 'file.java',
        type: 'TEMPLATE_REPOSITORY',
        startLine: 1,
        endLine: 1,
        description: 'Issue description',
        suggestedFix: 'Suggested fix',
        category: 'METHOD_PARAMETER_MISMATCH',
        severity: 'MEDIUM',
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ConsistencyIssueCommentComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ConsistencyIssueCommentComponent, {
                remove: { imports: [MonacoDiffEditorComponent] },
                add: { imports: [MockMonacoDiffEditorComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ConsistencyIssueCommentComponent);
        comp = fixture.componentInstance;
        onApply = jest.fn(() => true);
        fixture.componentRef.setInput('issue', baseIssue);
        fixture.componentRef.setInput('onApply', onApply);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should build a line label for a single line issue', () => {
        fixture.componentRef.setInput('issue', { ...baseIssue, startLine: 3, endLine: 3 });
        fixture.detectChanges();

        expect(comp.lineLabel()).toBe('L3');
    });

    it('should build a line label for a range issue', () => {
        fixture.componentRef.setInput('issue', { ...baseIssue, startLine: 3, endLine: 5 });
        fixture.detectChanges();

        expect(comp.lineLabel()).toBe('L3-5');
    });

    it('should detect when diff content is available', () => {
        comp.showDetails.set(false);
        fixture.componentRef.setInput('issue', { ...baseIssue, originalText: 'old', modifiedText: 'new' });
        fixture.detectChanges();

        expect(comp.hasDiff()).toBeTrue();
    });

    it('should apply suggested change and store the status', () => {
        onApply = jest.fn(() => false);
        fixture.componentRef.setInput('onApply', onApply);
        fixture.detectChanges();

        comp.applySuggestedChange();

        expect(onApply).toHaveBeenCalledExactlyOnceWith(baseIssue);
        expect(comp.applyStatus()).toBeFalse();
    });

    it('should toggle details visibility', () => {
        fixture.detectChanges();

        expect(comp.showDetails()).toBeTrue();
        comp.toggleDetails();
        expect(comp.showDetails()).toBeFalse();
    });

    it('should update the diff editor when the issue changes', () => {
        const mockDiffEditor = new MockMonacoDiffEditorComponent();
        (comp as unknown as { diffEditor: () => MockMonacoDiffEditorComponent }).diffEditor = () => mockDiffEditor;
        const withDiff = { ...baseIssue, originalText: 'old', modifiedText: 'new' };
        fixture.componentRef.setInput('issue', withDiff);
        fixture.detectChanges();

        const updatedIssue = { ...withDiff, modifiedText: 'newer' };
        fixture.componentRef.setInput('issue', updatedIssue);
        fixture.detectChanges();

        expect(mockDiffEditor.setFileContents).toHaveBeenCalledWith('old', 'newer', updatedIssue.filePath, updatedIssue.filePath);
    });
});
