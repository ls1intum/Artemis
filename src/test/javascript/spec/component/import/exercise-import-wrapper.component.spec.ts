import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseImportTabsComponent } from 'app/exercises/shared/import/exercise-import-tabs.component';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

describe('ExerciseImportWrapperComponent', () => {
    let component: ExerciseImportWrapperComponent;
    let fixture: ComponentFixture<ExerciseImportWrapperComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(ExerciseImportTabsComponent), MockComponent(ExerciseImportComponent), FormsModule],
            declarations: [ExerciseImportWrapperComponent, MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportWrapperComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);

        fixture.detectChanges();
    });

    it.each([ExerciseType.TEXT, ExerciseType.QUIZ, ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD])(
        'should show correct header title for each exercise type',
        (exerciseType) => {
            component.exerciseType = exerciseType;
            component.ngOnInit();
            expect(component.titleKey).toBe(
                component.exerciseType === ExerciseType.FILE_UPLOAD
                    ? `artemisApp.fileUploadExercise.home.importLabel`
                    : `artemisApp.${component.exerciseType}Exercise.home.importLabel`,
            );
        },
    );

    it('should show correct header title for SCA import', () => {
        component.exerciseType = ExerciseType.PROGRAMMING;
        component.programmingLanguage = ProgrammingLanguage.JAVA;
        component.ngOnInit();
        expect(component.titleKey).toBe('artemisApp.programmingExercise.configureGrading.categories.importLabel');
    });

    it('should show tabs component for programming-exercises', () => {
        component.exerciseType = ExerciseType.PROGRAMMING;
        fixture.detectChanges();
        const tabsComponent = fixture.debugElement.nativeElement.querySelector('#exercise-import-tabs');
        const exerciseImportComponent = fixture.debugElement.nativeElement.querySelector('#exercise-import');
        expect(tabsComponent).toBeTruthy();
        expect(exerciseImportComponent).toBeNull();
    });

    it.each([ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD, ExerciseType.QUIZ])(
        'should show exercise-import component for all other exercise types',
        (exerciseType) => {
            component.exerciseType = exerciseType;
            fixture.detectChanges();
            const tabsComponent = fixture.debugElement.nativeElement.querySelector('#exercise-import-tabs');
            const exerciseImportComponent = fixture.debugElement.nativeElement.querySelector('#exercise-import');
            expect(tabsComponent).toBeNull();
            expect(exerciseImportComponent).toBeTruthy();
        },
    );

    it('should close the active modal when clicking cancel button', () => {
        const activeModalSpy = jest.spyOn(activeModal, 'dismiss');
        fixture.debugElement.nativeElement.querySelector('#cancel-btn').click();
        expect(activeModalSpy).toHaveBeenCalledOnce();
    });
});
