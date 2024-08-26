import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-repo-download.component';

describe('ProgrammingExerciseStudentRepoDownloadComponent', () => {
    let comp: ProgrammingExerciseStudentRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentRepoDownloadComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseInstructorRepoDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseStudentRepoDownloadComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not attempt to download if the exercise id is missing', () => {
        const exportSpy = jest.spyOn(programmingExerciseService, 'exportStudentRepository');
        comp.participationId = 100;
        fixture.detectChanges();
        comp.exportRepository();
        expect(exportSpy).not.toHaveBeenCalled();
    });

    it('should not attempt to download if the participation id is missing', () => {
        const exportSpy = jest.spyOn(programmingExerciseService, 'exportStudentRepository');
        comp.exerciseId = 100;
        fixture.detectChanges();
        comp.exportRepository();
        expect(exportSpy).not.toHaveBeenCalled();
    });

    it('should download the correct repository', () => {
        const exportSpy = jest.spyOn(programmingExerciseService, 'exportStudentRepository');
        comp.exerciseId = 10;
        comp.participationId = 20;
        fixture.detectChanges();
        comp.exportRepository();
        expect(exportSpy).toHaveBeenCalledExactlyOnceWith(10, 20);
    });
});
