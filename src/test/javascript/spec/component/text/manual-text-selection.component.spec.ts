import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ManualTextSelectionComponent } from 'app/exercises/text/shared/manual-text-selection/manual-text-selection.component';
import { SelectionRectangle, TextSelectEvent } from 'app/exercises/text/shared/text-select.directive';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';

describe('ManualTextSelectionComponent', () => {
    let component: ManualTextSelectionComponent;
    let fixture: ComponentFixture<ManualTextSelectionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ManualTextSelectionComponent],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ManualTextSelectionComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should select text and assess the text', () => {
        const rectangle = { left: 0, top: 0, width: 0, height: 0 } as SelectionRectangle;
        const event = { text: 'This is a text\n another line', viewportRectangle: null, hostRectangle: null } as TextSelectEvent;

        component.disabled = true;

        // catch edge case
        component.didSelectSolutionText(event);

        component.disabled = false;

        component.didSelectSolutionText(event);

        expect(component.hostRectangle).toBe(undefined);
        expect(component.selectedText).toBe(undefined);

        event.hostRectangle = rectangle;

        component.didSelectSolutionText(event);

        expect(component.hostRectangle).toEqual(rectangle);
        expect(component.selectedText).toBe('This is a text<br> another line');

        const emitSpy = jest.spyOn(component.assess, 'emit');
        component.assessAction();

        expect(emitSpy).toHaveBeenCalledTimes(1);
        expect(component.selectedText).toBe(undefined);
        expect(component.hostRectangle).toBe(undefined);
    });

    it('should send assessment event when selecting text block manually', () => {
        component.selectedText = 'sample text';
        const sendAssessmentEventSpy = jest.spyOn(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.assessAction();
        fixture.detectChanges();
        expect(sendAssessmentEventSpy).toHaveBeenCalledTimes(1);
        expect(sendAssessmentEventSpy).toHaveBeenCalledWith(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });
});
