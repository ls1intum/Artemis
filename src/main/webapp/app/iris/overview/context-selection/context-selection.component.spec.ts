import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ContextSelectionComponent } from './context-selection.component';

describe('ContextSelectionComponent', () => {
    let component: ContextSelectionComponent;
    let fixture: ComponentFixture<ContextSelectionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ContextSelectionComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ContextSelectionComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
