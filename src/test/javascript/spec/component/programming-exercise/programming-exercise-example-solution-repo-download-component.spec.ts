import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-example-solution-repo-download.component';

describe('ProgrammingExerciseExampleSolutionRepoDownloadComponent', () => {
    let component: ProgrammingExerciseExampleSolutionRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseExampleSolutionRepoDownloadComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseExampleSolutionRepoDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent);
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
        const spy = jest.spyOn(service, 'exportStudentRequestedRepository');
        component.exportRepository();
        expect(spy).not.toHaveBeenCalled();
    });

    it('should download the repos', () => {
        component.exerciseId = 1;
        const exportExampleSolutionRepositorySpy = jest.spyOn(service, 'exportStudentRequestedRepository');
        component.exportRepository();
        expect(exportExampleSolutionRepositorySpy).toHaveBeenCalledOnce();
        exportExampleSolutionRepositorySpy.mockRestore();
    });
});
