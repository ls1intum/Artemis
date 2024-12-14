import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { Lecture } from 'app/entities/lecture.model';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ArtemisTestModule } from '../../../test.module';
import { LectureUpdatePeriodComponent } from '../../../../../../main/webapp/app/lecture/lecture-period/lecture-period.component';
import { FormDateTimePickerComponent } from '../../../../../../main/webapp/app/shared/date-time-picker/date-time-picker.component';
import { LectureUpdateWizardStepComponent } from '../../../../../../main/webapp/app/lecture/wizard-mode/lecture-update-wizard-step.component';
import { LectureUpdateWizardUnitsComponent } from '../../../../../../main/webapp/app/lecture/wizard-mode/lecture-wizard-units.component';
import { LectureUpdateWizardAttachmentsComponent } from '../../../../../../main/webapp/app/lecture/wizard-mode/lecture-wizard-attachments.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MarkdownEditorMonacoComponent } from '../../../../../../main/webapp/app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { LectureTitleChannelNameComponent } from '../../../../../../main/webapp/app/lecture/lecture-title-channel-name.component';
import { CustomNotIncludedInValidatorDirective } from '../../../../../../main/webapp/app/shared/validators/custom-not-included-in-validator.directive';
import { TitleChannelNameComponent } from '../../../../../../main/webapp/app/shared/form/title-channel-name/title-channel-name.component';

describe('LectureWizardTitleComponent', () => {
    let wizardTitleComponentFixture: ComponentFixture<LectureUpdateWizardTitleComponent>;
    let wizardTitleComponent: LectureUpdateWizardTitleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockModule(ArtemisSharedModule)],
            declarations: [
                LectureUpdateWizardTitleComponent,
                LectureUpdatePeriodComponent,
                LectureTitleChannelNameComponent,
                TitleChannelNameComponent,
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(LectureUpdateWizardStepComponent),
                MockComponent(LectureUpdateWizardUnitsComponent),
                MockComponent(LectureUpdateWizardAttachmentsComponent),
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
