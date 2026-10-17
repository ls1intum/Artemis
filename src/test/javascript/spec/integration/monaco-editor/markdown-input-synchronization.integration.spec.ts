import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { CommunicationService } from 'app/communication/service/communication.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

// Run in vitest.monaco.config.ts: this must exercise Monaco's real model, selection and undo stack.
describe('Markdown input synchronization with real Monaco', () => {
    let fixture: ComponentFixture<MarkdownEditorMonacoComponent>;
    let component: MarkdownEditorMonacoComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MarkdownEditorMonacoComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(FileUploaderService),
                MockProvider(AlertService),
                MockProvider(CourseConversationsService),
                MockProvider(CommunicationService),
                MockProvider(ProfileService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
            ],
        })
            .overrideComponent(MarkdownEditorMonacoComponent, {
                remove: { imports: [PostingButtonComponent, RedirectToIrisButtonComponent] },
                add: { imports: [MockComponent(PostingButtonComponent), MockComponent(RedirectToIrisButtonComponent)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(MarkdownEditorMonacoComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('externalHeight', true);
        fixture.componentRef.setInput('markdown', 'first line\nsecond line');
        fixture.detectChanges();
        // jsdom has no viewport. Selection content is real; the floating refinement button has no screen coordinates here.
        vi.spyOn(component.monacoEditor()!, 'getScrolledVisiblePosition').mockReturnValue(null);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('preserves subsequent typing, selection and undo when the parent acknowledges an older edit', () => {
        const monaco = component.monacoEditor()!;
        const editor = monaco.getEditor();
        const model = monaco.getModel()!;
        const changes: boolean[] = [];
        const subscription = model.onDidChangeContent((event) => changes.push(event.isFlush));
        try {
            editor.setPosition({ lineNumber: 2, column: 7 });
            monaco.triggerKeySequence(' edited');
            monaco.flushPendingTextChange();
            const acknowledgedText = monaco.getText();
            editor.pushUndoStop();
            monaco.triggerKeySequence(' continued');
            editor.setSelection({ startLineNumber: 2, startColumn: 3, endLineNumber: 2, endColumn: 8 });
            const selection = editor.getSelection();
            const latestText = monaco.getText();
            const version = model.getVersionId();

            fixture.componentRef.setInput('markdown', acknowledgedText);
            fixture.detectChanges();

            expect(monaco.getModel()).toBe(model);
            expect(monaco.getText()).toBe(latestText);
            expect(editor.getSelection()).toEqual(selection);
            expect(model.getVersionId()).toBe(version);
            expect(changes).not.toContain(true);
            editor.trigger('test', 'undo', undefined);
            expect(monaco.getText()).toBe(acknowledgedText);
        } finally {
            subscription.dispose();
        }
    });

    it('still replaces the content for another channel draft and an empty draft', () => {
        const monaco = component.monacoEditor()!;
        monaco.triggerKeySequence(' draft');
        monaco.flushPendingTextChange();
        fixture.componentRef.setInput('markdown', 'different draft');
        fixture.detectChanges();
        expect(monaco.getText()).toBe('different draft');
        fixture.componentRef.setInput('markdown', '');
        fixture.detectChanges();
        expect(monaco.getText()).toBe('');
    });
});
