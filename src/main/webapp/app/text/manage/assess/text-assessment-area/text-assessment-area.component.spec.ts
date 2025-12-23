import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextAssessmentAreaComponent } from 'app/text/manage/assess/text-assessment-area/text-assessment-area.component';
import { TextBlockAssessmentCardComponent } from 'app/text/manage/assess/textblock-assessment-card/text-block-assessment-card.component';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective } from 'ng-mocks';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ManualTextblockSelectionComponent } from 'app/text/manage/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('TextAssessmentAreaComponent', () => {
    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TextAssessmentAreaComponent,
                MockComponent(TextBlockAssessmentCardComponent),
                MockComponent(ManualTextblockSelectionComponent),
                TranslatePipeMock,
                MockDirective(TranslateDirective),
            ],
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
            fixture.changeDetectorRef.detectChanges();

            const all = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent));
            expect(all).toHaveLength(i);
        }
    });

    it('should toggle on alt', () => {
        const spyOnAlt = jest.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });

        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledOnce();
        expect(component.autoTextBlockAssessment).toBeFalse();
    });

    it('should not toggle on alt when manual selection forbidden', () => {
        const spyOnAlt = jest.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });
        component.allowManualBlockSelection = false;
        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledOnce();
        expect(component.autoTextBlockAssessment).toBeTrue();
    });

    it('should add TextBlockRef if text block is added manually', () => {
        component.textBlockRefs = [TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new()];
        jest.spyOn(component.textBlockRefsAddedRemoved, 'emit');

        component.addTextBlockRef(TextBlockRef.new());
        fixture.changeDetectorRef.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledOnce();
        expect(component.textBlockRefs).toHaveLength(5);
    });

    it('should remove TextBlockRef if text block is deleted', () => {
        component.textBlockRefs = [TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new()];
        jest.spyOn(component.textBlockRefsAddedRemoved, 'emit');

        component.removeTextBlockRef(component.textBlockRefs[0]);
        fixture.changeDetectorRef.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledOnce();
        expect(component.textBlockRefs).toHaveLength(3);
    });
});
