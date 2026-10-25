import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SavedPostingBannerComponent } from './saved-posting-banner.component';

describe('saved posting banner', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('keeps the saved marker on the banner only for consecutive messages', () => {
        const fixture = TestBed.createComponent(SavedPostingBannerComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.communication.post.saved');
        expect(fixture.nativeElement.classList.contains('is-saved')).toBe(false);
        fixture.componentRef.setInput('consecutive', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.classList.contains('is-saved')).toBe(true);
    });
});
