import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildAgentClearDistributedDataComponent } from 'app/localci/build-agent-summary/build-agent-clear-distributed-data/build-agent-clear-distributed-data.component';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentClearDistributedDataComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildAgentClearDistributedDataComponent;
    let fixture: ComponentFixture<BuildAgentClearDistributedDataComponent>;
    const dialogRef = {
        close: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentClearDistributedDataComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentClearDistributedDataComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should close without a result on cancel', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.cancel();
        expect(closeSpy).toHaveBeenCalledWith();
    });

    it('should have button enabled when confirmation text is correct', () => {
        component.confirmationText.set('clear data');
        expect(component.buttonEnabled()).toBeFalsy();

        component.confirmationText.set('CLEAR DATA');
        expect(component.buttonEnabled()).toBeTruthy();
    });

    it('should close with true on confirm', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
