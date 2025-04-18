import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuickActionsComponent } from './quick-actions.component';

describe('QuickActionsComponent', () => {
    let component: QuickActionsComponent;
    let fixture: ComponentFixture<QuickActionsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuickActionsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(QuickActionsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
