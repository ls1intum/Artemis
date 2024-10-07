import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbNavModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ColorAction } from 'app/shared/monaco-editor/model/actions/color.action';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { CdkDragMove, DragDropModule } from '@angular/cdk/drag-drop';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';
import { COMMUNICATION_MARKDOWN_EDITOR_OPTIONS } from 'app/shared/monaco-editor/monaco-editor-option.helper';

describe('MarkdownEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownEditorMonacoComponent>;
    let comp: MarkdownEditorMonacoComponent;
    let fileUploaderService: FileUploaderService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(FileUploaderService), MockProvider(AlertService)],
            imports: [FormsModule, NgbNavModule, ArtemisTestModule, ArtemisSharedModule, MockDirective(NgbTooltip), DragDropModule],
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
        fileUploaderService = fixture.debugElement.injector.get(FileUploaderService);
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
        comp.onTextChanged(text);
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
        comp.onNavChanged({ nextId: MarkdownEditorMonacoComponent.TAB_EDIT, activeId: MarkdownEditorMonacoComponent.TAB_PREVIEW, preventDefault: jest.fn() });
        expect(adjustEditorDimensionsSpy).toHaveBeenCalledOnce();
        expect(focusSpy).toHaveBeenCalledOnce();
    });

    it('should emit when leaving the visual tab', () => {
        const emitSpy = jest.spyOn(comp.onLeaveVisualTab, 'emit');
        fixture.detectChanges();
        comp.onNavChanged({ nextId: MarkdownEditorMonacoComponent.TAB_EDIT, activeId: MarkdownEditorMonacoComponent.TAB_VISUAL, preventDefault: jest.fn() });
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
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        comp.onFileUpload({ target: { files } });
        expect(embedFilesStub).toHaveBeenCalledExactlyOnceWith(files);
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
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        const files = [new File([''], 'test.png')];
        const uploadMarkdownFileStub = jest.spyOn(fileUploaderService, 'uploadMarkdownFile').mockRejectedValue(new Error('Test error'));
        fixture.detectChanges();
        comp.embedFiles(files);
        flush();
        expect(uploadMarkdownFileStub).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledOnce();
    }));

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
        expect(attachmentStub).toHaveBeenCalledExactlyOnceWith({ url: fileInformation[0].url, text: fileInformation[0].file.name });
        expect(urlStub).toHaveBeenCalledExactlyOnceWith({ url: fileInformation[1].url, text: fileInformation[1].file.name });
    }));

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

    it('should react to content height changes if the height is linked to the editor', () => {
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
});
