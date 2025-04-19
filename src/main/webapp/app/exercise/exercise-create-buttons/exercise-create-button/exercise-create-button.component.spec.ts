import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseCreateButtonComponent } from './exercise-create-button.component';

describe('ExerciseCreateButtonComponent', () => {
    let component: ExerciseCreateButtonComponent;
    let fixture: ComponentFixture<ExerciseCreateButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseCreateButtonComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseCreateButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
