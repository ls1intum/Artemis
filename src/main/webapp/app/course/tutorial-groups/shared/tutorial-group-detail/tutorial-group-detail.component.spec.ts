import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupDetailComponent } from './tutorial-group-detail.component';

describe('TutorialGroupDetailComponent', () => {
    let component: TutorialGroupDetailComponent;
    let fixture: ComponentFixture<TutorialGroupDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupDetailComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
