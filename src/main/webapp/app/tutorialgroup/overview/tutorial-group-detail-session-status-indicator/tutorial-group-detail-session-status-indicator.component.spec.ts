import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupDetailSessionStatusIndicatorComponent } from './tutorial-group-detail-session-status-indicator.component';

describe('TutorialGroupDetailSessionStatusIndicator', () => {
    let component: TutorialGroupDetailSessionStatusIndicatorComponent;
    let fixture: ComponentFixture<TutorialGroupDetailSessionStatusIndicatorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupDetailSessionStatusIndicatorComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupDetailSessionStatusIndicatorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
