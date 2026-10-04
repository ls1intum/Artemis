import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { MockPipe } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';

describe('ExpandableSectionComponent', () => {
    let component: ExpandableSectionComponent;
    let fixture: ComponentFixture<ExpandableSectionComponent>;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExpandableSectionComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [LocalStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExpandableSectionComponent);
                localStorageService = TestBed.inject(LocalStorageService);
                component = fixture.componentInstance;
            });
    });
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([false, true])('announces expansion after keyboard activation for subheader %s', (isSubHeader) => {
        fixture.componentRef.setInput('headerKey', 'Instructions');
        fixture.componentRef.setInput('hasTranslation', false);
        fixture.componentRef.setInput('isSubHeader', isSubHeader);
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(true);
        fixture.detectChanges();
        const header = fixture.nativeElement.querySelector('.expandable-header') as HTMLElement;
        expect(header.getAttribute('aria-expanded')).toBe('false');
        header.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        fixture.detectChanges();
        expect(component.isCollapsed()).toBe(false);
        expect(header.getAttribute('aria-expanded')).toBe('true');
        header.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        fixture.detectChanges();
        expect(component.isCollapsed()).toBe(true);
        expect(header.getAttribute('aria-expanded')).toBe('false');
    });

    it('should get correct key', () => {
        const headerKey = 'test';
        fixture.componentRef.setInput('headerKey', headerKey);

        const key = component.storageKey;

        expect(key).toEqual(component.PREFIX + headerKey);
    });

    it('should load state from local storage on init', () => {
        fixture.componentRef.setInput('headerKey', 'test');
        const retrieveSpy = vi.spyOn(localStorageService, 'retrieve').mockReturnValue(true);
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.ngOnInit();

        expect(retrieveSpy).toHaveBeenCalledWith(component.storageKey);
        expect(component.isCollapsed()).toBe(true);
        expect(storeSpy).toHaveBeenCalledWith(component.storageKey, true);
    });

    it('should toggle state on toggle of collapsed', () => {
        fixture.componentRef.setInput('headerKey', 'test');
        component.isCollapsed.set(true);

        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.toggleCollapsed();

        expect(component.isCollapsed()).toBe(false);
        expect(storeSpy).toHaveBeenCalledWith(component.storageKey, false);
    });
});
