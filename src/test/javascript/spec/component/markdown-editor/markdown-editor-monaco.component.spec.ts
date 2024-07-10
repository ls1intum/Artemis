import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbNavModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MonacoColorAction } from 'app/shared/monaco-editor/model/actions/monaco-color.action';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MonacoUrlAction } from 'app/shared/monaco-editor/model/actions/monaco-url.action';
import { MonacoAttachmentAction } from 'app/shared/monaco-editor/model/actions/monaco-attachment.action';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';
import { MonacoTestCaseAction } from 'app/shared/monaco-editor/model/actions/monaco-test-case.action';
import { MonacoTaskAction } from 'app/shared/monaco-editor/model/actions/monaco-task.action';

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
        comp.domainActions = [new MonacoFormulaAction(), new MonacoTaskAction(), new MonacoTestCaseAction()];
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
        comp.onNavChanged({ nextId: 'editor', activeId: 'editor_preview', preventDefault: jest.fn() });
        expect(adjustEditorDimensionsSpy).toHaveBeenCalledOnce();
        expect(focusSpy).toHaveBeenCalledOnce();
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
        const urlAction = new MonacoUrlAction();
        const urlStub = jest.spyOn(urlAction, 'executeInCurrentEditor').mockImplementation();
        const attachmentAction = new MonacoAttachmentAction();
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
        comp.colorAction = new MonacoColorAction();
        fixture.detectChanges();
        const executeInCurrentEditorStub = jest.spyOn(comp.colorAction, 'executeInCurrentEditor').mockImplementation();
        const markdownColors = comp.colorSignal();
        for (let i = 0; i < markdownColors.length; i++) {
            const color = markdownColors[i];
            comp.onSelectColor(color);
            expect(executeInCurrentEditorStub).toHaveBeenNthCalledWith(i + 1, { color: comp.colorToClassMap.get(color) });
        }
    });
});
