import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FireworksComponent } from 'app/atlas/overview/fireworks/fireworks.component';

describe('Fireworks', () => {
    let fixture: ComponentFixture<FireworksComponent>;
    let component: FireworksComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FireworksComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();

        const element = fixture.debugElement.query(By.css('div.pyro'));
        expect(element).toBeNull();
    });

    it('should be visible', () => {
        fixture.componentRef.setInput('active', true);

        fixture.detectChanges();

        const element = fixture.debugElement.query(By.css('div.pyro'));
        expect(element).not.toBeNull();
    });
});
