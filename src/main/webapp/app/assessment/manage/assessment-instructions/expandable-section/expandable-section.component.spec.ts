import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockPipe } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';

describe('ExpandableSectionComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExpandableSectionComponent;
    let fixture: ComponentFixture<ExpandableSectionComponent>;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExpandableSectionComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [LocalStorageService],
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
        expect(component.isCollapsed).toBe(true);
        expect(storeSpy).toHaveBeenCalledWith(component.storageKey, true);
    });

    it('should toggle state on toggle of collapsed', () => {
        fixture.componentRef.setInput('headerKey', 'test');
        component.isCollapsed = true;

        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.toggleCollapsed();

        expect(component.isCollapsed).toBe(false);
        expect(storeSpy).toHaveBeenCalledWith(component.storageKey, false);
    });
});
