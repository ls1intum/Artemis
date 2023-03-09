import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CoverageGenerationStepComponent } from 'app/exercises/programming/hestia/generation-overview/steps/coverage-generation-step/coverage-generation-step.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';

describe('CoverageGenerationStep Component', () => {
    let comp: CoverageGenerationStepComponent;
    let fixture: ComponentFixture<CoverageGenerationStepComponent>;

    let exerciseService: ProgrammingExerciseService;

    let onCoverageLoadedSpy: jest.SpyInstance;

    let exercise: ProgrammingExercise;
    let fileContentByPath: Map<string, string>;
    let coverageReport: CoverageReport;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(CoverageGenerationStepComponent);
        comp = fixture.componentInstance;

        exerciseService = TestBed.inject(ProgrammingExerciseService);

        onCoverageLoadedSpy = jest.spyOn(comp.onCoverageLoaded, 'emit');

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        comp.exercise = exercise;

        fileContentByPath = new Map<string, string>();
        fileContentByPath.set('A.java', 'abc');
        fileContentByPath.set('B.java', 'def');

        coverageReport = new CoverageReport();
        coverageReport.id = 2;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load all code hints on init', () => {
        const loadFilesSpy = jest.spyOn(exerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(of(fileContentByPath));
        const loadReportSpy = jest.spyOn(exerciseService, 'getLatestFullTestwiseCoverageReport').mockReturnValue(of(coverageReport));

        comp.ngOnInit();

        expect(loadFilesSpy).toHaveBeenCalledOnce();
        expect(loadFilesSpy).toHaveBeenCalledWith(1);
        expect(loadReportSpy).toHaveBeenCalledOnce();
        expect(loadReportSpy).toHaveBeenCalledWith(1);

        expect(comp.fileContentByPath).toEqual(fileContentByPath);
        expect(comp.coverageReport).toEqual(coverageReport);
        expect(comp.isLoading).toBeFalse();
        expect(onCoverageLoadedSpy).toHaveBeenCalledOnce();
        expect(onCoverageLoadedSpy).toHaveBeenCalledWith(coverageReport);
    });
});
