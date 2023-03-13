import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseImportFromFileComponent } from 'app/exercises/shared/import/from-file/exercise-import-from-file.component';

describe('ExerciseImportFromFileComponent', () => {
    let component: ExerciseImportFromFileComponent;
    let fixture: ComponentFixture<ExerciseImportFromFileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExerciseImportFromFileComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportFromFileComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
