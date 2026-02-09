import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialEditComponent } from './tutorial-edit.component';

describe('TutorialEdit', () => {
    let component: TutorialEditComponent;
    let fixture: ComponentFixture<TutorialEditComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialEditComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialEditComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
