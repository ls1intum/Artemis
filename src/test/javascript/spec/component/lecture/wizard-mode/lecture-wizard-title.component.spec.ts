import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { Lecture } from 'app/entities/lecture.model';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { MarkdownEditorMonacoComponent } from '../../../../../../main/webapp/app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { LectureTitleChannelNameComponent } from '../../../../../../main/webapp/app/lecture/lecture-title-channel-name.component';
import { CustomNotIncludedInValidatorDirective } from '../../../../../../main/webapp/app/shared/validators/custom-not-included-in-validator.directive';
import { TitleChannelNameComponent } from '../../../../../../main/webapp/app/shared/form/title-channel-name/title-channel-name.component';

describe('LectureWizardTitleComponent', () => {
    let wizardTitleComponentFixture: ComponentFixture<LectureUpdateWizardTitleComponent>;
    let wizardTitleComponent: LectureUpdateWizardTitleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule)],
            declarations: [
                LectureUpdateWizardTitleComponent,
                LectureTitleChannelNameComponent,
                TitleChannelNameComponent,
                MockComponent(MarkdownEditorMonacoComponent),
                MockDirective(CustomNotIncludedInValidatorDirective),
            ],
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
