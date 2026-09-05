import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { ModelingMarkdownExplanationEditorComponent } from 'app/modeling/shared/modeling-markdown-explanation-editor/modeling-markdown-explanation-editor.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { beforeEach, describe, expect, it } from 'vitest';

@Component({ selector: 'jhi-markdown-editor-monaco', template: '' })
class StubMarkdownEditorMonacoComponent {
    markdown = input<string>();
    markdownChange = output<string>();
    domainActions = input<unknown[]>([]);
    initialEditorHeight = input<number>();
    resizableMinHeight = input<number>();
    externalHeight = input(false);
    enableResize = input(true);
    showMarkdownInfoText = input(true);
}

describe('ModelingMarkdownExplanationEditorComponent', () => {
    let fixture: ComponentFixture<ModelingMarkdownExplanationEditorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ModelingMarkdownExplanationEditorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ModelingMarkdownExplanationEditorComponent, {
                remove: { imports: [MarkdownEditorMonacoComponent] },
                add: { imports: [StubMarkdownEditorMonacoComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ModelingMarkdownExplanationEditorComponent);
    });

    it('uses the same persistent, resizable explanation surface as the plain-text editor', () => {
        fixture.detectChanges();

        const surface = fixture.debugElement.query(By.css('.modeling-explanation-surface__surface'));
        const editor = fixture.debugElement.query(By.css('.modeling-markdown-explanation-editor__editor'));
        const markdownEditor = fixture.debugElement.query(By.directive(StubMarkdownEditorMonacoComponent));

        expect(surface).not.toBeNull();
        expect(editor).not.toBeNull();
        expect(markdownEditor.componentInstance.externalHeight()).toBe(true);
        expect(markdownEditor.componentInstance.enableResize()).toBe(false);
        surface.triggerEventHandler('resizeMove', { width: 500, height: 180 });
        fixture.detectChanges();
        expect(editor.nativeElement.style.getPropertyValue('height')).toBe('100%');
        expect(surface.nativeElement.classList).toContain('modeling-explanation-surface__surface--manually-sized');
    });

    it('binds Markdown changes through the wrapper', () => {
        fixture.componentRef.setInput('markdown', 'Initial rationale');
        fixture.detectChanges();

        const markdownEditor = fixture.debugElement.query(By.directive(StubMarkdownEditorMonacoComponent));
        expect(markdownEditor.componentInstance.markdown()).toBe('Initial rationale');

        markdownEditor.componentInstance.markdownChange.emit('Updated rationale');
        fixture.detectChanges();
        expect(fixture.componentInstance.markdown()).toBe('Updated rationale');
    });
});
