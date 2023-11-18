import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BooleanIconComponent } from 'app/shared/boolean-icon/boolean-icon.component';

describe('BooleanIconComponent', () => {
    let component: BooleanIconComponent;
    let fixture: ComponentFixture<BooleanIconComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [BooleanIconComponent],
            providers: [],
        }).compileComponents();

        fixture = TestBed.createComponent(BooleanIconComponent);
        component = fixture.componentInstance;
    });

    it('should initialize', () => {
        component.iconBoolean = false;
        fixture.detectChanges();
        expect(BooleanIconComponent).not.toBeNull();
    });
});
