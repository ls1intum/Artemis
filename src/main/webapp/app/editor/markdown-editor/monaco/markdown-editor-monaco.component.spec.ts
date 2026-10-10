import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Mock, vi } from 'vitest';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { ColorAction } from 'app/editor/monaco-editor/model/actions/color.action';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CdkDragMove } from '@angular/cdk/drag-drop';
import { UrlAction } from 'app/editor/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/editor/monaco-editor/model/actions/attachment.action';
import { FormulaAction } from 'app/editor/monaco-editor/model/actions/formula.action';
import { TestCaseAction } from 'app/editor/monaco-editor/model/actions/test-case.action';
import { TaskAction } from 'app/editor/monaco-editor/model/actions/task.action';
import { FullscreenAction } from 'app/editor/monaco-editor/model/actions/fullscreen.action';
import { MonacoEditorOptionPreset } from 'app/editor/monaco-editor/model/monaco-editor-option-preset.model';
import { COMMUNICATION_MARKDOWN_EDITOR_OPTIONS } from 'app/editor/monaco-editor/monaco-editor-option.helper';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { CommunicationService } from 'app/communication/service/communication.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { PostingEditType } from 'app/communication/communication.util';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { EmojiAction } from 'app/editor/monaco-editor/model/actions/emoji.action';
import { LectureAttachmentReferenceAction, LectureWithDetails } from 'app/editor/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { ReferenceType } from 'app/communication/communication.util';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';
import { CommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { EditorRange } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { MenuItem } from 'primeng/api';
import { TieredMenu } from 'primeng/tieredmenu';
import { of } from 'rxjs';

// Capture the global ResizeObserver provided by the test setup so it can be restored after each test.
const originalResizeObserver = globalThis.ResizeObserver;

@Component({
    template: `
        <jhi-markdown-editor-monaco>
            <div id="previewMonaco"><span class="preview-probe"></span></div>
        </jhi-markdown-editor-monaco>
    `,
    imports: [MarkdownEditorMonacoComponent],
})
class PreviewHostComponent {}

describe('MarkdownEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownEditorMonacoComponent>;
    let comp: MarkdownEditorMonacoComponent;
    let fileUploaderService: FileUploaderService;

    const TAB_EDIT = MarkdownEditorMonacoComponent.TAB_EDIT;
    const TAB_PREVIEW = MarkdownEditorMonacoComponent.TAB_PREVIEW;
    const TAB_VISUAL = MarkdownEditorMonacoComponent.TAB_VISUAL;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MarkdownEditorMonacoComponent],
            providers: [
                MockProvider(FileUploaderService),
                MockProvider(AlertService),
                MockProvider(CourseConversationsService),
                MockProvider(CommunicationService),
                MockProvider(ProfileService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            // Swap the heavy editor children for lightweight mocks; the Monaco editor in particular must not load
            // real Monaco. PrimeNG components and other directives/pipes stay real.
            .overrideComponent(MarkdownEditorMonacoComponent, {
                remove: { imports: [MonacoEditorComponent, PostingButtonComponent, RedirectToIrisButtonComponent] },
                add: {
                    imports: [MockComponent(MonacoEditorComponent), MockComponent(PostingButtonComponent), MockComponent(RedirectToIrisButtonComponent)],
                },
            })
            .compileComponents();
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
        fixture = TestBed.createComponent(MarkdownEditorMonacoComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('externalHeight', true);
        fixture.componentRef.setInput('domainActions', [new FormulaAction(), new TaskAction(), new TestCaseAction()]);
        fileUploaderService = TestBed.inject(FileUploaderService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        globalThis.ResizeObserver = originalResizeObserver;
    });

    it('should limit the vertical drag position based on the input values', () => {
        fixture.componentRef.setInput('initialEditorHeight', MarkdownEditorHeight.MEDIUM);
        fixture.componentRef.setInput('resizableMinHeight', MarkdownEditorHeight.SMALL);
        fixture.componentRef.setInput('resizableMaxHeight', MarkdownEditorHeight.LARGE);
        fixture.componentRef.setInput('enableResize', true);
        fixture.detectChanges();
        const wrapperTop = comp.wrapper().nativeElement.getBoundingClientRect().top;
        const minPoint = comp.constrainDragPosition({ x: 0, y: wrapperTop - 10000 });
        expect(minPoint.y).toBe(wrapperTop + comp.resizableMinHeight());
        const maxPoint = comp.constrainDragPosition({ x: 0, y: wrapperTop + 10000 });
        expect(maxPoint.y).toBe(wrapperTop + comp.resizableMaxHeight());
    });

    it('should update the content and emit markdownChange on text change', () => {
        const text = 'test';
        const textChangeSpy = vi.spyOn(comp.markdownChange, 'emit');
        fixture.detectChanges();
        comp.onTextChanged({ text: text, fileName: 'test-file.md' });
        expect(textChangeSpy).toHaveBeenCalledWith(text);
        expect(comp.currentMarkdown()).toBe(text);
    });

    it('should notify when switching to preview mode', () => {
        const emitSpy = vi.spyOn(comp.onPreviewSelect, 'emit');
        fixture.detectChanges();
        comp.onTabChange(TAB_PREVIEW);
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should preview the live Monaco value before the debounced text change emits', () => {
        fixture.detectChanges();
        vi.spyOn(comp.monacoEditor()!, 'getText').mockReturnValue('**Fresh preview**');

        comp.onTabChange(TAB_PREVIEW);

        expect(comp.currentMarkdown()).toBe('**Fresh preview**');
        const html = comp.defaultPreviewHtml() as { changingThisBreaksApplicationSecurity: string };
        expect(html.changingThisBreaksApplicationSecurity).toContain('<strong>Fresh preview</strong>');
    });

    it('should layout and focus the editor when the edit tab is shown', () => {
        fixture.detectChanges();
        comp.onTabChange(TAB_PREVIEW);
        const adjustEditorDimensionsSpy = vi.spyOn(comp, 'adjustEditorDimensions');
        const focusSpy = vi.spyOn(comp.monacoEditor()!, 'focus');
        comp.onTabChange(TAB_EDIT);
        // The editor layout/focus is scheduled via afterNextRender; flush it.
        fixture.detectChanges();
        expect(adjustEditorDimensionsSpy).toHaveBeenCalledOnce();
        expect(focusSpy).toHaveBeenCalledOnce();
    });

    it('should not layout or focus the editor when a non-edit tab is shown', () => {
        fixture.detectChanges();
        const adjustEditorDimensionsSpy = vi.spyOn(comp, 'adjustEditorDimensions');
        const focusSpy = vi.spyOn(comp.monacoEditor()!, 'focus');
        comp.onTabChange(TAB_PREVIEW);
        fixture.detectChanges();
        expect(adjustEditorDimensionsSpy).not.toHaveBeenCalled();
        expect(focusSpy).not.toHaveBeenCalled();
    });

    it('should emit when leaving the visual tab', () => {
        const emitSpy = vi.spyOn(comp.onLeaveVisualTab, 'emit');
        fixture.detectChanges();
        comp.onTabChange(TAB_VISUAL);
        comp.onTabChange(TAB_EDIT);
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should not create a review comment manager when review comments are disabled', () => {
        fixture.detectChanges();
        const getReviewCommentManagerSpy = vi.spyOn(comp as any, 'getReviewCommentManager');
        (comp as any).reviewCommentManager = undefined;
        fixture.componentRef.setInput('enableExerciseReviewComments', false);

        (comp as any).updateReviewCommentButton();

        expect(getReviewCommentManagerSpy).not.toHaveBeenCalled();
    });

    it('should use initial line number as fallback for problem statement threads', () => {
        const thread = { initialLineNumber: 8 } as any;
        expect((comp as any).getProblemStatementThreadLine(thread)).toBe(7);
    });

    it('should prefer current line number over initial line number for problem statement threads', () => {
        const thread = { lineNumber: 5, initialLineNumber: 8 } as any;
        expect((comp as any).getProblemStatementThreadLine(thread)).toBe(4);
    });

    it('should expose review comment manager callbacks for problem statement context', () => {
        fixture.detectChanges();
        (comp.monacoEditor()! as any).getEditor = vi.fn().mockReturnValue({
            onDidScrollChange: vi.fn().mockReturnValue({ dispose: vi.fn() }),
        });

        fixture.componentRef.setInput('enableExerciseReviewComments', true);
        fixture.componentRef.setInput('showLocationWarning', false);
        fixture.changeDetectorRef.detectChanges();

        const manager = (comp as any).getReviewCommentManager();
        const config = (manager as any).config;

        expect(config.shouldShowHoverButton()).toBe(true);
        expect(config.canSubmit()).toBe(true);
        expect(config.getDraftFileName()).toBe('problem_statement.md');
        expect(config.getDraftContext({ lineNumber: 3, fileName: 'ignored.md' })).toEqual({
            targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
        });

        const thread = { id: 1, targetType: CommentThreadLocationType.PROBLEM_STATEMENT, initialLineNumber: 2, lineNumber: 6 } as any;
        (comp as any).exerciseReviewCommentService.threads.set([thread]);

        expect(config.getThreads()).toEqual([thread]);
        expect(config.filterThread(thread)).toBe(true);
        expect(config.filterThread({ ...thread, targetType: CommentThreadLocationType.TEMPLATE_REPO })).toBe(false);
        expect(config.getThreadLine(thread)).toBe(5);

        const onAddSpy = vi.spyOn(comp.onAddReviewComment, 'emit');
        config.onAdd({ lineNumber: 7, fileName: 'problem_statement.md' });
        expect(onAddSpy).toHaveBeenCalledOnce();
        expect(onAddSpy).toHaveBeenCalledWith({ lineNumber: 7, fileName: 'problem_statement.md' });

        expect(config.showLocationWarning()).toBe(false);
        fixture.componentRef.setInput('showLocationWarning', true);
        fixture.changeDetectorRef.detectChanges();
        expect(config.showLocationWarning()).toBe(true);
        expect(config.canSubmit()).toBe(false);

        comp.inEditMode.set(false);
        expect(config.shouldShowHoverButton()).toBe(false);
    });

    it('should still update review comment button when a manager already exists', () => {
        const updateHoverButton = vi.fn();
        (comp as any).reviewCommentManager = {
            updateHoverButton,
            updateDraftInputs: vi.fn(),
            tryUpdateThreadInputs: vi.fn(),
            clearDrafts: vi.fn(),
            disposeAll: vi.fn(),
            renderWidgets: vi.fn(),
        };
        fixture.componentRef.setInput('enableExerciseReviewComments', false);
        fixture.changeDetectorRef.detectChanges();

        (comp as any).updateReviewCommentButton();

        expect(updateHoverButton).toHaveBeenCalled();
    });

    it.each([
        { tab: TAB_EDIT, flags: [true, false, false] },
        { tab: TAB_PREVIEW, flags: [false, true, false] },
        { tab: TAB_VISUAL, flags: [false, false, true] },
    ])(`should set the correct flags when navigating to $tab`, ({ tab, flags }) => {
        fixture.detectChanges();
        comp.onTabChange(tab);
        expect([comp.inEditMode(), comp.inPreviewMode(), comp.inVisualMode()]).toEqual(flags);
    });

    it('should embed manually uploaded files', () => {
        const inputEvent = { target: { files: [new File([''], 'test.png')] } } as unknown as InputEvent;
        const embedFilesStub = vi.spyOn(comp, 'embedFiles').mockImplementation(() => {});
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        comp.onFileUpload(inputEvent);
        expect(embedFilesStub).toHaveBeenCalledOnce();
        expect(embedFilesStub).toHaveBeenCalledWith(files, (inputEvent as any).target);
    });

    it('should not embed via manual upload if the event contains no files', () => {
        const embedFilesStub = vi.spyOn(comp, 'embedFiles').mockImplementation(() => {});
        fixture.detectChanges();
        comp.onFileUpload({ target: { files: [] } } as any);
        expect(embedFilesStub).not.toHaveBeenCalled();
    });

    it('should embed dropped files', () => {
        const embedFilesStub = vi.spyOn(comp, 'embedFiles').mockImplementation(() => {});
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        const event = { dataTransfer: { files }, preventDefault: vi.fn() };
        comp.onFileDrop(event as any);
        expect(embedFilesStub).toHaveBeenCalledOnce();
        expect(embedFilesStub).toHaveBeenCalledWith(files);
    });

    it('should not try to embed via drop if the event contains no files', () => {
        const embedFilesStub = vi.spyOn(comp, 'embedFiles').mockImplementation(() => {});
        fixture.detectChanges();
        comp.onFileDrop({ dataTransfer: { files: [] }, preventDefault: vi.fn() } as any);
        expect(embedFilesStub).not.toHaveBeenCalled();
    });

    it('should notify if the upload of a markdown file failed', async () => {
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'addAlert');
        const files = [new File([''], 'test.png')];
        const uploadMarkdownFileStub = vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockRejectedValue(new Error('Test error'));
        fixture.detectChanges();
        comp.embedFiles(files);
        await vi.waitFor(() => {
            expect(alertSpy).toHaveBeenCalledOnce();
            expect(uploadMarkdownFileStub).toHaveBeenCalledOnce();
        });
    });

    it('should set the upload callback on the attachment actions', () => {
        const attachmentAction = new AttachmentAction();
        const setUploadCallbackSpy = vi.spyOn(attachmentAction, 'setUploadCallback');
        const embedFilesStub = vi.spyOn(comp, 'embedFiles').mockImplementation(() => {});
        fixture.componentRef.setInput('defaultActions', [attachmentAction]);
        fixture.componentRef.setInput('enableFileUpload', true);
        fixture.detectChanges();
        expect(setUploadCallbackSpy).toHaveBeenCalledOnce();
        // Check if the correct function is passed to the action.
        const argument = setUploadCallbackSpy.mock.calls[0][0];
        expect(argument).toBeDefined();
        argument!([]);
        expect(embedFilesStub).toHaveBeenCalledOnce();
        expect(embedFilesStub).toHaveBeenCalledWith([]);
    });

    it('should embed image and .pdf files', async () => {
        const urlAction = new UrlAction();
        const urlStub = vi.spyOn(urlAction, 'executeInCurrentEditor').mockImplementation(() => {});
        const attachmentAction = new AttachmentAction();
        const attachmentStub = vi.spyOn(attachmentAction, 'executeInCurrentEditor').mockImplementation(() => {});
        const fileInformation = [
            { file: new File([''], 'test.png'), url: 'https://test.invalid/generated42.png' },
            { file: new File([''], 'test.pdf'), url: 'https://test.invalid/generated1234.pdf' },
        ];
        fixture.componentRef.setInput('defaultActions', [urlAction, attachmentAction]);
        const files = [new File([''], 'test.png'), new File([''], 'test.pdf')];
        const uploadMarkdownFileStub = vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockImplementation((file: File) => {
            const path = file.name.endsWith('.png') ? fileInformation[0].url : fileInformation[1].url;
            return Promise.resolve({ path });
        });
        fixture.detectChanges();
        comp.embedFiles(files);
        // Wait for both uploads to settle and for both files to be embedded (the .png via the attachment action and
        // the .pdf via the url action) before asserting the exact arguments, so neither embed call is checked early.
        await vi.waitFor(() => {
            expect(uploadMarkdownFileStub).toHaveBeenCalledTimes(2);
            expect(attachmentStub).toHaveBeenCalledOnce();
            expect(urlStub).toHaveBeenCalledOnce();
        });
        // Each file should be uploaded.
        expect(uploadMarkdownFileStub).toHaveBeenNthCalledWith(1, files[0]);
        expect(uploadMarkdownFileStub).toHaveBeenNthCalledWith(2, files[1]);
        // Each file should be embedded. PDFs should be embedded as URLs.
        expect(attachmentStub).toHaveBeenCalledWith({
            url: fileInformation[0].url,
            text: fileInformation[0].file.name,
        });
        expect(urlStub).toHaveBeenCalledWith({
            url: fileInformation[1].url,
            text: fileInformation[1].file.name,
        });
    });

    it('should not embed files if file upload is disabled', () => {
        const urlAction = new UrlAction();
        const urlStub = vi.spyOn(urlAction, 'executeInCurrentEditor').mockImplementation(() => {});
        const attachmentAction = new AttachmentAction();
        const attachmentStub = vi.spyOn(attachmentAction, 'executeInCurrentEditor').mockImplementation(() => {});
        const files = [new File([''], 'test.png'), new File([''], 'test.pdf')];
        fixture.componentRef.setInput('defaultActions', [urlAction, attachmentAction]);
        fixture.componentRef.setInput('enableFileUpload', false);
        fixture.detectChanges();
        comp.embedFiles(files);
        expect(urlStub).not.toHaveBeenCalled();
        expect(attachmentStub).not.toHaveBeenCalled();
    });

    it('should execute the action when clicked', () => {
        const action = new UrlAction();
        const executeInCurrentEditorStub = vi.spyOn(action, 'executeInCurrentEditor').mockImplementation(() => {});
        fixture.componentRef.setInput('defaultActions', [action]);
        fixture.detectChanges();
        comp.handleActionClick(new MouseEvent('click'), action);
        expect(executeInCurrentEditorStub).toHaveBeenCalledOnce();
    });

    it('should pass the correct color as argument to the color action', () => {
        fixture.componentRef.setInput('colorAction', new ColorAction());
        fixture.detectChanges();
        const executeInCurrentEditorStub = vi.spyOn(comp.colorAction()!, 'executeInCurrentEditor').mockImplementation(() => {});
        const markdownColors = comp.colors;
        for (let i = 0; i < markdownColors.length; i++) {
            const color = markdownColors[i];
            comp.onSelectColor(color);
            expect(executeInCurrentEditorStub).toHaveBeenNthCalledWith(i + 1, { color: comp.colorToClassMap.get(color) });
        }
    });

    it('should pass the entire element to the fullscreen action for external height', () => {
        fixture.componentRef.setInput('externalHeight', true);
        const fullscreenAction = new FullscreenAction();
        fixture.componentRef.setInput('metaActions', [fullscreenAction]);
        fixture.detectChanges();
        expect(fullscreenAction.element).toBe(comp.fullElement().nativeElement);
    });

    it('should pass the wrapper element to the fullscreen action when height is managed internally', () => {
        fixture.componentRef.setInput('externalHeight', false);
        fixture.componentRef.setInput('initialEditorHeight', MarkdownEditorHeight.MEDIUM);
        const fullscreenAction = new FullscreenAction();
        fixture.componentRef.setInput('metaActions', [fullscreenAction]);
        fixture.detectChanges();
        expect(fullscreenAction.element).toBe(comp.wrapper().nativeElement);
    });

    it('should compute height 0 for a missing element', () => {
        fixture.detectChanges();
        expect(comp.getElementClientHeight(undefined)).toBe(0);
    });

    it('should not react to content height changes if the height is not liked to the editor size', () => {
        fixture.componentRef.setInput('externalHeight', false);
        fixture.componentRef.setInput('linkEditorHeightToContentHeight', false);
        fixture.componentRef.setInput('initialEditorHeight', MarkdownEditorHeight.MEDIUM);
        fixture.detectChanges();
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.MEDIUM);
        comp.onContentHeightChanged(100);
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.MEDIUM);
    });

    it('should not react to content height changes if file upload is enabled but the footer has not loaded', () => {
        fixture.componentRef.setInput('externalHeight', false);
        fixture.componentRef.setInput('initialEditorHeight', MarkdownEditorHeight.SMALL);
        vi.spyOn(comp, 'getElementClientHeight').mockReturnValue(0);
        fixture.componentRef.setInput('enableFileUpload', true);
        fixture.componentRef.setInput('linkEditorHeightToContentHeight', true);
        fixture.componentRef.setInput('resizableMinHeight', MarkdownEditorHeight.INLINE);
        fixture.componentRef.setInput('resizableMaxHeight', MarkdownEditorHeight.LARGE);
        fixture.detectChanges();
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.SMALL);
        comp.onContentHeightChanged(9999);
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.SMALL);
    });

    it('should react to content height changes if the height is linked to the editor', () => {
        fixture.componentRef.setInput('externalHeight', false);
        vi.spyOn(comp, 'getElementClientHeight').mockReturnValue(20);
        fixture.componentRef.setInput('linkEditorHeightToContentHeight', true);
        fixture.componentRef.setInput('resizableMaxHeight', MarkdownEditorHeight.LARGE);
        fixture.detectChanges();
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.SMALL);
        comp.onContentHeightChanged(1500);
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.LARGE);
        comp.onContentHeightChanged(20);
        expect(comp.targetWrapperHeight()).toBe(MarkdownEditorHeight.SMALL);
    });

    it('should adjust the wrapper height when resized manually', () => {
        fixture.componentRef.setInput('externalHeight', false);
        const cdkDragMove = { source: { reset: vi.fn() }, pointerPosition: { y: 300 } } as unknown as CdkDragMove;
        const wrapperTop = 100;
        const dragElemHeight = 20;
        fixture.detectChanges();
        vi.spyOn(comp, 'getElementClientHeight').mockReturnValue(dragElemHeight);
        vi.spyOn(comp.wrapper().nativeElement, 'getBoundingClientRect').mockReturnValue({ top: wrapperTop } as DOMRect);
        comp.onResizeMoved(cdkDragMove);
        expect(comp.targetWrapperHeight()).toBe(300 - wrapperTop - dragElemHeight / 2);
    });

    it('should use the correct options to enable text field mode', () => {
        fixture.detectChanges();
        const applySpy = vi.spyOn(comp.monacoEditor()!, 'applyOptionPreset');
        comp.enableTextFieldMode();
        expect(applySpy).toHaveBeenCalledOnce();
        expect(applySpy).toHaveBeenCalledWith(COMMUNICATION_MARKDOWN_EDITOR_OPTIONS);
    });

    it('should apply option presets to the editor', () => {
        fixture.detectChanges();
        const applySpy = vi.spyOn(comp.monacoEditor()!, 'applyOptionPreset');
        const preset = new MonacoEditorOptionPreset({ lineNumbers: 'off' });
        comp.applyOptionPreset(preset);
        expect(applySpy).toHaveBeenCalledOnce();
        expect(applySpy).toHaveBeenCalledWith(preset);
    });

    it('should render markdown callouts correctly', () => {
        comp.setMarkdown(
            `
> [!NOTE]
> Highlights information that users should take into account, even when skimming.

> [!TIP]
> Optional information to help a user be more successful.

> [!IMPORTANT]
> Crucial information necessary for users to succeed.

> [!WARNING]
> Critical content demanding immediate user attention due to potential risks.

> [!CAUTION]
> Negative potential consequences of an action.`,
        );

        const expectedHtml = `<div class="markdown-alert markdown-alert-note"><p class="markdown-alert-title"><svg class="octicon octicon-info mr-2" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"></path></svg>Note</p><p>Highlights information that users should take into account, even when skimming.</p>
</div>
<div class="markdown-alert markdown-alert-tip"><p class="markdown-alert-title"><svg class="octicon octicon-light-bulb mr-2" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path d="M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 0 1-1.484.211c-.04-.282-.163-.547-.37-.847a8.456 8.456 0 0 0-.542-.68c-.084-.1-.173-.205-.268-.32C3.201 7.75 2.5 6.766 2.5 5.25 2.5 2.31 4.863 0 8 0s5.5 2.31 5.5 5.25c0 1.516-.701 2.5-1.328 3.259-.095.115-.184.22-.268.319-.207.245-.383.453-.541.681-.208.3-.33.565-.37.847a.751.751 0 0 1-1.485-.212c.084-.593.337-1.078.621-1.489.203-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637-3.75-4-3.75ZM5.75 12h4.5a.75.75 0 0 1 0 1.5h-4.5a.75.75 0 0 1 0-1.5ZM6 15.25a.75.75 0 0 1 .75-.75h2.5a.75.75 0 0 1 0 1.5h-2.5a.75.75 0 0 1-.75-.75Z"></path></svg>Tip</p><p>Optional information to help a user be more successful.</p>
</div>
<div class="markdown-alert markdown-alert-important"><p class="markdown-alert-title"><svg class="octicon octicon-report mr-2" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path d="M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0 1 14.25 13H8.06l-2.573 2.573A1.458 1.458 0 0 1 3 14.543V13H1.75A1.75 1.75 0 0 1 0 11.25Zm1.75-.25a.25.25 0 0 0-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h6.5a.25.25 0 0 0 .25-.25v-9.5a.25.25 0 0 0-.25-.25Zm7 2.25v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 9a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z"></path></svg>Important</p><p>Crucial information necessary for users to succeed.</p>
</div>
<div class="markdown-alert markdown-alert-warning"><p class="markdown-alert-title"><svg class="octicon octicon-alert mr-2" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path d="M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0 1 14.082 15H1.918a1.75 1.75 0 0 1-1.543-2.575Zm1.763.707a.25.25 0 0 0-.44 0L1.698 13.132a.25.25 0 0 0 .22.368h12.164a.25.25 0 0 0 .22-.368Zm.53 3.996v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z"></path></svg>Warning</p><p>Critical content demanding immediate user attention due to potential risks.</p>
</div>
<div class="markdown-alert markdown-alert-caution"><p class="markdown-alert-title"><svg class="octicon octicon-stop mr-2" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path d="M4.47.22A.749.749 0 0 1 5 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 0 1-.22.53l-4.25 4.25A.749.749 0 0 1 11 16H5a.749.749 0 0 1-.53-.22L.22 11.53A.749.749 0 0 1 0 11V5c0-.199.079-.389.22-.53Zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5ZM8 4a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"></path></svg>Caution</p><p>Negative potential consequences of an action.</p>
</div>`;
        comp.parseMarkdown();
        // The markdown editor generates SafeHtml to prevent certain client-side attacks, but for this test, we only need the raw HTML.
        const html = comp.defaultPreviewHtml() as { changingThisBreaksApplicationSecurity: string };
        const renderedHtml = html.changingThisBreaksApplicationSecurity;
        expect(renderedHtml).toEqual(expectedHtml);
    });

    it('should handle invalid callout type gracefully', () => {
        comp.setMarkdown(
            `
> [!INVALID]
> This is an invalid callout type.`,
        );
        comp.parseMarkdown();
        // The markdown editor generates SafeHtml to prevent certain client-side attacks, but for this test, we only need the raw HTML.
        const html = comp.defaultPreviewHtml() as { changingThisBreaksApplicationSecurity: string };
        const renderedHtml = html.changingThisBreaksApplicationSecurity;
        expect(renderedHtml).toContain('<blockquote>');
    });

    it('should render nested content within callouts', () => {
        comp.setMarkdown(
            `
> [!NOTE]
> # Heading
> - List item 1
> - List item 2
>
> Nested blockquote:
> > This is nested.`,
        );

        comp.parseMarkdown();

        const html = comp.defaultPreviewHtml() as { changingThisBreaksApplicationSecurity: string };
        // The markdown editor generates SafeHtml to prevent certain client-side attacks, but for this test, we only need the raw HTML.
        const renderedHtml = html.changingThisBreaksApplicationSecurity;
        expect(renderedHtml).toContain('<h1>Heading</h1>');
        expect(renderedHtml).toContain('<ul>');
        expect(renderedHtml).toContain('<blockquote>');
    });

    it('should always show all text actions if not in communication mode', () => {
        fixture.componentRef.setInput('isInCommunication', false);
        fixture.detectChanges();

        expect(comp.showTextStyleActions()).toBe(true);
        expect(comp.showNonTextStyleActions()).toBe(true);
    });

    it('should hide text style actions in communication mode by default', () => {
        fixture.componentRef.setInput('isInCommunication', true);
        fixture.detectChanges();

        expect(comp.showTextStyleActions()).toBe(false);
        expect(comp.showNonTextStyleActions()).toBe(true);
    });

    it('should show text style actions in communication mode when text is selected', () => {
        fixture.componentRef.setInput('isInCommunication', true);
        fixture.detectChanges();

        comp.updateEditorActionsVisibility({ startLineNumber: 1, endLineNumber: 1, startColumn: 10, endColumn: 20 });

        expect(comp.showTextStyleActions()).toBe(true);
        expect(comp.showNonTextStyleActions()).toBe(false);
    });

    it('should emit closeEditor on close button click', () => {
        fixture.detectChanges();
        const emitSpy = vi.spyOn(comp.closeEditor, 'emit');

        comp.onCloseButtonClick();

        expect(emitSpy).toHaveBeenCalled();
    });

    it('should dispose selection change listener on destroy', () => {
        fixture.detectChanges();

        // Mock the disposable
        const mockDisposable = { dispose: vi.fn() };
        (comp as any).selectionChangeDisposable = mockDisposable;

        comp.ngOnDestroy();

        expect(mockDisposable.dispose).toHaveBeenCalled();
    });

    it('should return selection from getSelection', () => {
        fixture.detectChanges();

        const mockSelection = {
            startLineNumber: 1,
            endLineNumber: 3,
            startColumn: 1,
            endColumn: 10,
        };

        vi.spyOn(comp.monacoEditor()!, 'getSelection').mockReturnValue(mockSelection as any);

        const result = comp.getSelection();

        expect(result).toEqual({
            startLine: 1,
            endLine: 3,
            startColumn: 1,
            endColumn: 10,
        });
    });

    it('should return undefined from getSelection when no selection', () => {
        fixture.detectChanges();

        vi.spyOn(comp.monacoEditor()!, 'getSelection').mockReturnValue(undefined);

        const result = comp.getSelection();

        expect(result).toBeUndefined();
    });

    it('should return undefined from getSelection when monacoEditor is undefined', () => {
        fixture.detectChanges();

        (comp as any).monacoEditor = () => undefined;

        const result = comp.getSelection();

        expect(result).toBeUndefined();
    });

    describe('projected preview content', () => {
        let hostFixture: ComponentFixture<PreviewHostComponent>;
        let editor: MarkdownEditorMonacoComponent;

        const probe = () => hostFixture.nativeElement.querySelector('.preview-probe') as HTMLElement | null;
        const isHidden = (element: HTMLElement | null) => element?.closest('.hidden') != null;

        beforeEach(() => {
            hostFixture = TestBed.createComponent(PreviewHostComponent);
            hostFixture.detectChanges();
            editor = hostFixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent)).componentInstance;
        });

        // Preview content that renders into the document at startup (task test statuses, PlantUML diagrams) finds its anchors with a
        // document query, so it has to be connected from the start and not only after the preview tab was opened.
        it('should be connected to the document before the preview tab is opened', () => {
            expect(editor.activeTab()).toBe(MarkdownEditorMonacoComponent.TAB_EDIT);
            expect(probe()?.isConnected).toBe(true);
            expect(document.body.contains(probe())).toBe(true);
            expect(document.querySelectorAll('.preview-probe')).toHaveLength(1);
            expect(isHidden(probe())).toBe(true);
        });

        it('should be shown while the preview tab is active and hidden again on leaving it', () => {
            const initialProbe = probe();
            const onPreviewSelect = vi.fn();
            const onEditSelect = vi.fn();
            editor.onPreviewSelect.subscribe(onPreviewSelect);
            editor.onEditSelect.subscribe(onEditSelect);

            editor.onTabChange(MarkdownEditorMonacoComponent.TAB_PREVIEW);
            hostFixture.detectChanges();
            expect(editor.inPreviewMode()).toBe(true);
            expect(probe()?.isConnected).toBe(true);
            expect(isHidden(probe())).toBe(false);
            expect(onPreviewSelect).toHaveBeenCalledOnce();
            expect(onEditSelect).not.toHaveBeenCalled();

            editor.onTabChange(MarkdownEditorMonacoComponent.TAB_EDIT);
            hostFixture.detectChanges();
            expect(editor.inPreviewMode()).toBe(false);
            expect(probe()?.isConnected).toBe(true);
            expect(isHidden(probe())).toBe(true);
            expect(onEditSelect).toHaveBeenCalledOnce();

            // The projected content is kept alive across the tab switches instead of being recreated.
            expect(probe()).toBe(initialProbe);
        });

        it('should stay hidden and attached while the visual tab is active', () => {
            const initialProbe = probe();

            editor.onTabChange(MarkdownEditorMonacoComponent.TAB_VISUAL);
            hostFixture.detectChanges();
            expect(editor.inVisualMode()).toBe(true);
            expect(editor.inPreviewMode()).toBe(false);
            expect(probe()).toBe(initialProbe);
            expect(probe()?.isConnected).toBe(true);
            expect(isHidden(probe())).toBe(true);

            // Visual to preview and back keeps working, the visual tab only adds its own lazily rendered content.
            editor.onTabChange(MarkdownEditorMonacoComponent.TAB_PREVIEW);
            hostFixture.detectChanges();
            expect(isHidden(probe())).toBe(false);

            editor.onTabChange(MarkdownEditorMonacoComponent.TAB_VISUAL);
            hostFixture.detectChanges();
            expect(isHidden(probe())).toBe(true);
            expect(probe()).toBe(initialProbe);
        });
    });

    describe('default preview content', () => {
        const defaultPreview = () => fixture.nativeElement.querySelector('.markdown-preview') as HTMLElement | null;
        const isHidden = (element: HTMLElement | null) => element?.closest('.hidden') != null;

        it('should attach the default preview at startup, keep it hidden in edit mode and update it while hidden', () => {
            fixture.detectChanges();

            const preview = defaultPreview();
            expect(preview).not.toBeNull();
            expect(preview!.isConnected).toBe(true);
            expect(isHidden(preview)).toBe(true);
            expect(preview!.innerHTML).toBe('');

            comp.setMarkdown('**bold text**');
            comp.parseMarkdown();
            fixture.detectChanges();

            expect(defaultPreview()).toBe(preview);
            expect(preview!.innerHTML).toContain('<strong>bold text</strong>');
            expect(isHidden(preview)).toBe(true);
        });

        it('should show the rendered default preview when the preview tab is activated', () => {
            fixture.detectChanges();
            const preview = defaultPreview()!;
            vi.spyOn(comp.monacoEditor()!, 'getText').mockReturnValue('# Heading\n\n*emphasis*');

            comp.onTabChange(TAB_PREVIEW);
            fixture.detectChanges();

            expect(defaultPreview()).toBe(preview);
            expect(isHidden(preview)).toBe(false);
            expect(preview.innerHTML).toContain('<h1>Heading</h1>');
            expect(preview.innerHTML).toContain('<em>emphasis</em>');

            comp.onTabChange(TAB_EDIT);
            fixture.detectChanges();
            expect(isHidden(preview)).toBe(true);
            expect(preview.innerHTML).toContain('<h1>Heading</h1>');
        });

        it('should not render a default preview and not compute its html if it is disabled', () => {
            fixture.componentRef.setInput('showDefaultPreview', false);
            fixture.detectChanges();
            vi.spyOn(comp.monacoEditor()!, 'getText').mockReturnValue('**bold text**');

            comp.onTabChange(TAB_PREVIEW);
            fixture.detectChanges();

            expect(defaultPreview()).toBeNull();
            expect(comp.defaultPreviewHtml()).toBeUndefined();
        });
    });

    describe('send and iris buttons in the preview container', () => {
        // In edit mode the toolbar is visible and carries the send button with the id "save". Everything else inside a hidden container belongs to the preview.
        const sendButtons = () => Array.from(fixture.nativeElement.querySelectorAll('button[jhi-posting-button]')) as HTMLElement[];
        const irisButtons = () => Array.from(fixture.nativeElement.querySelectorAll('jhi-redirect-to-iris-button')) as HTMLElement[];
        const previewSendButtons = () => sendButtons().filter((button) => button.id !== 'save');
        const previewIrisButtons = () => irisButtons().filter((button) => button.closest('.hidden') !== null);

        it.each([
            { isInCommunication: true, editType: PostingEditType.CREATE, expectedButtons: 1 },
            { isInCommunication: true, editType: undefined, expectedButtons: 1 },
            { isInCommunication: true, editType: PostingEditType.UPDATE, expectedButtons: 0 },
            { isInCommunication: false, editType: PostingEditType.CREATE, expectedButtons: 0 },
            { isInCommunication: false, editType: undefined, expectedButtons: 0 },
        ])('should render $expectedButtons send and iris button in the hidden preview for communication=$isInCommunication and editType=$editType', (testCase) => {
            fixture.componentRef.setInput('isInCommunication', testCase.isInCommunication);
            fixture.componentRef.setInput('editType', testCase.editType);
            fixture.detectChanges();

            expect(previewSendButtons()).toHaveLength(testCase.expectedButtons);
            expect(previewIrisButtons()).toHaveLength(testCase.expectedButtons);
            for (const button of previewSendButtons()) {
                expect(button.closest('.hidden')).not.toBeNull();
            }
            // The toolbar offers the same actions next to the editor, so the total is one more than the preview alone.
            expect(sendButtons()).toHaveLength(testCase.expectedButtons * 2);
            expect(irisButtons()).toHaveLength(testCase.expectedButtons * 2);
        });

        it('should reveal the send and iris button of the preview container with the preview tab', () => {
            fixture.componentRef.setInput('isInCommunication', true);
            fixture.componentRef.setInput('editType', PostingEditType.CREATE);
            fixture.detectChanges();
            const sendButton = previewSendButtons()[0];
            const irisButton = previewIrisButtons()[0];
            expect(sendButton.closest('.hidden')).not.toBeNull();
            expect(irisButton.closest('.hidden')).not.toBeNull();

            comp.onTabChange(TAB_PREVIEW);
            fixture.detectChanges();

            expect(sendButton.isConnected).toBe(true);
            expect(sendButton.closest('.hidden')).toBeNull();
            expect(irisButton.closest('.hidden')).toBeNull();
        });
    });

    describe('selection and scroll listeners', () => {
        let selectionListener: (selection: EditorRange | undefined) => void;
        let scrollListener: () => void;
        let selectionDisposable: { dispose: Mock<() => void> };
        let scrollDisposable: { dispose: Mock<() => void> };
        let selectionEmitSpy: ReturnType<typeof vi.spyOn>;

        const selection = (startColumn: number, endColumn: number): EditorRange => ({ startLineNumber: 2, endLineNumber: 3, startColumn, endColumn });

        const mockEditorQueries = (selectedText: string | undefined) => {
            const editor = comp.monacoEditor()!;
            vi.spyOn(editor, 'getModel').mockReturnValue(selectedText === undefined ? (null as any) : ({ getValueInRange: () => selectedText } as any));
            vi.spyOn(editor, 'getScrolledVisiblePosition').mockReturnValue({ top: 10, left: 5, height: 20 });
            vi.spyOn(editor, 'getDomNode').mockReturnValue({ getBoundingClientRect: () => ({ top: 100, left: 50 }) } as HTMLElement);
        };

        beforeEach(() => {
            selectionDisposable = { dispose: vi.fn<() => void>() };
            scrollDisposable = { dispose: vi.fn<() => void>() };
            fixture.detectChanges();
            // The mocked editor never calls listeners. Register them again against spies that capture the listeners,
            // so the tests can drive them. Tearing the component down first removes the listeners of the first registration.
            comp.ngOnDestroy();
            const editor = comp.monacoEditor()!;
            vi.spyOn(editor, 'onSelectionChange').mockImplementation((listener) => {
                selectionListener = listener;
                return selectionDisposable;
            });
            vi.spyOn(editor, 'onScrollChange').mockImplementation((listener) => {
                scrollListener = listener;
                return scrollDisposable;
            });
            comp.ngAfterViewInit();
            selectionEmitSpy = vi.spyOn(comp.onSelectionChange, 'emit');
        });

        it('should emit the selected text with its screen position below the end of the selection', () => {
            mockEditorQueries('selected');

            selectionListener(selection(4, 9));

            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith({
                startLine: 2,
                endLine: 3,
                startColumn: 4,
                endColumn: 9,
                selectedText: 'selected',
                // editor top + caret top + caret height + 5px offset, editor left + caret left
                screenPosition: { top: 135, left: 55 },
            });
        });

        it.each([
            { description: 'whitespace only', selectedText: '  \n ' },
            { description: 'empty', selectedText: '' },
            { description: 'unavailable because the editor has no model', selectedText: undefined },
        ])('should hide the inline button if the selected text is $description', ({ selectedText }) => {
            mockEditorQueries(selectedText);

            selectionListener(selection(4, 9));

            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith(undefined);
            expect(comp['cachedSelection']).toBeUndefined();
        });

        it('should hide the inline button if the selection is cleared', () => {
            mockEditorQueries('selected');
            selectionListener(selection(4, 9));
            expect(comp['cachedSelection']).toBeDefined();
            selectionEmitSpy.mockClear();

            selectionListener(undefined);

            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith(undefined);
            expect(comp['cachedSelection']).toBeUndefined();
        });

        it('should hide the inline button if the end of the selection is scrolled out of view', () => {
            mockEditorQueries('selected');
            vi.spyOn(comp.monacoEditor()!, 'getScrolledVisiblePosition').mockReturnValue(null);

            selectionListener(selection(4, 9));

            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith(undefined);
        });

        it('should hide the inline button if the editor has no DOM node', () => {
            mockEditorQueries('selected');
            vi.spyOn(comp.monacoEditor()!, 'getDomNode').mockReturnValue(null);

            selectionListener(selection(4, 9));

            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith(undefined);
        });

        it('should not emit anything when recomputing the position without a cached selection', () => {
            comp['emitSelectionWithScreenPosition']();

            expect(selectionEmitSpy).not.toHaveBeenCalled();
        });

        it('should toggle the text style actions with the selection in communication mode', () => {
            fixture.componentRef.setInput('isInCommunication', true);
            mockEditorQueries('selected');
            expect(comp.showTextStyleActions()).toBe(true);

            selectionListener(selection(4, 9));
            expect(comp.showTextStyleActions()).toBe(true);
            expect(comp.showNonTextStyleActions()).toBe(false);

            selectionListener({ startLineNumber: 2, endLineNumber: 2, startColumn: 4, endColumn: 4 });
            // A collapsed selection (just the cursor) switches back to the non text style actions.
            expect(comp.showTextStyleActions()).toBe(false);
            expect(comp.showNonTextStyleActions()).toBe(true);
        });

        it('should not change the visible actions outside of communication mode', () => {
            mockEditorQueries('selected');
            const visibilitySpy = vi.spyOn(comp, 'updateEditorActionsVisibility');

            selectionListener(selection(4, 9));

            expect(visibilitySpy).not.toHaveBeenCalled();
            expect(comp.showTextStyleActions()).toBe(true);
            expect(comp.showNonTextStyleActions()).toBe(true);
        });

        it('should keep the visible actions if they already match an empty selection', () => {
            comp.showTextStyleActions.set(false);
            comp.showNonTextStyleActions.set(true);
            const showTextStyleSetSpy = vi.spyOn(comp.showTextStyleActions, 'set');
            const showNonTextStyleSetSpy = vi.spyOn(comp.showNonTextStyleActions, 'set');

            comp.updateEditorActionsVisibility(undefined);

            expect(showTextStyleSetSpy).not.toHaveBeenCalled();
            expect(showNonTextStyleSetSpy).not.toHaveBeenCalled();
            expect(comp.showTextStyleActions()).toBe(false);
            expect(comp.showNonTextStyleActions()).toBe(true);
        });

        it('should hide the inline button when the editor scrolls', () => {
            mockEditorQueries('selected');
            selectionListener(selection(4, 9));
            selectionEmitSpy.mockClear();

            scrollListener();

            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith(undefined);
            expect(comp['cachedSelection']).toBeUndefined();
        });

        it('should hide the inline button when the page scrolls and stop listening after destroy', () => {
            mockEditorQueries('selected');
            selectionListener(selection(4, 9));
            selectionEmitSpy.mockClear();

            window.dispatchEvent(new Event('scroll'));
            expect(selectionEmitSpy).toHaveBeenCalledOnce();
            expect(selectionEmitSpy).toHaveBeenCalledWith(undefined);

            selectionEmitSpy.mockClear();
            comp.ngOnDestroy();
            window.dispatchEvent(new Event('scroll'));

            expect(selectionEmitSpy).not.toHaveBeenCalled();
            expect(comp['windowScrollHandler']).toBeUndefined();
            expect(selectionDisposable.dispose).toHaveBeenCalledOnce();
            expect(scrollDisposable.dispose).toHaveBeenCalledOnce();
        });
    });

    describe('layout', () => {
        it('should re-layout the editor when an observed element is resized', () => {
            let resizeCallback: ResizeObserverCallback | undefined;
            globalThis.ResizeObserver = class {
                constructor(callback: ResizeObserverCallback) {
                    resizeCallback = callback;
                }
                observe() {}
                unobserve() {}
                disconnect() {}
            } as unknown as typeof ResizeObserver;
            fixture.detectChanges();
            const adjustSpy = vi.spyOn(comp, 'adjustEditorDimensions');

            resizeCallback!([], comp.resizeObserver!);

            expect(adjustSpy).toHaveBeenCalledOnce();
        });

        it('should re-layout the editor after the mode switched to diff', () => {
            fixture.detectChanges();
            const adjustSpy = vi.spyOn(comp, 'adjustEditorDimensions');

            fixture.componentRef.setInput('mode', 'diff');
            // The first pass runs the mode effect, the second one flushes the afterNextRender callback it schedules.
            fixture.detectChanges();
            fixture.detectChanges();

            expect(adjustSpy).toHaveBeenCalledOnce();
        });

        it('should not re-layout the editor on the initial mode', () => {
            const adjustSpy = vi.spyOn(comp, 'adjustEditorDimensions');
            fixture.componentRef.setInput('mode', 'diff');

            fixture.detectChanges();
            fixture.detectChanges();

            // Only the layout of ngAfterViewInit, no additional one from the mode effect.
            expect(adjustSpy).toHaveBeenCalledOnce();
        });
    });

    describe('diff mode', () => {
        it('should forward the line changes of the diff editor', () => {
            fixture.detectChanges();
            const emitSpy = vi.spyOn(comp.diffLineChange, 'emit');
            const change = { ready: true, lineChange: { addedLineCount: 3, removedLineCount: 1 } };

            comp.onDiffChanged(change);

            expect(emitSpy).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith(change);
        });

        it('should track the width of the original pane', () => {
            fixture.detectChanges();

            comp.onDiffOriginalPaneLayoutChanged(321);

            expect(comp['diffOriginalPaneWidth']()).toBe(321);
        });

        it('should apply refined content to the diff editor', () => {
            fixture.detectChanges();
            const applySpy = vi.spyOn(comp.monacoEditor()!, 'applyDiffContent');

            comp.applyDiffContent('direct');
            comp.applyRefinedContent('refined');

            expect(applySpy).toHaveBeenCalledTimes(2);
            expect(applySpy).toHaveBeenNthCalledWith(1, 'direct');
            expect(applySpy).toHaveBeenNthCalledWith(2, 'refined');
        });

        it('should revert all changes in the diff editor', () => {
            fixture.detectChanges();
            const revertSpy = vi.spyOn(comp.monacoEditor()!, 'revertAll');

            comp.revertAll();

            expect(revertSpy).toHaveBeenCalledOnce();
        });

        it('should ignore diff operations while the editor is not available', () => {
            fixture.detectChanges();
            const editor = comp.monacoEditor()!;
            const applySpy = vi.spyOn(editor, 'applyDiffContent');
            const revertSpy = vi.spyOn(editor, 'revertAll');
            (comp as any).monacoEditor = () => undefined;

            expect(() => {
                comp.applyDiffContent('content');
                comp.applyRefinedContent('content');
                comp.revertAll();
            }).not.toThrow();

            expect(applySpy).not.toHaveBeenCalled();
            expect(revertSpy).not.toHaveBeenCalled();
        });
    });

    describe('file upload', () => {
        it('should open the file picker through the hidden file input', () => {
            fixture.detectChanges();
            const clickSpy = vi.spyOn(comp.fileUploadInput()!.nativeElement, 'click').mockImplementation(() => {});

            comp.openFilePicker();

            expect(clickSpy).toHaveBeenCalledOnce();
        });

        it('should not fail to open the file picker without a file input', () => {
            fixture.componentRef.setInput('enableFileUpload', false);
            fixture.detectChanges();

            expect(comp.fileUploadInput()).toBeUndefined();
            expect(() => comp.openFilePicker()).not.toThrow();
        });

        it('should call the file picker when the attachment action asks for it', () => {
            const attachmentAction = new AttachmentAction();
            const openDialogSpy = vi.spyOn(attachmentAction, 'setOpenFileDialogCallback');
            const openFilePickerSpy = vi.spyOn(comp, 'openFilePicker').mockImplementation(() => {});
            fixture.componentRef.setInput('defaultActions', [attachmentAction]);
            fixture.detectChanges();

            expect(openDialogSpy).toHaveBeenCalledOnce();
            openDialogSpy.mock.calls[0][0]!();
            expect(openFilePickerSpy).toHaveBeenCalledOnce();
        });

        it('should reset the file input after the upload failed', async () => {
            vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockRejectedValue(new Error('Upload failed'));
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'addAlert');
            const inputElement = { value: 'C:\\fakepath\\test.png' } as HTMLInputElement;
            fixture.detectChanges();

            comp.embedFiles([new File([''], 'test.png')], inputElement);

            await vi.waitFor(() => expect(inputElement.value).toBe(''));
            expect(alertSpy).toHaveBeenCalledOnce();
            expect(alertSpy).toHaveBeenCalledWith(expect.objectContaining({ message: 'Upload failed', disableTranslation: true }));
        });

        it('should reset the file input after the file was embedded', async () => {
            const attachmentAction = new AttachmentAction();
            const attachmentStub = vi.spyOn(attachmentAction, 'executeInCurrentEditor').mockImplementation(() => {});
            fixture.componentRef.setInput('defaultActions', [new UrlAction(), attachmentAction]);
            vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockResolvedValue({ path: 'https://test.invalid/test.png' });
            const inputElement = { value: 'C:\\fakepath\\test.png' } as HTMLInputElement;
            fixture.detectChanges();

            comp.embedFiles([new File([''], 'test.png')], inputElement);

            await vi.waitFor(() => expect(inputElement.value).toBe(''));
            expect(attachmentStub).toHaveBeenCalledWith({ text: 'test.png', url: 'https://test.invalid/test.png' });
        });

        it('should upload into the current conversation in communication mode and fall back to the given conversation', async () => {
            const communicationService = TestBed.inject(CommunicationService);
            vi.spyOn(communicationService, 'getCourse').mockReturnValue({ id: 5 } as any);
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(undefined as any);
            const conversationUploadSpy = vi.spyOn(fileUploaderService, 'uploadMarkdownFileInCurrentConversation').mockRejectedValue(new Error('Upload failed'));
            const plainUploadSpy = vi.spyOn(fileUploaderService, 'uploadMarkdownFile');
            fixture.componentRef.setInput('useCommunicationForFileUpload', true);
            fixture.componentRef.setInput('fallbackConversationId', 77);
            fixture.detectChanges();
            const file = new File([''], 'test.png');

            comp.embedFiles([file]);

            await vi.waitFor(() => expect(conversationUploadSpy).toHaveBeenCalledOnce());
            expect(conversationUploadSpy).toHaveBeenCalledWith(file, 5, 77);
            expect(plainUploadSpy).not.toHaveBeenCalled();
        });

        it('should reject an upload response that cannot be embedded', () => {
            fixture.componentRef.setInput('defaultActions', [new UrlAction()]);
            fixture.detectChanges();
            const file = new File([''], 'test.png');

            // No attachment action is configured, so there is nothing that could embed the image.
            expect(() => comp['processFileUploadResponse']({ path: 'https://test.invalid/test.png' }, file)).toThrow('Cannot process file upload.');
        });

        it('should reject an upload response without a path', () => {
            fixture.componentRef.setInput('defaultActions', [new UrlAction(), new AttachmentAction()]);
            fixture.detectChanges();
            const file = new File([''], 'test.png');

            expect(() => comp['processFileUploadResponse']({}, file)).toThrow('Cannot process file upload.');
        });
    });

    describe('action buttons', () => {
        it('should hand the click position to the emoji action before executing it', () => {
            const emojiAction = new EmojiAction({} as any, {} as any, {} as any);
            const setPointSpy = vi.spyOn(emojiAction, 'setPoint');
            const executeSpy = vi.spyOn(emojiAction, 'executeInCurrentEditor').mockImplementation(() => {});
            fixture.detectChanges();

            comp.handleActionClick(new MouseEvent('click', { clientX: 12, clientY: 34 }), emojiAction);

            expect(setPointSpy).toHaveBeenCalledOnce();
            expect(setPointSpy).toHaveBeenCalledWith({ x: 12, y: 34 });
            expect(executeSpy).toHaveBeenCalledOnce();
        });

        it('should not insert a color for an unknown hex code', () => {
            fixture.detectChanges();
            const executeSpy = vi.spyOn(comp.colorAction()!, 'executeInCurrentEditor').mockImplementation(() => {});

            comp.onSelectColor('#123456');

            expect(executeSpy).not.toHaveBeenCalled();
        });
    });

    describe('lecture reference menu', () => {
        const slide = (id: number) => ({ id }) as Slide;
        const unitWithLink = (name: string, slides?: Slide[]) => ({ name, attachment: { link: `/api/${name}.pdf` }, slides }) as unknown as AttachmentVideoUnit;
        const unitWithoutLink = { name: 'no-link', attachment: {} } as unknown as AttachmentVideoUnit;

        let lectureAction: LectureAttachmentReferenceAction;
        let executeSpy: ReturnType<typeof vi.spyOn>;
        let menu: TieredMenu;

        beforeEach(() => {
            lectureAction = new LectureAttachmentReferenceAction(
                { getCourse: () => ({ id: 1 }) } as unknown as CommunicationService,
                { findAllByCourseIdWithSlides: () => of(new HttpResponse<any[]>({ body: [] })) } as any,
                {} as any,
            );
            executeSpy = vi.spyOn(lectureAction, 'executeInCurrentEditor').mockImplementation(() => {});
            menu = { toggle: vi.fn() } as unknown as TieredMenu;
            vi.spyOn(TestBed.inject(TranslateService), 'instant').mockImplementation((key) => `translated:${key}`);
            fixture.detectChanges();
        });

        const openMenu = (lectures: LectureWithDetails[]): MenuItem[] => {
            lectureAction.lecturesWithDetails = lectures;
            const event = new MouseEvent('click');
            comp.openLectureMenu(menu, event, lectureAction);
            expect(menu.toggle).toHaveBeenCalledOnce();
            expect(menu.toggle).toHaveBeenCalledWith(event);
            return comp['lectureMenuModel']();
        };

        it('should show a single disabled entry if there are no lectures', () => {
            expect(openMenu([])).toEqual([{ label: 'translated:global.generic.emptyList', disabled: true }]);
        });

        it('should reference a lecture without attachments directly', () => {
            const lecture: LectureWithDetails = { id: 1, title: 'Lecture 1', attachmentVideoUnits: [unitWithoutLink] };

            const items = openMenu([lecture]);

            expect(items).toHaveLength(1);
            expect(items[0].label).toBe('Lecture 1');
            expect(items[0].items).toBeUndefined();
            items[0].command!({} as any);
            expect(executeSpy).toHaveBeenCalledWith({ reference: ReferenceType.LECTURE, lecture });
        });

        it('should offer a submenu with the lecture itself and its attachment units', () => {
            const plainUnit = unitWithLink('plain-unit');
            const lecture: LectureWithDetails = { id: 2, title: 'Lecture 2', attachmentVideoUnits: [unitWithoutLink, plainUnit] };

            const items = openMenu([lecture]);

            expect(items).toHaveLength(1);
            expect(items[0].label).toBe('Lecture 2');
            const submenu = items[0].items!;
            // The unit without an attachment link is not referencable and therefore not listed.
            expect(submenu.map((item) => item.label)).toEqual(['Lecture 2', 'plain-unit']);

            submenu[0].command!({} as any);
            expect(executeSpy).toHaveBeenLastCalledWith({ reference: ReferenceType.LECTURE, lecture });
            submenu[1].command!({} as any);
            expect(executeSpy).toHaveBeenLastCalledWith({ reference: ReferenceType.ATTACHMENT_UNITS, lecture, attachmentVideoUnit: plainUnit });
        });

        it('should list the slides of an attachment unit with their number', () => {
            const slides = [slide(10), slide(11)];
            const unit = unitWithLink('slides-unit', slides);
            const lecture: LectureWithDetails = { id: 3, title: 'Lecture 3', attachmentVideoUnits: [unit] };

            const items = openMenu([lecture]);

            const unitMenu = items[0].items![1];
            expect(unitMenu.label).toBe('slides-unit');
            const unitItems = unitMenu.items!;
            expect(unitItems.map((item) => item.label)).toEqual([
                'slides-unit',
                'translated:artemisApp.markdownEditor.slideWithNumber',
                'translated:artemisApp.markdownEditor.slideWithNumber',
            ]);
            expect(TestBed.inject(TranslateService).instant).toHaveBeenCalledWith('artemisApp.markdownEditor.slideWithNumber', { number: 1 });
            expect(TestBed.inject(TranslateService).instant).toHaveBeenCalledWith('artemisApp.markdownEditor.slideWithNumber', { number: 2 });

            unitItems[0].command!({} as any);
            expect(executeSpy).toHaveBeenLastCalledWith({ reference: ReferenceType.ATTACHMENT_UNITS, lecture, attachmentVideoUnit: unit });
            unitItems[2].command!({} as any);
            expect(executeSpy).toHaveBeenLastCalledWith({ reference: ReferenceType.SLIDE, lecture, slide: slides[1], attachmentVideoUnit: unit, slideIndex: 2 });
        });

        it('should rebuild the menu with the lectures loaded since the last time it was opened', () => {
            expect(openMenu([])).toHaveLength(1);
            expect(comp['lectureMenuModel']()[0].disabled).toBe(true);

            lectureAction.lecturesWithDetails = [
                { id: 1, title: 'First' },
                { id: 2, title: 'Second' },
            ];
            comp.openLectureMenu(menu, new MouseEvent('click'), lectureAction);

            expect(comp['lectureMenuModel']().map((item) => item.label)).toEqual(['First', 'Second']);
        });

        it.each([
            { description: 'no attachment units', lecture: { id: 1, title: 'L' }, expected: false },
            { description: 'only units without an attachment', lecture: { id: 1, title: 'L', attachmentVideoUnits: [{ name: 'u' } as AttachmentVideoUnit] }, expected: false },
            {
                description: 'only attachments without a link',
                lecture: { id: 1, title: 'L', attachmentVideoUnits: [{ name: 'u', attachment: {} } as unknown as AttachmentVideoUnit] },
                expected: false,
            },
            {
                description: 'an attachment with a link',
                lecture: { id: 1, title: 'L', attachmentVideoUnits: [{ name: 'u', attachment: {} } as unknown as AttachmentVideoUnit, unitWithLink('linked')] },
                expected: true,
            },
        ])('should report referencable attachments for a lecture with $description', ({ lecture, expected }) => {
            expect(comp.hasReferencableAttachments(lecture)).toBe(expected);
        });
    });

    describe('review comment manager', () => {
        const enableReviewComments = () => {
            fixture.detectChanges();
            (comp.monacoEditor()! as any).getEditor = vi.fn().mockReturnValue({
                onDidScrollChange: vi.fn().mockReturnValue({ dispose: vi.fn() }),
            });
            fixture.componentRef.setInput('enableExerciseReviewComments', true);
            fixture.changeDetectorRef.detectChanges();
            return (comp as any).getReviewCommentManager().config;
        };

        it('should forward inline fix and navigation requests of the review comment widgets', () => {
            const config = enableReviewComments();
            const inlineFixSpy = vi.spyOn(comp.onApplyInlineFix, 'emit');
            const navigateSpy = vi.spyOn(comp.onNavigateToReviewCommentLocation, 'emit');
            const location = { targetType: CommentThreadLocationType.TEMPLATE_REPO, filePath: 'src/Main.java', lineNumber: 4 } as any;

            config.onApplyInlineFix({ thread: { id: 9 } as CommentThread });
            config.onNavigateToLocation(location);

            expect(inlineFixSpy).toHaveBeenCalledOnce();
            expect(inlineFixSpy).toHaveBeenCalledWith({ threadId: 9 });
            expect(navigateSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledWith(location);
        });

        it('should clear the drafts of an existing review comment manager', () => {
            const clearDrafts = vi.fn();
            (comp as any).reviewCommentManager = { clearDrafts, disposeAll: vi.fn() };

            comp.clearReviewCommentDrafts();

            expect(clearDrafts).toHaveBeenCalledOnce();
        });

        it('should not fail to clear drafts if no review comment manager was created', () => {
            expect(comp['reviewCommentManager']).toBeUndefined();

            expect(() => comp.clearReviewCommentDrafts()).not.toThrow();
        });

        it('should not create a review comment manager while the editor is not available', () => {
            fixture.detectChanges();
            (comp as any).monacoEditor = () => undefined;

            expect(comp['getReviewCommentManager']()).toBeUndefined();
            expect(comp['reviewCommentManager']).toBeUndefined();
        });

        it('should not render review comment widgets while the editor is not available', () => {
            fixture.detectChanges();
            fixture.componentRef.setInput('enableExerciseReviewComments', true);
            (comp as any).monacoEditor = () => undefined;
            const getManagerSpy = vi.spyOn(comp as any, 'getReviewCommentManager');

            comp['renderEditorWidgets']();

            expect(getManagerSpy).not.toHaveBeenCalled();
        });
    });
});
