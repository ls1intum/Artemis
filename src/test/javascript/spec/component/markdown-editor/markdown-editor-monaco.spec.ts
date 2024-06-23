import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbNavModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MonacoColorAction } from 'app/shared/monaco-editor/model/actions/monaco-color.action';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ArtemisSharedModule } from 'app/shared/shared.module';

describe('MarkdownEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownEditorMonacoComponent>;
    let comp: MarkdownEditorMonacoComponent;

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
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

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
