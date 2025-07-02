import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemirisGraphViewComponent } from './memiris-graph-view.component';

describe('MemirisGraphViewComponent', () => {
    let component: MemirisGraphViewComponent;
    let fixture: ComponentFixture<MemirisGraphViewComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisGraphViewComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisGraphViewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
