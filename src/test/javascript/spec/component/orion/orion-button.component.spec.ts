import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockDirective } from 'ng-mocks';

describe('OrionButtonComponent', () => {
    let comp: OrionButtonComponent;
    let fixture: ComponentFixture<OrionButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrionButtonComponent, MockDirective(FeatureToggleDirective)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionButtonComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should calculate btnPrimary correctly', () => {
        comp.outlined = true;
        expect(comp.btnPrimary).toBeFalse();
    });

    it('should not forward click if buttonLoading', () => {
        const emitSpy = jest.spyOn(comp.clickHandler, 'emit');
        comp.buttonLoading = true;

        const buttonElement = fixture.debugElement.query(By.css('button'));
        expect(buttonElement).not.toBe(null);

        buttonElement.nativeElement.click();

        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should forward click if not buttonLoading', () => {
        const emitSpy = jest.spyOn(comp.clickHandler, 'emit');
        comp.buttonLoading = false;

        const buttonElement = fixture.debugElement.query(By.css('button'));
        expect(buttonElement).not.toBe(null);

        buttonElement.nativeElement.click();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
