import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BonusComponent } from 'app/grading-system/bonus-source/bonus.component';

describe('BonusComponent', () => {
    let component: BonusComponent;
    let fixture: ComponentFixture<BonusComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [BonusComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(BonusComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
