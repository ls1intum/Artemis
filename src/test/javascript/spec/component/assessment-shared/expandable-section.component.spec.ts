import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/assessment-instructions/expandable-section/expandable-section.component';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

describe('ExpandableSectionComponent', () => {
    let component: ExpandableSectionComponent;
    let fixture: ComponentFixture<ExpandableSectionComponent>;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExpandableSectionComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: LocalStorageService, useClass: MockLocalStorageService }],
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

    it('should load state from local storage on init', () => {
        const key = 'test';
        component.headerKey = key;
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(true);
        const storeSpy = jest.spyOn(localStorageService, 'store');

        component.ngOnInit();

        expect(retrieveSpy).toHaveBeenCalledWith(key);
        expect(component.isCollapsed).toBeTrue();
        expect(storeSpy).toHaveBeenCalledWith(key, true);
    });

    it('should toggle state on toggle of collapsed', () => {
        const key = 'test';

        component.headerKey = key;
        component.isCollapsed = true;

        const storeSpy = jest.spyOn(localStorageService, 'store');

        component.toggleCollapsed();

        expect(component.isCollapsed).toBeFalse();
        expect(storeSpy).toHaveBeenCalledWith(key, false);
    });
});
