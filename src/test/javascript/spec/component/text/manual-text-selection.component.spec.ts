import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { ManualTextSelectionComponent } from 'app/exercises/text/shared/manual-text-selection/manual-text-selection.component';
import { SelectionRectangle, TextSelectEvent } from 'app/exercises/text/shared/text-select.directive';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ManualTextSelectionComponent', () => {
    let component: ManualTextSelectionComponent;
    let fixture: ComponentFixture<ManualTextSelectionComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisConfirmIconModule],
            declarations: [ManualTextSelectionComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
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
        fixture = TestBed.createComponent(ManualTextSelectionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should select text and assess the text', () => {
        const rectangle = { left: 0, top: 0, width: 0, height: 0 } as SelectionRectangle;
        const event = { text: 'This is a text\n another line', viewportRectangle: null, hostRectangle: null } as TextSelectEvent;

        component.disabled = true;

        // catch edge case
        component.didSelectSolutionText(event);

        component.disabled = false;

        component.didSelectSolutionText(event);

        expect(component.hostRectangle).to.be.undefined;
        expect(component.selectedText).to.be.undefined;

        event.hostRectangle = rectangle;

        component.didSelectSolutionText(event);

        expect(component.hostRectangle).to.deep.equal(rectangle);
        expect(component.selectedText).to.equal('This is a text<br> another line');

        const spy = sinon.spy(component.assess, 'emit');
        component.assessAction();

        expect(spy).to.have.been.calledOnce;
        expect(component.selectedText).to.be.undefined;
        expect(component.hostRectangle).to.be.undefined;
    });

    it('should send assessment event when selecting text block manually', () => {
        component.selectedText = 'sample text';
        const sendAssessmentEvent = sinon.spy(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.assessAction();
        fixture.detectChanges();
        expect(sendAssessmentEvent).to.have.been.calledWith(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });
});
