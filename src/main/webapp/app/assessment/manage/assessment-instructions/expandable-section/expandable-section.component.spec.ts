import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockPipe } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';

describe('ExpandableSectionComponent', () => {
    let component: ExpandableSectionComponent;
    let fixture: ComponentFixture<ExpandableSectionComponent>;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExpandableSectionComponent, MockPipe(ArtemisTranslatePipe)],
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
        jest.restoreAllMocks();
    });

    it('should get correct key', () => {
        const headerKey = 'test';
        fixture.componentRef.setInput('headerKey', headerKey);

        const key = component.storageKey;

        expect(key).toEqual(component.PREFIX + headerKey);
    });

    it('should load state from local storage on init', () => {
        fixture.componentRef.setInput('headerKey', 'test');
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(true);
        const storeSpy = jest.spyOn(localStorageService, 'store');

        component.ngOnInit();

        expect(retrieveSpy).toHaveBeenCalledWith(component.storageKey);
        expect(component.isCollapsed).toBeTrue();
        expect(storeSpy).toHaveBeenCalledWith(component.storageKey, true);
    });

    it('should toggle state on toggle of collapsed', () => {
        fixture.componentRef.setInput('headerKey', 'test');
        component.isCollapsed = true;

        const storeSpy = jest.spyOn(localStorageService, 'store');

        component.toggleCollapsed();

        expect(component.isCollapsed).toBeFalse();
        expect(storeSpy).toHaveBeenCalledWith(component.storageKey, false);
    });
});
