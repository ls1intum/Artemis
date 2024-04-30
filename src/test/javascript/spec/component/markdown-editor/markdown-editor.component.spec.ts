import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService } from 'app/core/util/alert.service';
import { NgbNav } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';
import { NegatedTypeCheckPipe } from 'app/shared/pipes/negated-type-check.pipe';
import { TypeCheckPipe } from 'app/shared/pipes/type-check.pipe';

describe('MarkdownEditorComponent', () => {
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let component: MarkdownEditorComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(FileUploaderService), MockProvider(AlertService)],
            imports: [FormsModule, NgbNav, ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [
                MarkdownEditorComponent,
                MockComponent(AceEditorComponent),
                MockComponent(ColorSelectorComponent),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(NegatedTypeCheckPipe),
                MockPipe(TypeCheckPipe),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(MarkdownEditorComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should embed files when at least one file is uploaded', () => {
        // Arrange
        const files = [new File([], 'file1.png')];
        const event = {
            target: {
                files: files,
            },
        };

        const embedFilesSpy = jest.spyOn(component, 'embedFiles').mockImplementation();

        // Act
        component.onFileUpload(event as any);

        // Assert
        expect(embedFilesSpy).toHaveBeenCalledWith(files);
    });

    it('should not embed files when no file is uploaded', () => {
        // Arrange
        const event = {
            target: {
                files: [],
            },
        };

        const embedFilesSpy = jest.spyOn(component, 'embedFiles');

        // Act
        component.onFileUpload(event as any);

        // Assert
        expect(embedFilesSpy).not.toHaveBeenCalled();
    });
});
