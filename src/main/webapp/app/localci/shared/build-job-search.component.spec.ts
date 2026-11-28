import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { BuildJobSearchComponent } from './build-job-search.component';

describe('build job search', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('propagates the search term and requests a search on blur and button activation', async () => {
        const fixture = TestBed.createComponent(BuildJobSearchComponent);
        fixture.componentRef.setInput('searchTerm', 'Initial');
        const changed = vi.fn();
        const requested = vi.fn();
        fixture.componentInstance.searchTerm.subscribe(changed);
        fixture.componentInstance.searchRequested.subscribe(requested);
        fixture.detectChanges();
        await fixture.whenStable();
        const input = fixture.nativeElement.querySelector('input');
        expect(input.value).toBe('Initial');
        input.value = 'Updated';
        input.dispatchEvent(new Event('input'));
        expect(changed).toHaveBeenCalledWith('Updated');
        input.dispatchEvent(new Event('focusout'));
        fixture.nativeElement.querySelector('button').click();
        expect(requested).toHaveBeenCalledTimes(2);
    });
    it('renders an unset parent search term as an empty input without requesting a search', async () => {
        const fixture = TestBed.createComponent(BuildJobSearchComponent);
        fixture.componentRef.setInput('searchTerm', undefined);
        const requested = vi.fn();
        fixture.componentInstance.searchRequested.subscribe(requested);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.componentInstance.searchTerm()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('input').value).toBe('');
        expect(requested).not.toHaveBeenCalled();
    });
});
