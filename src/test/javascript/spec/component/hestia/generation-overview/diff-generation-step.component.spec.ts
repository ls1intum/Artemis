import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DiffGenerationStepComponent } from 'app/exercises/programming/hestia/generation-overview/steps/diff-generation-step/diff-generation-step.component';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

describe('DiffGenerationStep Component', () => {
    let comp: DiffGenerationStepComponent;
    let fixture: ComponentFixture<DiffGenerationStepComponent>;

    let exerciseService: ProgrammingExerciseService;

    let onGitDiffLoadedSpy: jest.SpyInstance;

    let exercise: ProgrammingExercise;
    let fileContentByPath: Map<string, string>;
    let gitDiffReport: ProgrammingExerciseGitDiffReport;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(DiffGenerationStepComponent);
        comp = fixture.componentInstance;

        exerciseService = TestBed.inject(ProgrammingExerciseService);

        onGitDiffLoadedSpy = jest.spyOn(comp.onGitDiffLoaded, 'emit');

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        comp.exercise = exercise;

        fileContentByPath = new Map<string, string>();
        fileContentByPath.set('A.java', 'abc');
        fileContentByPath.set('B.java', 'def');

        gitDiffReport = new ProgrammingExerciseGitDiffReport();
        gitDiffReport.programmingExercise = exercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load all code hints on init', () => {
        const loadTemplateFilesSpy = jest.spyOn(exerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(of(fileContentByPath));
        const loadSolutionFilesSpy = jest.spyOn(exerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(of(fileContentByPath));
        const loadReportSpy = jest.spyOn(exerciseService, 'getDiffReport').mockReturnValue(of(gitDiffReport));

        comp.ngOnInit();

        expect(loadTemplateFilesSpy).toHaveBeenCalledOnce();
        expect(loadTemplateFilesSpy).toHaveBeenCalledWith(1);
        expect(loadSolutionFilesSpy).toHaveBeenCalledOnce();
        expect(loadSolutionFilesSpy).toHaveBeenCalledWith(1);
        expect(loadReportSpy).toHaveBeenCalledOnce();
        expect(loadReportSpy).toHaveBeenCalledWith(1);

        expect(comp.gitDiffReport).toEqual(gitDiffReport);
        expect(comp.isLoading).toBeFalse();
        expect(onGitDiffLoadedSpy).toHaveBeenCalledOnce();
        expect(onGitDiffLoadedSpy).toHaveBeenCalledWith(gitDiffReport);
    });
});
