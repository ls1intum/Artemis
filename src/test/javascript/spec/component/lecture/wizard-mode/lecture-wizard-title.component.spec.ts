import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { Lecture } from 'app/entities/lecture.model';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockComponent } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';

describe('LectureWizardTitleComponent', () => {
    let wizardTitleComponentFixture: ComponentFixture<LectureUpdateWizardTitleComponent>;
    let wizardTitleComponent: LectureUpdateWizardTitleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [LectureUpdateWizardTitleComponent, MockComponent(MarkdownEditorComponent), MockComponent(TitleChannelNameComponent)],
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

    it('should update channelName', () => {
        const newChannelName = 'New Channel Name';
        let emittedChannelName: string | undefined;
        wizardTitleComponent.channelNameChange.subscribe((channelName: string) => {
            emittedChannelName = channelName;
        });

        const titleChannelNameElement = wizardTitleComponentFixture.debugElement.query(By.css('jhi-title-channel-name'));
        titleChannelNameElement.triggerEventHandler('channelNameChange', newChannelName);

        expect(wizardTitleComponent.channelName).toBe(newChannelName);
        expect(emittedChannelName).toBe(newChannelName);
    });
});
