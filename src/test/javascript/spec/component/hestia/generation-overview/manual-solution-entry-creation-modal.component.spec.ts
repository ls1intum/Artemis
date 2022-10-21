import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ManualSolutionEntryCreationModalComponent } from 'app/exercises/programming/hestia/generation-overview/manual-solution-entry-creation-modal/manual-solution-entry-creation-modal.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';

describe('ManualSolutionEntryCreationModal Component', () => {
    let comp: ManualSolutionEntryCreationModalComponent;
    let fixture: ComponentFixture<ManualSolutionEntryCreationModalComponent>;

    let exerciseService: ProgrammingExerciseService;
    let solutionEntryService: ProgrammingExerciseSolutionEntryService;
    let modalService: NgbActiveModal;

    let testCases: ProgrammingExerciseTestCase[];
    const solutionFiles = ['A.java', 'B.java'];

    let testCaseSpy: jest.SpyInstance;
    let getFileNamesSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(ManualSolutionEntryCreationModalComponent);
        comp = fixture.componentInstance;

        exerciseService = TestBed.inject(ProgrammingExerciseService);
        solutionEntryService = TestBed.inject(ProgrammingExerciseSolutionEntryService);
        modalService = TestBed.inject(NgbActiveModal);

        const testCase1 = new ProgrammingExerciseTestCase();
        testCase1.id = 2;
        const testCase2 = new ProgrammingExerciseTestCase();
        testCase2.id = 3;
        testCases = [testCase1, testCase2];
        testCaseSpy = jest.spyOn(exerciseService, 'getAllTestCases').mockReturnValue(of(testCases));

        getFileNamesSpy = jest.spyOn(exerciseService, 'getSolutionFileNames').mockReturnValue(of(solutionFiles));

        comp.exerciseId = 1;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load test cases and files on init', () => {
        comp.ngOnInit();

        expect(testCaseSpy).toHaveBeenCalledOnce();
        expect(testCaseSpy).toHaveBeenCalledWith(1);
        expect(comp.testCases).toEqual(testCases);
        expect(getFileNamesSpy).toHaveBeenCalledOnce();
        expect(getFileNamesSpy).toHaveBeenCalledWith(1);
        expect(comp.solutionRepositoryFileNames).toEqual(solutionFiles);
    });

    it('should create manual entry', () => {
        const entryToCreate = new ProgrammingExerciseSolutionEntry();
        entryToCreate.testCase = testCases[0];
        comp.solutionEntry = entryToCreate;

        const createdEntry = new ProgrammingExerciseSolutionEntry();
        createdEntry.id = 4;
        const createSpy = jest.spyOn(solutionEntryService, 'createSolutionEntry').mockReturnValue(of(createdEntry));
        const createdEmitterSpy = jest.spyOn(comp.onEntryCreated, 'emit');
        const closedSpy = jest.spyOn(modalService, 'close');

        comp.onCreateEntry();

        expect(createSpy).toHaveBeenCalledOnce();
        expect(createSpy).toHaveBeenCalledWith(1, 2, entryToCreate);
        expect(createdEmitterSpy).toHaveBeenCalledOnce();
        expect(createdEmitterSpy).toHaveBeenCalledWith(createdEntry);
        expect(closedSpy).toHaveBeenCalledOnce();
    });

    it('should close modal', () => {
        const closedSpy = jest.spyOn(modalService, 'close');
        comp.clear();
        expect(closedSpy).toHaveBeenCalledOnce();
    });
});
