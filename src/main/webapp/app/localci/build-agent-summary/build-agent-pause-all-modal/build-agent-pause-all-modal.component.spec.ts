import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildAgentPauseAllModalComponent } from 'app/localci/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BuildAgentPauseAllModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildAgentPauseAllModalComponent;
    let fixture: ComponentFixture<BuildAgentPauseAllModalComponent>;
    const dialogRef = {
        close: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentPauseAllModalComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentPauseAllModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should close without a result on cancel', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.cancel();
        expect(closeSpy).toHaveBeenCalledWith();
    });

    it('should close with true on confirm', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
