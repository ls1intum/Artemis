import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildAgentPauseAllModalComponent } from 'app/buildagent/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentPauseAllModalComponent', () => {
    let component: BuildAgentPauseAllModalComponent;
    let fixture: ComponentFixture<BuildAgentPauseAllModalComponent>;
    const activeModal: NgbActiveModal = {
        dismiss: jest.fn(),
        close: jest.fn(),
        update: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentPauseAllModalComponent],
            providers: [
                { provide: NgbActiveModal, useValue: activeModal },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentPauseAllModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should dismiss on cancel', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        component.cancel();
        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close on confirm', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
