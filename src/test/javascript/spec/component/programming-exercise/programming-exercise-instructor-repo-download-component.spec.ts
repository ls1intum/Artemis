import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

describe('ProgrammingExerciseInstructorRepoDownloadComponent', () => {
    let component: ProgrammingExerciseInstructorRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorRepoDownloadComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseInstructorRepoDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorRepoDownloadComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ProgrammingExerciseService);
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should not download when there is no exercise', () => {
        const spy = jest.spyOn(service, 'exportInstructorRepository');
        component.exportRepository();
        expect(spy).not.toHaveBeenCalled();
    });

    it('should download the repos', () => {
        component.exerciseId = 1;
        const repoTypes: ProgrammingExerciseInstructorRepositoryType[] = ['SOLUTION', 'TEMPLATE', 'TESTS', 'AUXILIARY'];
        repoTypes.forEach((repoType) => {
            const exportInstructorRepositorySpy = jest.spyOn(service, 'exportInstructorRepository');
            component.repositoryType = repoType;
            component.exportRepository();
            expect(exportInstructorRepositorySpy).toHaveBeenCalledOnce();
            exportInstructorRepositorySpy.mockRestore();
        });
    });
});
