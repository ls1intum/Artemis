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

    it('should embed manually uploaded files', () => {
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        comp.onFileUpload({ target: { files } });
        expect(embedFilesStub).toHaveBeenCalledExactlyOnceWith(files);
    });

    it('should embed dropped files', () => {
        const embedFilesStub = jest.spyOn(comp, 'embedFiles').mockImplementation();
        fixture.detectChanges();
        const files = [new File([''], 'test.png')];
        const event = { dataTransfer: { files }, preventDefault: jest.fn() };
        comp.onFileDrop(event as any);
        expect(embedFilesStub).toHaveBeenCalledExactlyOnceWith(files);
    });

    it('should throw on upload if the actions are not available', () => {
        comp.defaultActions = [];
        const files = [new File([''], 'test.png')];
        fixture.detectChanges();
        expect(() => comp.embedFiles(files)).toThrow(Error);
    });

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

    it('should pass the correct color as argument to the color action', () => {
        comp.colorAction = new MonacoColorAction();
        fixture.detectChanges();
        const executeInCurrentEditorStub = jest.spyOn(comp.colorAction, 'executeInCurrentEditor').mockImplementation();
        for (let i = 0; i < comp.markdownColors.length; i++) {
            comp.onSelectColor(comp.markdownColors[i]);
            expect(executeInCurrentEditorStub).toHaveBeenNthCalledWith(i + 1, { color: comp.markdownColorNames[i] });
        }
    });
});
