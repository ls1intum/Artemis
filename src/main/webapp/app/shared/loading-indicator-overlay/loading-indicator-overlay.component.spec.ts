import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoadingIndicatorOverlayComponent } from './loading-indicator-overlay.component';

describe('LoadingIndicatorOverlay', () => {
    let component: LoadingIndicatorOverlayComponent;
    let fixture: ComponentFixture<LoadingIndicatorOverlayComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LoadingIndicatorOverlayComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LoadingIndicatorOverlayComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
