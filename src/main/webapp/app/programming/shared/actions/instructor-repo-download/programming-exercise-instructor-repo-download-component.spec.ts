import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { MockProgrammingExerciseService } from '../../../../../../../test/javascript/spec/helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { RepositoryType } from '../../code-editor/model/code-editor.model';

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
        const repoTypes: RepositoryType[] = [RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS, RepositoryType.AUXILIARY];
        repoTypes.forEach((repoType) => {
            const exportInstructorRepositorySpy = jest.spyOn(service, 'exportInstructorRepository');
            component.repositoryType = repoType;
            component.exportRepository();
            expect(exportInstructorRepositorySpy).toHaveBeenCalledOnce();
            exportInstructorRepositorySpy.mockRestore();
        });
    });
});
