import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildAgentPauseAllModalComponent } from 'app/buildagent/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentPauseAllModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildAgentPauseAllModalComponent;
    let fixture: ComponentFixture<BuildAgentPauseAllModalComponent>;
    const activeModal: NgbActiveModal = {
        dismiss: vi.fn(),
        close: vi.fn(),
        update: vi.fn(),
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
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        component.cancel();
        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close on confirm', () => {
        const closeSpy = vi.spyOn(activeModal, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
