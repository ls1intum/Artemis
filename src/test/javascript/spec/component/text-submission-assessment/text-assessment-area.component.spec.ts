import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { ArtemisTestModule } from '../../test.module';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';

describe('TextAssessmentAreaComponent', () => {
    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextAssessmentAreaComponent, MockComponent(TextblockAssessmentCardComponent), MockComponent(ManualTextblockSelectionComponent), TranslatePipeMock],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextAssessmentAreaComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
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
