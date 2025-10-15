import { AlertService } from 'app/shared/service/alert.service';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbNavModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ColorAction } from 'app/shared/monaco-editor/model/actions/color.action';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CdkDragMove, DragDropModule } from '@angular/cdk/drag-drop';

import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';
import { COMMUNICATION_MARKDOWN_EDITOR_OPTIONS } from 'app/shared/monaco-editor/monaco-editor-option.helper';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/shared/service/file-uploader.service';

describe('MarkdownEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownEditorMonacoComponent>;
    let comp: MarkdownEditorMonacoComponent;
    let fileUploaderService: FileUploaderService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(FileUploaderService),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            imports: [FormsModule, NgbNavModule, MockDirective(NgbTooltip), DragDropModule],
            declarations: [
                MarkdownEditorMonacoComponent,
                MockComponent(MonacoEditorComponent),
                MockComponent(ColorSelectorComponent),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTranslatePipe),
            ],
        }).compileComponents();
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(MarkdownEditorMonacoComponent);
        comp = fixture.componentInstance;
        comp.initialEditorHeight = 'external';
        comp.domainActions = [new FormulaAction(), new TaskAction(), new TestCaseAction()];
        fileUploaderService = TestBed.inject(FileUploaderService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should limit the vertical drag position based on the input values', () => {
        comp.initialEditorHeight = MarkdownEditorHeight.MEDIUM;
        comp.resizableMinHeight = MarkdownEditorHeight.SMALL;
        comp.resizableMaxHeight = MarkdownEditorHeight.LARGE;
        comp.enableResize = true;
        fixture.detectChanges();
        const wrapperTop = comp.wrapper.nativeElement.getBoundingClientRect().top;
        const minPoint = comp.constrainDragPosition({ x: 0, y: wrapperTop - 10000 });
        expect(minPoint.y).toBe(wrapperTop + comp.resizableMinHeight);
        const maxPoint = comp.constrainDragPosition({ x: 0, y: wrapperTop + 10000 });
        expect(maxPoint.y).toBe(wrapperTop + comp.resizableMaxHeight);
    });

    it('should emit and update on markdown change', () => {
        const text = 'test';
        const textChangeSpy = jest.spyOn(comp.markdownChange, 'emit');
        fixture.detectChanges();
        comp.onTextChanged({ text: text, fileName: 'test-file.md' });
        expect(textChangeSpy).toHaveBeenCalledWith(text);
        expect(comp._markdown).toBe(text);
    });

    it('should notify when switching to preview mode', () => {
        const emitSpy = jest.spyOn(comp.onPreviewSelect, 'emit');
        fixture.detectChanges();
        comp.onNavChanged({ nextId: 'editor_preview', activeId: 'editor', preventDefault: jest.fn() });
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should layout and focus the editor when switching to editor mode', () => {
        fixture.detectChanges();
        const adjustEditorDimensionsSpy = jest.spyOn(comp, 'adjustEditorDimensions');
        const focusSpy = jest.spyOn(comp.monacoEditor, 'focus');
        comp.onNavChanged({
            nextId: MarkdownEditorMonacoComponent.TAB_EDIT,
            activeId: MarkdownEditorMonacoComponent.TAB_PREVIEW,
            preventDefault: jest.fn(),
        });
        expect(adjustEditorDimensionsSpy).toHaveBeenCalledOnce();
        expect(focusSpy).toHaveBeenCalledOnce();
    });

    it('should emit when leaving the visual tab', () => {
        const emitSpy = jest.spyOn(comp.onLeaveVisualTab, 'emit');
        fixture.detectChanges();
        comp.onNavChanged({
            nextId: MarkdownEditorMonacoComponent.TAB_EDIT,
            activeId: MarkdownEditorMonacoComponent.TAB_VISUAL,
            preventDefault: jest.fn(),
        });
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it.each([
        { tab: MarkdownEditorMonacoComponent.TAB_EDIT, flags: [true, false, false] },
        { tab: MarkdownEditorMonacoComponent.TAB_PREVIEW, flags: [false, true, false] },
        { tab: MarkdownEditorMonacoComponent.TAB_VISUAL, flags: [false, false, true] },
    ])(`should set the correct flags when navigating to $tab`, ({ tab, flags }) => {
        fixture.detectChanges();
        comp.onNavChanged({ nextId: tab, activeId: MarkdownEditorMonacoComponent.TAB_EDIT, preventDefault: jest.fn() });
        expect([comp.inEditMode, comp.inPreviewMode, comp.inVisualMode]).toEqual(flags);
    });

    it('should embed manually uploaded files', () => {
        const inputEvent = { target: { files: [new File([''], 'test.png')] } } as unknown as InputEvent;
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        comp.onFileUpload(inputEvent);
        expect(embedFilesStub).toHaveBeenCalledExactlyOnceWith(files, inputEvent.target);
    });

    it('should not embed via manual upload if the event contains no files', () => {
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        comp.onFileUpload({ target: { files: [] } } as any);
        expect(embedFilesStub).not.toHaveBeenCalled();
    });

    it('should embed dropped files', () => {
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        const event = { dataTransfer: { files }, preventDefault: jest.fn() };
        comp.onFileDrop(event as any);
        expect(embedFilesStub).toHaveBeenCalledExactlyOnceWith(files);
    });

    it('should not try to embed via drop if the event contains no files', () => {
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        comp.onFileDrop({ dataTransfer: { files: [] }, preventDefault: jest.fn() } as any);
        expect(embedFilesStub).not.toHaveBeenCalled();
    });

    it('should notify if the upload of a markdown file failed', fakeAsync(() => {
        const alertService = TestBed.inject(AlertService);
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        const files = [new File([''], 'test.png')];
        const uploadMarkdownFileStub = jest.spyOn(fileUploaderService, 'uploadMarkdownFile').mockRejectedValue(new Error('Test error'));
        fixture.detectChanges();
        comp.embedFiles(files);
        flush();
        expect(uploadMarkdownFileStub).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledOnce();
    }));

    it('should set the upload callback on the attachment actions', () => {
        const attachmentAction = new AttachmentAction();
        const setUploadCallbackSpy = jest.spyOn(attachmentAction, 'setUploadCallback');
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        comp.defaultActions = [attachmentAction];
        comp.enableFileUpload = true;
        fixture.detectChanges();
        expect(setUploadCallbackSpy).toHaveBeenCalledOnce();
        // Check if the correct function is passed to the action.
        const argument = setUploadCallbackSpy.mock.calls[0][0];
        expect(argument).toBeDefined();
        argument!([]);
        expect(embedFilesStub).toHaveBeenCalledExactlyOnceWith([]);
    });

    it('should embed image and .pdf files', fakeAsync(() => {
        const urlAction = new UrlAction();
        const urlStub = jest.spyOn(urlAction, 'executeInCurrentEditor').mockImplementation();
        const attachmentAction = new AttachmentAction();
        const attachmentStub = jest.spyOn(attachmentAction, 'executeInCurrentEditor').mockImplementation();
        const fileInformation = [
            { file: new File([''], 'test.png'), url: 'https://test.invalid/generated42.png' },
            { file: new File([''], 'test.pdf'), url: 'https://test.invalid/generated1234.pdf' },
        ];
        comp.defaultActions = [urlAction, attachmentAction];
        const files = [new File([''], 'test.png'), new File([''], 'test.pdf')];
        const uploadMarkdownFileStub = jest.spyOn(fileUploaderService, 'uploadMarkdownFile').mockImplementation((file: File) => {
            const path = file.name.endsWith('.png') ? fileInformation[0].url : fileInformation[1].url;
            return Promise.resolve({ path });
        });
        fixture.detectChanges();
        comp.embedFiles(files);
        flush();
        // Each file should be uploaded.
        expect(uploadMarkdownFileStub).toHaveBeenCalledTimes(2);
        expect(uploadMarkdownFileStub).toHaveBeenNthCalledWith(1, files[0]);
        expect(uploadMarkdownFileStub).toHaveBeenNthCalledWith(2, files[1]);
        // Each file should be embedded. PDFs should be embedded as URLs.
        expect(attachmentStub).toHaveBeenCalledExactlyOnceWith({
            url: fileInformation[0].url,
            text: fileInformation[0].file.name,
        });
        expect(urlStub).toHaveBeenCalledExactlyOnceWith({
            url: fileInformation[1].url,
            text: fileInformation[1].file.name,
        });
    }));

    it('should not embed files if file upload is disabled', () => {
        const urlAction = new UrlAction();
        const urlStub = jest.spyOn(urlAction, 'executeInCurrentEditor').mockImplementation();
        const attachmentAction = new AttachmentAction();
        const attachmentStub = jest.spyOn(attachmentAction, 'executeInCurrentEditor').mockImplementation();
        const files = [new File([''], 'test.png'), new File([''], 'test.pdf')];
        comp.defaultActions = [urlAction, attachmentAction];
        comp.enableFileUpload = false;
        fixture.detectChanges();
        comp.embedFiles(files);
        expect(urlStub).not.toHaveBeenCalled();
        expect(attachmentStub).not.toHaveBeenCalled();
    });

    it('should execute the action when clicked', () => {
        const action = new UrlAction();
        const executeInCurrentEditorStub = jest.spyOn(action, 'executeInCurrentEditor').mockImplementation();
        comp.defaultActions = [action];
        fixture.detectChanges();
        comp.handleActionClick(new MouseEvent('click'), action);
        expect(executeInCurrentEditorStub).toHaveBeenCalledOnce();
    });

    it('should open the color selector', () => {
        fixture.detectChanges();
        const openColorSelectorSpy = jest.spyOn(comp.colorSelector, 'openColorSelector');
        const event = new MouseEvent('click');
        comp.openColorSelector(event);
        expect(openColorSelectorSpy).toHaveBeenCalledExactlyOnceWith(event, comp.colorPickerMarginTop, comp.colorPickerHeight);
    });

    it('should pass the correct color as argument to the color action', () => {
        comp.colorAction = new ColorAction();
        fixture.detectChanges();
        const executeInCurrentEditorStub = jest.spyOn(comp.colorAction, 'executeInCurrentEditor').mockImplementation();
        const markdownColors = comp.colorSignal();
        for (let i = 0; i < markdownColors.length; i++) {
            const color = markdownColors[i];
            comp.onSelectColor(color);
            expect(executeInCurrentEditorStub).toHaveBeenNthCalledWith(i + 1, { color: comp.colorToClassMap.get(color) });
        }
    });

    it('should pass the entire element to the fullscreen action for external height', () => {
        comp.initialEditorHeight = 'external';
        const fullscreenAction = new FullscreenAction();
        comp.metaActions = [fullscreenAction];
        fixture.detectChanges();
        expect(fullscreenAction.element).toBe(comp.fullElement.nativeElement);
    });

    it('should pass the wrapper element to the fullscreen action for a set initial height', () => {
        comp.initialEditorHeight = MarkdownEditorHeight.MEDIUM;
        const fullscreenAction = new FullscreenAction();
        comp.metaActions = [fullscreenAction];
        fixture.detectChanges();
        expect(fullscreenAction.element).toBe(comp.wrapper.nativeElement);
    });

    it('should compute height 0 for a missing element', () => {
        fixture.detectChanges();
        expect(comp.getElementClientHeight(undefined)).toBe(0);
    });

    it('should not react to content height changes if the height is not liked to the editor size', () => {
        comp.linkEditorHeightToContentHeight = false;
        comp.initialEditorHeight = MarkdownEditorHeight.MEDIUM;
        fixture.detectChanges();
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.MEDIUM);
        comp.onContentHeightChanged(100);
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.MEDIUM);
    });

    it('should not react to content height changes if file upload is enabled but the footer has not loaded', () => {
        comp.initialEditorHeight = MarkdownEditorHeight.SMALL;
        jest.spyOn(comp, 'getElementClientHeight').mockReturnValue(0);
        comp.enableFileUpload = true;
        comp.linkEditorHeightToContentHeight = true;
        comp.resizableMinHeight = MarkdownEditorHeight.INLINE;
        comp.resizableMaxHeight = MarkdownEditorHeight.LARGE;
        fixture.detectChanges();
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.SMALL);
        comp.onContentHeightChanged(9999);
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.SMALL);
    });

    it('should react to content height changes if the height is linked to the editor', () => {
        jest.spyOn(comp, 'getElementClientHeight').mockReturnValue(20);
        comp.linkEditorHeightToContentHeight = true;
        comp.resizableMaxHeight = MarkdownEditorHeight.LARGE;
        fixture.detectChanges();
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.SMALL);
        comp.onContentHeightChanged(1500);
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.LARGE);
        comp.onContentHeightChanged(20);
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.SMALL);
    });

    it('should adjust the wrapper height when resized manually', () => {
        const cdkDragMove = { source: { reset: jest.fn() }, pointerPosition: { y: 300 } } as unknown as CdkDragMove;
        const wrapperTop = 100;
        const dragElemHeight = 20;
        fixture.detectChanges();
        jest.spyOn(comp, 'getElementClientHeight').mockReturnValue(dragElemHeight);
        jest.spyOn(comp.wrapper.nativeElement, 'getBoundingClientRect').mockReturnValue({ top: wrapperTop } as DOMRect);
        comp.onResizeMoved(cdkDragMove);
        expect(comp.targetWrapperHeight).toBe(300 - wrapperTop - dragElemHeight / 2);
    });

    it('should use the correct options to enable text field mode', () => {
        fixture.detectChanges();
        const applySpy = jest.spyOn(comp.monacoEditor, 'applyOptionPreset');
        comp.enableTextFieldMode();
        expect(applySpy).toHaveBeenCalledExactlyOnceWith(COMMUNICATION_MARKDOWN_EDITOR_OPTIONS);
    });

    it('should apply option presets to the editor', () => {
        fixture.detectChanges();
        const applySpy = jest.spyOn(comp.monacoEditor, 'applyOptionPreset');
        const preset = new MonacoEditorOptionPreset({ lineNumbers: 'off' });
        comp.applyOptionPreset(preset);
        expect(applySpy).toHaveBeenCalledExactlyOnceWith(preset);
    });

    it('should render markdown callouts correctly', () => {
        comp._markdown = `
> [!NOTE]
> Highlights information that users should take into account, even when skimming.

> [!TIP]
> Optional information to help a user be more successful.

> [!IMPORTANT]
> Crucial information necessary for users to succeed.

> [!WARNING]
> Critical content demanding immediate user attention due to potential risks.

> [!CAUTION]
> Negative potential consequences of an action.`;

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
        const html = comp.defaultPreviewHtml as { changingThisBreaksApplicationSecurity: string };
        const renderedHtml = html.changingThisBreaksApplicationSecurity;
        expect(renderedHtml).toEqual(expectedHtml);
    });
    it('should handle invalid callout type gracefully', () => {
        comp._markdown = `
> [!INVALID]
> This is an invalid callout type.`;
        comp.parseMarkdown();
        // The markdown editor generates SafeHtml to prevent certain client-side attacks, but for this test, we only need the raw HTML.
        const html = comp.defaultPreviewHtml as { changingThisBreaksApplicationSecurity: string };
        const renderedHtml = html.changingThisBreaksApplicationSecurity;
        expect(renderedHtml).toContain('<blockquote>');
    });

    it('should render nested content within callouts', () => {
        comp._markdown = `
> [!NOTE]
> # Heading
> - List item 1
> - List item 2
>
> Nested blockquote:
> > This is nested.`;

        comp.parseMarkdown();

        const html = comp.defaultPreviewHtml as { changingThisBreaksApplicationSecurity: string };
        // The markdown editor generates SafeHtml to prevent certain client-side attacks, but for this test, we only need the raw HTML.
        const renderedHtml = html.changingThisBreaksApplicationSecurity;
        expect(renderedHtml).toContain('<h1>Heading</h1>');
        expect(renderedHtml).toContain('<ul>');
        expect(renderedHtml).toContain('<blockquote>');
    });
});
