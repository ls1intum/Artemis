import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess-new/text-assessment-area/text-assessment-area.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess-new/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TextAssessmentAreaComponent', () => {
    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisConfirmIconModule],
            declarations: [TextAssessmentAreaComponent, TextblockAssessmentCardComponent, TextblockFeedbackEditorComponent],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextAssessmentAreaComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add a TextblockAssessmentCardComponent for each TextBlockRef', () => {
        const textBlockRefs = [
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
        ];

        for (let i = 0; i < textBlockRefs.length; i++) {
            component.textBlockRefs = textBlockRefs.slice(0, i);
            fixture.detectChanges();

            const all = fixture.debugElement.queryAll(By.directive(TextblockAssessmentCardComponent));
            expect(all.length).toBe(i);
        }
    });
});
