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

describe('MarkdownEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownEditorMonacoComponent>;
    let component: MarkdownEditorMonacoComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(FileUploaderService), MockProvider(AlertService)],
            imports: [FormsModule, NgbNavModule, ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [
                MarkdownEditorMonacoComponent,
                MockComponent(MonacoEditorComponent),
                MockComponent(ColorSelectorComponent),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTranslatePipe),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(MarkdownEditorMonacoComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
    });
});
