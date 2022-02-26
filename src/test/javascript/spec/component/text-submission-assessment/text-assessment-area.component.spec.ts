import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { ArtemisTestModule } from '../../test.module';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextBlock } from 'app/entities/text-block.model';

describe('TextAssessmentAreaComponent', () => {
    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    const blocks = [
        {
            text: 'First text.',
            startIndex: 0,
            endIndex: 11,
        } as TextBlock,
    ];
    const textBlockRefs = [new TextBlockRef(blocks[0])];

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

    it('should toggle on alt', () => {
        const spyOnAlt = jest.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });

        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledTimes(1);
        expect(component.autoTextBlockAssessment).toBe(false);
    });

    it('should add TextBlockRef if text block is added manually', () => {
        component.textBlockRefs = textBlockRefs;
        jest.spyOn(component.textBlockRefsAddedRemoved, 'emit');
        const expectedLength = component.textBlockRefs.length + 1;

        component.addTextBlockRef(TextBlockRef.new());
        fixture.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledTimes(1);
        expect(component.textBlockRefs).toHaveLength(expectedLength);
    });

    it('should remove TextBlockRef if text block is deleted', () => {
        component.textBlockRefs = textBlockRefs;
        jest.spyOn(component.textBlockRefsAddedRemoved, 'emit');
        const expectedLength = component.textBlockRefs.length - 1;

        component.removeTextBlockRef(component.textBlockRefs[0]);
        fixture.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledTimes(1);
        expect(component.textBlockRefs).toHaveLength(expectedLength);
    });
});
