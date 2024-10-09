import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { IconCardComponent } from 'app/shared/icon-card/icon-card.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('IconCardComponent', () => {
    let component: IconCardComponent;
    let fixture: ComponentFixture<IconCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IconCardComponent],
            declarations: [MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(IconCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
