import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildAgentClearDistributedDataComponent } from '../../../../../../main/webapp/app/localci/build-agents/build-agent-summary/build-agent-clear-distributed-data/build-agent-clear-distributed-data.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentClearDistributedDataComponent', () => {
    let component: BuildAgentClearDistributedDataComponent;
    let fixture: ComponentFixture<BuildAgentClearDistributedDataComponent>;
    const activeModal: NgbActiveModal = {
        dismiss: jest.fn(),
        close: jest.fn(),
        update: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentClearDistributedDataComponent],
            providers: [
                { provide: NgbActiveModal, useValue: activeModal },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentClearDistributedDataComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should dismiss on cancel', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        component.cancel();
        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('should have button enabled when confirmation text is correct', () => {
        component.confirmationText.set('clear data');
        expect(component.buttonEnabled()).toBeFalsy();

        component.confirmationText.set('CLEAR DATA');
        expect(component.buttonEnabled()).toBeTruthy();
    });

    it('should close on confirm', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
