import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IrisLearnerProfileComponent } from './iris-learner-profile.component';

describe('IrisLearnerProfileComponent', () => {
    let component: IrisLearnerProfileComponent;
    let fixture: ComponentFixture<IrisLearnerProfileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisLearnerProfileComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisLearnerProfileComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
