import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { ArtemisTestModule } from '../../../test.module';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';

describe('RemoveAuxiliaryRepositoryButton', () => {
    let comp: RemoveAuxiliaryRepositoryButtonComponent;
    let fixture: ComponentFixture<RemoveAuxiliaryRepositoryButtonComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, MockComponent(ButtonComponent)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RemoveAuxiliaryRepositoryButtonComponent);
                comp = fixture.componentInstance;
            });
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
