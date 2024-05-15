import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';

describe('ProgrammingExercisePlansAndRepositoriesPreviewComponent', () => {
    let component: ProgrammingExercisePlansAndRepositoriesPreviewComponent;
    let fixture: ComponentFixture<ProgrammingExercisePlansAndRepositoriesPreviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ProgrammingExercisePlansAndRepositoriesPreviewComponent],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExercisePlansAndRepositoriesPreviewComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should display checkout directories when they exist', () => {
        component.exerciseCheckoutDirectory = 'exerciseDirectory';
        component.solutionCheckoutDirectory = 'solutionDirectory';
        component.testCheckoutDirectory = 'testDirectory';
        fixture.detectChanges();

        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('.preview-box')).toBeTruthy();
        expect(compiled.querySelector('.preview-box').textContent).toContain('exerciseDirectory');
        expect(compiled.querySelector('.preview-box').textContent).toContain('solutionDirectory');
        expect(compiled.querySelector('.preview-box').textContent).toContain('testDirectory');
    });

    it('should not display checkout directories when they do not exist', () => {
        component.exerciseCheckoutDirectory = undefined;
        component.solutionCheckoutDirectory = undefined;
        component.testCheckoutDirectory = undefined;
        fixture.detectChanges();

        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('.preview-box')).toBeFalsy();
    });
});
