import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseImportButtonComponent } from './exercise-import-button.component';

describe('ExerciseImportButtonComponent', () => {
    let component: ExerciseImportButtonComponent;
    let fixture: ComponentFixture<ExerciseImportButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseImportButtonComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
