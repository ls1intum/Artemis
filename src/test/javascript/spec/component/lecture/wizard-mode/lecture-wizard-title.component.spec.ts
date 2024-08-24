import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUpdateTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { Lecture } from 'app/entities/lecture.model';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockComponent } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';

describe('LectureWizardTitleComponent', () => {
    let wizardTitleComponentFixture: ComponentFixture<LectureUpdateTitleComponent>;
    let wizardTitleComponent: LectureUpdateTitleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [LectureUpdateTitleComponent, MockComponent(MarkdownEditorComponent), MockComponent(LectureTitleChannelNameComponent)],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardTitleComponentFixture = TestBed.createComponent(LectureUpdateTitleComponent);
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
