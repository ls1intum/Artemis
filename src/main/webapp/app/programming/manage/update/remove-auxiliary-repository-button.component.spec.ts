import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/programming/manage/update/remove-auxiliary-repository-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { provideHttpClient } from '@angular/common/http';

describe('RemoveAuxiliaryRepositoryButton', () => {
    let comp: RemoveAuxiliaryRepositoryButtonComponent;
    let fixture: ComponentFixture<RemoveAuxiliaryRepositoryButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), MockComponent(ButtonComponent)],
            providers: [provideHttpClient()],
        }).compileComponents();
        fixture = TestBed.createComponent(RemoveAuxiliaryRepositoryButtonComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should remove auxiliary repository', () => {
        const auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.id = 4;
        const auxiliaryRepositories = [auxiliaryRepository];
        comp.programmingExercise = new ProgrammingExercise(new Course(), undefined);
        comp.programmingExercise.auxiliaryRepositories = auxiliaryRepositories;
        comp.row = auxiliaryRepository;

        fixture.detectChanges();
        comp.removeAuxiliaryRepository();

        expect(comp.programmingExercise.auxiliaryRepositories).toBeEmpty();
    });
});
