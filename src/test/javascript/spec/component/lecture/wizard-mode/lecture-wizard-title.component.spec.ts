import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { Lecture } from 'app/entities/lecture.model';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockComponent } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';

describe('LectureWizardTitleComponent', () => {
    let wizardTitleComponentFixture: ComponentFixture<LectureUpdateWizardTitleComponent>;
    let wizardTitleComponent: LectureUpdateWizardTitleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [LectureUpdateWizardTitleComponent, MockComponent(MarkdownEditorComponent), MockComponent(LectureTitleChannelNameComponent)],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardTitleComponentFixture = TestBed.createComponent(LectureUpdateWizardTitleComponent);
                wizardTitleComponent = wizardTitleComponentFixture.componentInstance;
                wizardTitleComponent.lecture = new Lecture();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardTitleComponentFixture.detectChanges();
        expect(wizardTitleComponent).not.toBeNull();
    });
});
