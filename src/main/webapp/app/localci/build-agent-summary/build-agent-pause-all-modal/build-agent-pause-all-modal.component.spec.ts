import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { BuildAgentPauseAllModalComponent } from 'app/localci/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentPauseAllModalComponent', () => {
    let component: BuildAgentPauseAllModalComponent;
    let fixture: ComponentFixture<BuildAgentPauseAllModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentPauseAllModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentPauseAllModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    it('should hide without confirming on cancel', () => {
        const confirmedSpy = vi.fn();
        component.confirmed.subscribe(confirmedSpy);
        component.cancel();
        expect(component.visible()).toBeFalsy();
        expect(confirmedSpy).not.toHaveBeenCalled();
    });

    it('should emit confirmed and hide on confirm', () => {
        const confirmedSpy = vi.fn();
        component.confirmed.subscribe(confirmedSpy);
        component.confirm();
        expect(confirmedSpy).toHaveBeenCalledOnce();
        expect(component.visible()).toBeFalsy();
    });
});
