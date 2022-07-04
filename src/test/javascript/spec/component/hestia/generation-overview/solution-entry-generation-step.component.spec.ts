import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SolutionEntryGenerationStepComponent } from 'app/exercises/programming/hestia/generation-overview/steps/solution-entry-generation-step/solution-entry-generation-step.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { EventEmitter } from '@angular/core';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType } from 'app/entities/programming-exercise-test-case.model';
import { SortingOrder } from 'app/shared/table/pageable-table';

describe('SolutionEntryGenerationStep Component', () => {
    let comp: SolutionEntryGenerationStepComponent;
    let fixture: ComponentFixture<SolutionEntryGenerationStepComponent>;

    let exerciseService: ProgrammingExerciseService;
    let modalService: NgbModal;
    let alertService: AlertService;
    let solutionEntryService: ProgrammingExerciseSolutionEntryService;

    let onEntryUpdatedSpy: jest.SpyInstance;

    let exercise: ProgrammingExercise;
    let entry1: ProgrammingExerciseSolutionEntry;
    let entry2: ProgrammingExerciseSolutionEntry;
    let solutionEntries: ProgrammingExerciseSolutionEntry[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SolutionEntryGenerationStepComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbModal), MockProvider(AlertService), MockProvider(ArtemisTranslatePipe)],
        }).compileComponents();
        fixture = TestBed.createComponent(SolutionEntryGenerationStepComponent);
        comp = fixture.componentInstance;

        exerciseService = TestBed.inject(ProgrammingExerciseService);
        modalService = TestBed.inject(NgbModal);
        alertService = TestBed.inject(AlertService);
        solutionEntryService = TestBed.inject(ProgrammingExerciseSolutionEntryService);

        onEntryUpdatedSpy = jest.spyOn(comp.onEntryUpdate, 'emit');

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        comp.exercise = exercise;
        comp.solutionEntries = [];

        entry1 = new ProgrammingExerciseSolutionEntry();
        entry1.id = 2;
        const testCase1 = new ProgrammingExerciseTestCase();
        testCase1.id = 4;
        testCase1.type = ProgrammingExerciseTestCaseType.STRUCTURAL;
        testCase1.testName = 'test1';
        entry1.testCase = testCase1;

        entry2 = new ProgrammingExerciseSolutionEntry();
        entry2.id = 3;
        const testCase2 = new ProgrammingExerciseTestCase();
        testCase2.id = 5;
        testCase2.type = ProgrammingExerciseTestCaseType.BEHAVIORAL;
        testCase2.testName = 'test2';
        entry2.testCase = testCase2;

        solutionEntries = [entry1, entry2];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load all solution entries on init', () => {
        const loadEntriesSpy = jest.spyOn(solutionEntryService, 'getSolutionEntriesForExercise').mockReturnValue(of(solutionEntries));

        comp.ngOnInit();

        expect(loadEntriesSpy).toHaveBeenCalledOnce();
        expect(loadEntriesSpy).toHaveBeenCalledWith(1);
        expect(comp.solutionEntries).toEqual(solutionEntries);
        expect(comp.isLoading).toBeFalse();
        expect(onEntryUpdatedSpy).toHaveBeenCalledOnce();
        expect(onEntryUpdatedSpy).toHaveBeenCalledWith(solutionEntries);
    });

    it('should open editable solution entry details modal', () => {
        const mockModal = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModal);

        comp.openSolutionEntryModal(entry1, true);

        expect(openSpy).toHaveBeenCalledOnce();
        expect(mockModal.componentInstance.exerciseId).toBe(1);
        expect(mockModal.componentInstance.solutionEntry).toEqual(entry1);
        expect(mockModal.componentInstance.isEditable).toBeTrue();
    });

    it('should open manual solution entry creation modal', () => {
        const mockModal = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        const emitter = new EventEmitter<ProgrammingExerciseSolutionEntry>();
        mockModal.componentInstance.onEntryCreated = emitter;

        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModal);

        comp.openManualEntryCreationModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(mockModal.componentInstance.exerciseId).toBe(1);

        emitter.emit(entry1);
        expect(comp.solutionEntries).toHaveLength(1);
        expect(comp.solutionEntries).toContain(entry1);
    });

    it('should generate structural solution entries', () => {
        const generateSpy = jest.spyOn(exerciseService, 'createStructuralSolutionEntries').mockReturnValue(of([entry1]));
        const successSpy = jest.spyOn(alertService, 'success');
        // only one behavioral entry
        comp.solutionEntries = [entry2];

        comp.onGenerateStructuralSolutionEntries();

        expect(generateSpy).toHaveBeenCalledOnce();
        expect(generateSpy).toHaveBeenCalledWith(1);
        expect(successSpy).toHaveBeenCalledOnce();
        expect(comp.solutionEntries).toContainAllValues([entry1, entry2]);
        expect(comp.testCaseSortOrder).toBeUndefined();
        expect(onEntryUpdatedSpy).toHaveBeenCalledOnce();
        expect(onEntryUpdatedSpy).toHaveBeenCalledWith([entry2, entry1]);
    });

    it('should generate behavioral solution entries', () => {
        const generateSpy = jest.spyOn(exerciseService, 'createBehavioralSolutionEntries').mockReturnValue(of([entry2]));
        const successSpy = jest.spyOn(alertService, 'success');
        // only one structural entry
        comp.solutionEntries = [entry1];

        comp.onGenerateBehavioralSolutionEntries();

        expect(generateSpy).toHaveBeenCalledOnce();
        expect(generateSpy).toHaveBeenCalledWith(1);
        expect(successSpy).toHaveBeenCalledOnce();
        expect(comp.solutionEntries).toContainAllValues([entry1, entry2]);
        expect(comp.testCaseSortOrder).toBeUndefined();
        expect(onEntryUpdatedSpy).toHaveBeenCalledOnce();
        expect(onEntryUpdatedSpy).toHaveBeenCalledWith(solutionEntries);
    });

    it('should delete all solution entries', () => {
        const deleteAllSpy = jest.spyOn(solutionEntryService, 'deleteAllSolutionEntriesForExercise').mockReturnValue(new Observable((s) => s.next()));
        comp.deleteAllSolutionEntries();

        expect(deleteAllSpy).toHaveBeenCalledOnce();
        expect(deleteAllSpy).toHaveBeenCalledWith(1);
        expect(comp.solutionEntries).toHaveLength(0);
        expect(onEntryUpdatedSpy).toHaveBeenCalledOnce();
        expect(onEntryUpdatedSpy).toHaveBeenCalledWith([]);
    });

    it('should delete individual solution entry', () => {
        const deleteIndividualSpy = jest.spyOn(solutionEntryService, 'deleteSolutionEntry').mockReturnValue(new Observable((s) => s.next()));
        comp.solutionEntries = solutionEntries;

        comp.deleteSolutionEntry(entry1);

        expect(deleteIndividualSpy).toHaveBeenCalledOnce();
        expect(deleteIndividualSpy).toHaveBeenCalledWith(1, 4, 2);
        expect(onEntryUpdatedSpy).toHaveBeenCalledOnce();
        expect(onEntryUpdatedSpy).toHaveBeenCalledWith([entry2]);
        expect(comp.solutionEntries).toEqual([entry2]);
    });

    it('should sort tests ascending from no order', () => {
        comp.solutionEntries = [entry2, entry1];
        comp.changeTestCaseSortOrder();
        expect(comp.solutionEntries).toEqual([entry1, entry2]);
        expect(comp.testCaseSortOrder).toBe(SortingOrder.ASCENDING);
    });

    it('should sort tests descending from ascending', () => {
        comp.testCaseSortOrder = SortingOrder.ASCENDING;
        comp.solutionEntries = [entry1, entry2];

        comp.changeTestCaseSortOrder();

        expect(comp.solutionEntries).toEqual([entry2, entry1]);
        expect(comp.testCaseSortOrder).toBe(SortingOrder.DESCENDING);
    });

    it('should sort tests ascending from descending', () => {
        comp.testCaseSortOrder = SortingOrder.DESCENDING;
        comp.solutionEntries = [entry2, entry1];

        comp.changeTestCaseSortOrder();

        expect(comp.solutionEntries).toEqual([entry1, entry2]);
        expect(comp.testCaseSortOrder).toBe(SortingOrder.ASCENDING);
    });
});
