import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { IconCardComponent } from 'app/shared/icon-card/icon-card.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faCircleInfo, faCoffee } from '@fortawesome/free-solid-svg-icons';

describe('IconCardComponent', () => {
    let component: IconCardComponent;
    let fixture: ComponentFixture<IconCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            declarations: [IconCardComponent, MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(IconCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).not.toBeNull();
    });

    it('should display the default headerIcon and headline', () => {
        expect(component.headerIcon()).toBe(faCircleInfo);
        expect(component.headline()).toBe('Title');
    });

    it('should display custom headerIcon and headline when inputs are set', () => {
        fixture.componentRef.setInput('headerIcon', faCoffee);
        fixture.componentRef.setInput('headline', 'Test');

        fixture.detectChanges();

        expect(component.headerIcon()).toBe(faCoffee);
        expect(component.headline()).toBe('Test');
    });
});
