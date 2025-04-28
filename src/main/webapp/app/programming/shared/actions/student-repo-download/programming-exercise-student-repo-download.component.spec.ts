import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/programming/shared/actions/student-repo-download/programming-exercise-student-repo-download.component';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';

describe('ProgrammingExerciseStudentRepoDownloadComponent', () => {
    let comp: ProgrammingExerciseStudentRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentRepoDownloadComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            providers: [
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: HttpClient, useClass: MockHttpService },
            ],
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
