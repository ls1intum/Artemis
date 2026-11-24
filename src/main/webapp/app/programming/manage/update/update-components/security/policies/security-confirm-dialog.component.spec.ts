import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityConfirmDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-confirm-dialog.component';

describe('SecurityConfirmDialogComponent', () => {
    it('names what is affected and closes when confirmed', () => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        const fixture = TestBed.createComponent(SecurityConfirmDialogComponent);
        const comp = fixture.componentInstance;
        const confirmed = vi.fn();
        comp.confirmed.subscribe(confirmed);
        fixture.componentRef.setInput('header', { key: 'header' });
        fixture.componentRef.setInput('question', { key: 'question' });
        fixture.componentRef.setInput('details', [{ labelKey: 'path', value: 'data/input.txt' }]);
        fixture.componentRef.setInput('warning', { key: 'warning' });
        fixture.componentRef.setInput('confirmLabel', 'delete');
        comp.visible.set(true);
        fixture.detectChanges();

        (comp as unknown as { confirm(): void }).confirm();

        expect(confirmed).toHaveBeenCalledOnce();
        expect(comp.visible()).toBe(false);
    });
});
