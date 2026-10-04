import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { BuildAgentClearDistributedDataComponent } from 'app/localci/build-agent-summary/build-agent-clear-distributed-data/build-agent-clear-distributed-data.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentClearDistributedDataComponent', () => {
    let component: BuildAgentClearDistributedDataComponent;
    let fixture: ComponentFixture<BuildAgentClearDistributedDataComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentClearDistributedDataComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentClearDistributedDataComponent);
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

    it('should have button enabled when confirmation text is correct', () => {
        component.confirmationText.set('clear data');
        expect(component.buttonEnabled()).toBeFalsy();

        component.confirmationText.set('CLEAR DATA');
        expect(component.buttonEnabled()).toBeTruthy();
    });

    it('should emit confirmed and hide on confirm', () => {
        const confirmedSpy = vi.fn();
        component.confirmed.subscribe(confirmedSpy);
        component.confirm();
        expect(confirmedSpy).toHaveBeenCalledOnce();
        expect(component.visible()).toBeFalsy();
    });
});
