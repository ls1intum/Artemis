import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { AlertService } from 'app/foundation/service/alert.service';
import { ColorSelectorComponent } from 'app/shared-ui/color-selector/color-selector.component';
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
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MetisService } from 'app/communication/service/metis.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';

// Capture the global ResizeObserver provided by the test setup so it can be restored after each test.
const originalResizeObserver = globalThis.ResizeObserver;

describe('MarkdownEditorMonacoComponent', () => {
    setupTestBed({ zoneless: true });

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
                MockProvider(MetisConversationService),
                MockProvider(MetisService),
                MockProvider(ProfileService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            // Swap the heavy editor children for lightweight mocks; the Monaco editor in particular must not load
            // real Monaco. PrimeNG components and other directives/pipes stay real.
            .overrideComponent(MarkdownEditorMonacoComponent, {
                remove: { imports: [MonacoEditorComponent, ColorSelectorComponent, PostingButtonComponent, RedirectToIrisButtonComponent] },
                add: {
                    imports: [
                        MockComponent(MonacoEditorComponent),
                        MockComponent(ColorSelectorComponent),
                        MockComponent(PostingButtonComponent),
                        MockComponent(RedirectToIrisButtonComponent),
                    ],
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

    it('should open the color selector', () => {
        fixture.detectChanges();
        const openColorSelectorSpy = vi.spyOn(comp.colorSelector()!, 'openColorSelector');
        const event = new MouseEvent('click');
        comp.openColorSelector(event);
        expect(openColorSelectorSpy).toHaveBeenCalledOnce();
        expect(openColorSelectorSpy).toHaveBeenCalledWith(event, comp.colorPickerMarginTop, comp.colorPickerHeight);
    });

    it('should pass the correct color as argument to the color action', () => {
        fixture.componentRef.setInput('colorAction', new ColorAction());
        fixture.detectChanges();
        const executeInCurrentEditorStub = vi.spyOn(comp.colorAction()!, 'executeInCurrentEditor').mockImplementation(() => {});
        const markdownColors = comp.colorSignal();
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
});
