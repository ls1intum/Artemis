import { describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SwitchEditModeButtonComponent } from 'app/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('SwitchEditModeButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SwitchEditModeButtonComponent>;
    let comp: SwitchEditModeButtonComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [SwitchEditModeButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        });

        fixture = TestBed.createComponent(SwitchEditModeButtonComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('isSimpleMode', false);
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should emit to call passed method when button is clicked', () => {
        const switchEditModeSpy = vi.spyOn(comp.switchEditMode, 'emit');

        const button = fixture.debugElement.query(By.css('jhi-button'));
        button.triggerEventHandler('onClick', null);

        expect(switchEditModeSpy).toHaveBeenCalledOnce();
    });
});
