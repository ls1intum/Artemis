import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-module-management/unit-creation-card/unit-creation-card.component';

describe('UnitCreationCardComponent', () => {
    let component: UnitCreationCardComponent;
    let fixture: ComponentFixture<UnitCreationCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [UnitCreationCardComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(UnitCreationCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
