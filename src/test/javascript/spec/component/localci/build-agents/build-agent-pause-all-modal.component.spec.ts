import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildAgentPauseAllModalComponent } from '../../../../../../main/webapp/app/localci/build-agents/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { ArtemisTestModule } from '../../../test.module';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

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
            imports: [ArtemisTestModule, BuildAgentPauseAllModalComponent],
            providers: [{ provide: NgbActiveModal, useValue: activeModal }],
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
