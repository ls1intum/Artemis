import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MetadataFieldRow, MetadataFieldRowComponent } from 'app/exercise/version-history/shared/metadata-field-row.component';

const baseField: MetadataFieldRow = {
    id: 'title',
    label: 'Title',
    currentDisplay: 'New Title',
    previousDisplay: 'Old Title',
    currentRaw: 'New Title',
    previousRaw: 'Old Title',
    currentEmpty: false,
    previousEmpty: false,
    revertable: true,
};

describe('MetadataFieldRowComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<MetadataFieldRowComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetadataFieldRowComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(MetadataFieldRowComponent);
        fixture.componentRef.setInput('field', baseField);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render label and current display value in full view mode', () => {
        const el: HTMLElement = fixture.nativeElement;
        const label = el.querySelector('.metadata-row__label');
        expect(label).toBeTruthy();
        expect(label!.textContent).toContain('Title');

        const value = el.querySelector('.metadata-row__value');
        expect(value).toBeTruthy();
        expect(value!.textContent).toContain('New Title');

        expect(el.querySelector('.metadata-diff')).toBeFalsy();
    });

    it('should render old and new values with diff markup in diff view mode', () => {
        fixture.componentRef.setInput('isDiffView', true);
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const oldSpan = el.querySelector('.metadata-diff__value--old');
        const newSpan = el.querySelector('.metadata-diff__value--new');

        expect(oldSpan).toBeTruthy();
        expect(oldSpan!.textContent).toContain('Old Title');

        expect(newSpan).toBeTruthy();
        expect(newSpan!.textContent).toContain('New Title');

        expect(el.querySelector('.metadata-row__value')).toBeFalsy();
    });

    it('should show revert button when isDiffView is true and field is revertable', () => {
        fixture.componentRef.setInput('isDiffView', true);
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const revertButton = el.querySelector('.metadata-diff__revert');
        expect(revertButton).toBeTruthy();
    });

    it('should not show revert button when field is not revertable', () => {
        fixture.componentRef.setInput('isDiffView', true);
        fixture.componentRef.setInput('field', { ...baseField, revertable: false });
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const revertButton = el.querySelector('.metadata-diff__revert');
        expect(revertButton).toBeFalsy();
    });

    it('should emit revertField with correct payload when revert button is clicked', () => {
        fixture.componentRef.setInput('isDiffView', true);
        fixture.detectChanges();

        const emitSpy = vi.spyOn(fixture.componentInstance.revertField, 'emit');

        const el: HTMLElement = fixture.nativeElement;
        const revertButton = el.querySelector<HTMLButtonElement>('.metadata-diff__revert');
        expect(revertButton).toBeTruthy();
        revertButton!.click();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith({
            fieldId: 'title',
            fieldLabel: 'Title',
            previousRaw: 'Old Title',
        });
    });

    it('should show unset element when showUnsetLabel is true and field is empty', () => {
        fixture.componentRef.setInput('showUnsetLabel', true);
        fixture.componentRef.setInput('field', { ...baseField, currentEmpty: true, currentDisplay: '–' });
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const unsetSpan = el.querySelector('[jhiTranslate="global.generic.unset"]');
        expect(unsetSpan).toBeTruthy();
        expect(unsetSpan!.classList.contains('metadata-row__value--empty')).toBeTrue();
    });

    it('should show currentDisplay when showUnsetLabel is false even if field is empty', () => {
        fixture.componentRef.setInput('showUnsetLabel', false);
        fixture.componentRef.setInput('field', { ...baseField, currentEmpty: true, currentDisplay: '–' });
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const unsetSpan = el.querySelector('[jhiTranslate="global.generic.unset"]');
        expect(unsetSpan).toBeFalsy();

        const value = el.querySelector('.metadata-row__value');
        expect(value).toBeTruthy();
        expect(value!.textContent).toContain('–');
    });

    it('should apply empty CSS classes in diff view when previousEmpty or currentEmpty is true', () => {
        fixture.componentRef.setInput('isDiffView', true);
        fixture.componentRef.setInput('field', { ...baseField, previousEmpty: true, currentEmpty: true });
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const oldSpan = el.querySelector('.metadata-diff__value--old');
        const newSpan = el.querySelector('.metadata-diff__value--new');

        expect(oldSpan!.classList.contains('metadata-diff__value--empty')).toBeTrue();
        expect(newSpan!.classList.contains('metadata-diff__value--empty')).toBeTrue();
    });

    it('should not apply empty CSS classes in diff view when previousEmpty and currentEmpty are false', () => {
        fixture.componentRef.setInput('isDiffView', true);
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const oldSpan = el.querySelector('.metadata-diff__value--old');
        const newSpan = el.querySelector('.metadata-diff__value--new');

        expect(oldSpan!.classList.contains('metadata-diff__value--empty')).toBeFalse();
        expect(newSpan!.classList.contains('metadata-diff__value--empty')).toBeFalse();
    });
});
