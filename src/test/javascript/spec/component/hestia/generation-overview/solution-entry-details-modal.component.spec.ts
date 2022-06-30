import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { of } from 'rxjs';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { SolutionEntryDetailsModalComponent } from 'app/exercises/programming/hestia/generation-overview/solution-entry-details-modal/solution-entry-details-modal.component';

describe('SolutionEntryDetailsModal Component', () => {
    let comp: SolutionEntryDetailsModalComponent;
    let fixture: ComponentFixture<SolutionEntryDetailsModalComponent>;

    let solutionEntryService: ProgrammingExerciseSolutionEntryService;
    let modalService: NgbActiveModal;

    let solutionEntry: ProgrammingExerciseSolutionEntry;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(SolutionEntryDetailsModalComponent);
        comp = fixture.componentInstance;

        solutionEntryService = TestBed.inject(ProgrammingExerciseSolutionEntryService);
        modalService = TestBed.inject(NgbActiveModal);

        comp.exerciseId = 1;

        const testCase = new ProgrammingExerciseTestCase();
        testCase.id = 2;

        solutionEntry = new ProgrammingExerciseSolutionEntry();
        solutionEntry.id = 3;
        solutionEntry.testCase = testCase;

        comp.solutionEntry = solutionEntry;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create manual entry', () => {
        const updateSpy = jest.spyOn(solutionEntryService, 'updateSolutionEntry').mockReturnValue(of(solutionEntry));
        const closedSpy = jest.spyOn(modalService, 'close');

        comp.saveSolutionEntry();

        expect(updateSpy).toHaveBeenCalledOnce();
        expect(updateSpy).toHaveBeenCalledWith(1, 2, 3, solutionEntry);
        expect(closedSpy).toHaveBeenCalledOnce();
        expect(comp.solutionEntry).toEqual(solutionEntry);
    });

    it('should close modal', () => {
        const closedSpy = jest.spyOn(modalService, 'close');
        comp.clear();
        expect(closedSpy).toHaveBeenCalledOnce();
    });
});
