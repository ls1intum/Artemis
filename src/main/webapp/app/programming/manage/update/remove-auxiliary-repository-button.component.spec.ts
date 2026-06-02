import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/programming/manage/update/remove-auxiliary-repository-button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { provideHttpClient } from '@angular/common/http';

describe('RemoveAuxiliaryRepositoryButton', () => {
    setupTestBed({ zoneless: true });

    let comp: RemoveAuxiliaryRepositoryButtonComponent;
    let fixture: ComponentFixture<RemoveAuxiliaryRepositoryButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), MockComponent(ButtonComponent)],
            providers: [provideHttpClient()],
        });
        fixture = TestBed.createComponent(RemoveAuxiliaryRepositoryButtonComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.componentRef.setInput('row', new AuxiliaryRepository());
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should remove auxiliary repository', () => {
        const auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.id = 4;
        const auxiliaryRepositories = [auxiliaryRepository];
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.auxiliaryRepositories = auxiliaryRepositories;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.componentRef.setInput('row', auxiliaryRepository);

        fixture.detectChanges();
        comp.removeAuxiliaryRepository();

        expect(comp.programmingExercise().auxiliaryRepositories).toEqual([]);
    });
});
