import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildAgentClearDistributedDataComponent } from 'app/buildagent/build-agent-summary/build-agent-clear-distributed-data/build-agent-clear-distributed-data.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentClearDistributedDataComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildAgentClearDistributedDataComponent;
    let fixture: ComponentFixture<BuildAgentClearDistributedDataComponent>;
    const activeModal: NgbActiveModal = {
        dismiss: vi.fn(),
        close: vi.fn(),
        update: vi.fn(),
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
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
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
        const closeSpy = vi.spyOn(activeModal, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
