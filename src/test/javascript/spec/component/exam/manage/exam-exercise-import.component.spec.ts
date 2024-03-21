import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { Exam } from 'app/entities/exam.model';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('Exam Exercise Import Component', () => {
    let component: ExamExerciseImportComponent;
    let fixture: ComponentFixture<ExamExerciseImportComponent>;

    // Initializing one Exercise Group per Exercise Type
    const exerciseGroup1 = { title: 'exerciseGroup1' } as ExerciseGroup;
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup1);
    modelingExercise.id = 1;
    modelingExercise.title = 'ModelingExercise';
    exerciseGroup1.exercises = [modelingExercise];

    // Exercise Group contains two exercises
    const exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
    const textExercise = new TextExercise(undefined, exerciseGroup2);
    textExercise.id = 2;
    textExercise.title = 'TextExercise';
    const textExercise2 = new TextExercise(undefined, exerciseGroup2);
    textExercise2.id = 22;
    textExercise2.title = 'TextExercise2';
    exerciseGroup2.exercises = [textExercise, textExercise2];

    const exerciseGroup3 = { title: 'exerciseGroup3' } as ExerciseGroup;
    const programmingExercise = new ProgrammingExercise(undefined, exerciseGroup3);
    programmingExercise.id = 3;
    programmingExercise.title = 'ProgrammingExercise';
    programmingExercise.shortName = 'progEx';
    exerciseGroup3.exercises = [programmingExercise];

    const exerciseGroup4 = { title: 'exerciseGroup4' } as ExerciseGroup;
    const quizExercise = new QuizExercise(undefined, exerciseGroup4);
    quizExercise.id = 4;
    quizExercise.title = 'QuizExercise';
    exerciseGroup4.exercises = [quizExercise];

    const exerciseGroup5 = { title: 'exerciseGroup5' } as ExerciseGroup;
    const fileUploadExercise = new FileUploadExercise(undefined, exerciseGroup5);
    fileUploadExercise.id = 5;
    fileUploadExercise.title = 'FileUploadExercise';
    exerciseGroup5.exercises = [fileUploadExercise];

    const exam1 = {
        id: 10,
        exerciseGroups: [exerciseGroup1, exerciseGroup2, exerciseGroup3, exerciseGroup4, exerciseGroup5],
    } as Exam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule)],
            declarations: [MockPipe(ArtemisTranslatePipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamExerciseImportComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize ngOnInit without titleAndShortNameOfProgrammingExercises', () => {
        component.exam = exam1;
        component.importInSameCourse = false;
        component.ngOnInit();

        expect(component.selectedExercises.size).toBe(5);
        expect(component.selectedExercises.get(exerciseGroup1)).toEqual(new Set<Exercise>().add(modelingExercise));
        expect(component.selectedExercises.get(exerciseGroup2)).toEqual(new Set<Exercise>().add(textExercise).add(textExercise2));
        expect(component.selectedExercises.get(exerciseGroup3)).toEqual(new Set<Exercise>().add(programmingExercise));
        expect(component.selectedExercises.get(exerciseGroup4)).toEqual(new Set<Exercise>().add(quizExercise));
        expect(component.selectedExercises.get(exerciseGroup5)).toEqual(new Set<Exercise>().add(fileUploadExercise));

        expect(component.containsProgrammingExercises.size).toBe(5);
        expect(component.containsProgrammingExercises.get(exerciseGroup3)).toBeTrue();
        expect(component.containsProgrammingExercises.get(exerciseGroup1)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup2)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup4)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup5)).toBeFalse();

        // For importInSameCourse = false, this should be preliminary empty
        expect(component.titleAndShortNameOfProgrammingExercises.size).toBe(0);
    });

    it('should initialize ngOnInit with titleAndShortNameOfProgrammingExercises', () => {
        component.exam = exam1;
        component.importInSameCourse = true;
        component.ngOnInit();

        expect(component.selectedExercises.size).toBe(5);
        expect(component.selectedExercises.get(exerciseGroup1)).toEqual(new Set<Exercise>().add(modelingExercise));
        expect(component.selectedExercises.get(exerciseGroup2)).toEqual(new Set<Exercise>().add(textExercise).add(textExercise2));
        expect(component.selectedExercises.get(exerciseGroup3)).toEqual(new Set<Exercise>().add(programmingExercise));
        expect(component.selectedExercises.get(exerciseGroup4)).toEqual(new Set<Exercise>().add(quizExercise));
        expect(component.selectedExercises.get(exerciseGroup5)).toEqual(new Set<Exercise>().add(fileUploadExercise));

        expect(component.containsProgrammingExercises.size).toBe(5);
        expect(component.containsProgrammingExercises.get(exerciseGroup3)).toBeTrue();
        expect(component.containsProgrammingExercises.get(exerciseGroup1)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup2)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup4)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup5)).toBeFalse();

        // For importInSameCourse = true, this should be initialized
        expect(component.titleAndShortNameOfProgrammingExercises.size).toBe(1);
        expect(component.titleAndShortNameOfProgrammingExercises.get(programmingExercise.id!)).toEqual([programmingExercise.title, programmingExercise.shortName]);
        expect(component.getBlocklistTitleOfProgrammingExercise(programmingExercise.id!)).toEqual(programmingExercise.title);
        expect(component.getBlocklistShortNameOfProgrammingExercise(programmingExercise.id!)).toEqual(programmingExercise.shortName);
    });

    it('should initialize maps when updateMapsAfterRejectedImport is called', () => {
        component.exam = exam1;
        component.importInSameCourse = false;
        // Step 1: Initialize the component
        component.ngOnInit();
        // As importInSameCourse = false, this should be preliminary empty
        expect(component.titleAndShortNameOfProgrammingExercises.size).toBe(0);

        // Needed to test the titleAndShortNameOfProgrammingExercises
        const formerProgrammingExerciseTitle = programmingExercise.title;
        const formerProgrammingExerciseShortName = programmingExercise.shortName;

        // Step 2: Simulate exception by the server, as the programming exercise project key is not unique -> title and short name are undefined;
        // Therefore create exercise group that is replacing the exerciseGroup3
        const exerciseGroup3Rejected = {} as ExerciseGroup;
        const programmingExerciseRejected = new ProgrammingExercise(undefined, exerciseGroup3Rejected);
        programmingExerciseRejected.id = 3;
        exerciseGroup3Rejected.exercises = [programmingExerciseRejected];
        component.exam = {
            id: 10,
            exerciseGroups: [exerciseGroup1, exerciseGroup2, exerciseGroup3Rejected, exerciseGroup4, exerciseGroup5],
        } as Exam;
        // Method called by Parent-Component
        component.updateMapsAfterRejectedImportDueToInvalidProjectKey();

        // Check if titleAndShortNameOfProgrammingExercises contains the rejected title + shortName
        expect(component.titleAndShortNameOfProgrammingExercises.size).toBe(1);
        expect(component.getBlocklistTitleOfProgrammingExercise(programmingExercise.id!)).toEqual(formerProgrammingExerciseTitle);
        expect(component.titleAndShortNameOfProgrammingExercises.get(programmingExercise.id!)).toEqual([formerProgrammingExerciseTitle, formerProgrammingExerciseShortName]);

        expect(component.selectedExercises.size).toBe(5);
        expect(component.selectedExercises.get(exerciseGroup1)).toEqual(new Set<Exercise>().add(modelingExercise));
        expect(component.selectedExercises.get(exerciseGroup2)).toEqual(new Set<Exercise>().add(textExercise).add(textExercise2));
        // Contains "new" / rejected exercise group
        expect(component.selectedExercises.get(exerciseGroup3Rejected)).toEqual(new Set<Exercise>().add(programmingExerciseRejected));
        expect(component.selectedExercises.get(exerciseGroup4)).toEqual(new Set<Exercise>().add(quizExercise));
        expect(component.selectedExercises.get(exerciseGroup5)).toEqual(new Set<Exercise>().add(fileUploadExercise));

        expect(component.containsProgrammingExercises.size).toBe(5);
        expect(component.containsProgrammingExercises.get(exerciseGroup3Rejected)).toBeTrue();
        expect(component.containsProgrammingExercises.get(exerciseGroup1)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup2)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup4)).toBeFalse();
        expect(component.containsProgrammingExercises.get(exerciseGroup5)).toBeFalse();
    });

    it('should correctly process the selection of a modellingExercise', () => {
        component.exam = exam1;
        component.ngOnInit();

        // Step 1 ( after initialization): Modelling Exercise is selected
        expect(component.exerciseGroupContainsExercises(exerciseGroup1)).toBeTrue();
        expect(component.exerciseGroupContainsProgrammingExercises(exerciseGroup1)).toBeFalse();
        expect(component.exerciseIsSelected(modelingExercise, exerciseGroup1)).toBeTrue();

        // Step 2: Unselect the Modelling Exercise
        component.onSelectExercise(modelingExercise, exerciseGroup1);
        expect(component.exerciseGroupContainsExercises(exerciseGroup1)).toBeFalse();
        expect(component.exerciseGroupContainsProgrammingExercises(exerciseGroup1)).toBeFalse();
        expect(component.exerciseIsSelected(modelingExercise, exerciseGroup1)).toBeFalse();

        // Step 3: Select the Modelling Exercise again
        component.onSelectExercise(modelingExercise, exerciseGroup1);
        expect(component.exerciseGroupContainsExercises(exerciseGroup1)).toBeTrue();
        expect(component.exerciseGroupContainsProgrammingExercises(exerciseGroup1)).toBeFalse();
        expect(component.exerciseIsSelected(modelingExercise, exerciseGroup1)).toBeTrue();
    });

    it('should correctly process the selection of a programmingExercise', () => {
        component.exam = exam1;
        component.ngOnInit();

        // Step 1 ( after initialization): Programming Exercise is selected
        expect(component.exerciseGroupContainsExercises(exerciseGroup3)).toBeTrue();
        expect(component.exerciseGroupContainsProgrammingExercises(exerciseGroup3)).toBeTrue();
        expect(component.exerciseIsSelected(programmingExercise, exerciseGroup3)).toBeTrue();

        // Step 2: Unselect the Programming Exercise
        component.onSelectExercise(programmingExercise, exerciseGroup3);
        expect(component.exerciseGroupContainsExercises(exerciseGroup3)).toBeFalse();
        expect(component.exerciseGroupContainsProgrammingExercises(exerciseGroup3)).toBeTrue();
        expect(component.exerciseIsSelected(programmingExercise, exerciseGroup3)).toBeFalse();

        // Step 3: Select the Programming Exercise again
        component.onSelectExercise(programmingExercise, exerciseGroup3);
        expect(component.exerciseGroupContainsExercises(exerciseGroup3)).toBeTrue();
        expect(component.exerciseGroupContainsProgrammingExercises(exerciseGroup3)).toBeTrue();
        expect(component.exerciseIsSelected(programmingExercise, exerciseGroup3)).toBeTrue();
    });

    it('should correctly return an empty string when titleAndShortNameOfProgrammingExercises do not contain exercise', () => {
        expect(component.getBlocklistTitleOfProgrammingExercise(55)).toBe('');
        expect(component.getBlocklistShortNameOfProgrammingExercise(55)).toBe('');
    });

    it('should correctly map selected Exercises To Exercise Groups', () => {
        component.exam = exam1;
        component.ngOnInit();

        // Initial Case: All Exercise Groups are selected
        expect(component.mapSelectedExercisesToExerciseGroups()).toEqual(exam1.exerciseGroups);

        // Case 1: Two Exercises are unselected, but exerciseGroup2 still contains one exercise (textExercise2)
        component.onSelectExercise(modelingExercise, exerciseGroup1);
        component.onSelectExercise(textExercise, exerciseGroup2);
        expect(component.mapSelectedExercisesToExerciseGroups()).toEqual([exerciseGroup2, exerciseGroup3, exerciseGroup4, exerciseGroup5]);

        // Case 2: Second Exercise is unselected -> exerciseGroup2 should be "deleted"
        component.onSelectExercise(textExercise2, exerciseGroup2);
        expect(component.mapSelectedExercisesToExerciseGroups()).toEqual([exerciseGroup3, exerciseGroup4, exerciseGroup5]);

        // Case 3: Unselect two additional ones
        component.onSelectExercise(programmingExercise, exerciseGroup3);
        component.onSelectExercise(quizExercise, exerciseGroup4);
        expect(component.mapSelectedExercisesToExerciseGroups()).toEqual([exerciseGroup5]);

        // Case 5: Add quiz exercise again
        component.onSelectExercise(quizExercise, exerciseGroup4);
        expect(component.mapSelectedExercisesToExerciseGroups()).toEqual([exerciseGroup4, exerciseGroup5]);

        // Case 6: No Exercise selected
        component.onSelectExercise(quizExercise, exerciseGroup4);
        component.onSelectExercise(fileUploadExercise, exerciseGroup5);
        expect(component.mapSelectedExercisesToExerciseGroups()).toEqual([]);
    });

    it('should correctly validate the user input', () => {
        component.exam = exam1;
        component.ngOnInit();

        // Programming Exercises with this title and short name should not be accepted
        component.titleAndShortNameOfProgrammingExercises.set(programmingExercise.id!, ['rejectedTitle', 'rejectedShortName']);

        expect(component.validateUserInput()).toBeTrue();

        exerciseGroup1.title = undefined;
        expect(component.validateUserInput()).toBeFalse();
        exerciseGroup1.title = 'exerciseGroup1';

        modelingExercise.title = undefined;
        expect(component.validateUserInput()).toBeFalse();
        modelingExercise.title = 'ModelingExercise';

        programmingExercise.title = undefined;
        expect(component.validateUserInput()).toBeFalse();
        // Title Pattern mismatch
        programmingExercise.title = '//';
        expect(component.validateUserInput()).toBeFalse();
        programmingExercise.title = 'rejectedTitle';
        expect(component.validateUserInput()).toBeFalse();
        programmingExercise.title = 'ProgrammingExercise';

        programmingExercise.shortName = undefined;
        expect(component.validateUserInput()).toBeFalse();
        // Too short ( >= 3)
        programmingExercise.shortName = 'AA';
        expect(component.validateUserInput()).toBeFalse();
        // ShortName Pattern mismatch (no leading number)
        programmingExercise.shortName = '9AAA';
        expect(component.validateUserInput()).toBeFalse();
        programmingExercise.shortName = 'rejectedShortName';
        expect(component.validateUserInput()).toBeFalse();
        programmingExercise.shortName = 'prog3';

        expect(component.validateUserInput()).toBeTrue();
    });

    it('should correctly return the Exercise Icon', () => {
        expect(component.getExerciseIcon(modelingExercise.type)).toEqual(faProjectDiagram);
        expect(component.getExerciseIcon(textExercise.type)).toEqual(faFont);
        expect(component.getExerciseIcon(programmingExercise.type)).toEqual(faKeyboard);
        expect(component.getExerciseIcon(quizExercise.type)).toEqual(faCheckDouble);
        expect(component.getExerciseIcon(fileUploadExercise.type)).toEqual(faFileUpload);
    });

    describe('Programming exercise import validation', () => {
        const exerciseGroup6 = { title: 'exerciseGroup6' } as ExerciseGroup;
        const programmingExercise2 = new ProgrammingExercise(undefined, exerciseGroup6);

        programmingExercise2.id = 6;
        programmingExercise2.title = 'ProgrammingExercise';
        programmingExercise2.shortName = 'progEx1';

        const programmingExercise3 = new ProgrammingExercise(undefined, exerciseGroup6);
        programmingExercise3.id = 7;
        programmingExercise3.title = 'ProgrammingExercise';
        programmingExercise3.shortName = 'progEx2';
        exerciseGroup6.exercises = [programmingExercise2, programmingExercise3];

        const exam2 = {
            id: 10,
            exerciseGroups: [exerciseGroup1, exerciseGroup2, exerciseGroup3, exerciseGroup4, exerciseGroup5, exerciseGroup6],
        } as Exam;

        beforeEach(() => {
            component.exam = exam2;
            component.importInSameCourse = false;
            component.ngOnInit();
        });

        afterEach(() => {
            programmingExercise2.title = 'ProgrammingExercise';
            programmingExercise2.shortName = 'progEx1';
            programmingExercise3.title = 'ProgrammingExercise';
            programmingExercise3.shortName = 'progEx2';
        });

        it('should remove duplicated titles when importing', () => {
            expect(programmingExercise.title).toBe('ProgrammingExercise');
            expect(programmingExercise2.title).toBe('');
            expect(programmingExercise3.title).toBe('');
        });

        it.each(['exercisesWithDuplicatedTitles', 'exercisesWithDuplicatedShortNames'])(
            'should check for programming exercise duplicated titles and short names when entering input',
            (attrToCheck) => {
                const duplicatesToCheck = component[attrToCheck];
                const duplicatedTitles = attrToCheck === 'exercisesWithDuplicatedTitles';

                if (duplicatedTitles) {
                    programmingExercise2.title = programmingExercise.title;
                    programmingExercise3.title = programmingExercise.title;
                } else {
                    programmingExercise2.shortName = programmingExercise.shortName;
                    programmingExercise3.shortName = programmingExercise.shortName;
                }

                component.checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(programmingExercise, exerciseGroup3, duplicatedTitles);

                expect(duplicatesToCheck.size).toBe(3);
                expect(duplicatesToCheck.has(programmingExercise.id!)).toBeTrue();
                expect(duplicatesToCheck.has(programmingExercise2.id!)).toBeTrue();
                expect(duplicatesToCheck.has(programmingExercise3.id!)).toBeTrue();
            },
        );

        it.each([
            ['exercisesWithDuplicatedShortNames', false],
            ['exercisesWithDuplicatedShortNames', true],
            ['exercisesWithDuplicatedTitles', false],
            ['exercisesWithDuplicatedTitles', true],
        ])('should remove exercise from duplicates when title / short name is changed', (attrToCheck, additionalDuplicate) => {
            const duplicatesToCheck = component[attrToCheck];
            const duplicatedTitles = attrToCheck === 'exercisesWithDuplicatedTitles';

            // setup duplicates
            duplicatesToCheck.set(programmingExercise.id!, duplicatedTitles ? programmingExercise.title! : programmingExercise.shortName!);
            duplicatesToCheck.set(programmingExercise2.id!, duplicatedTitles ? programmingExercise.title! : programmingExercise.shortName!);
            if (additionalDuplicate) {
                duplicatesToCheck.set(programmingExercise3.id!, duplicatedTitles ? programmingExercise.title! : programmingExercise.shortName!);
            }

            if (duplicatedTitles) {
                programmingExercise2.title = 'new title';
            } else {
                programmingExercise2.shortName = 'new short name';
            }

            component.checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(programmingExercise2, exerciseGroup6, duplicatedTitles);
            expect(duplicatesToCheck.size).toBe(additionalDuplicate ? 2 : 0);
        });

        it.each(['exercisesWithDuplicatedTitles', 'exercisesWithDuplicatedShortNames'])(
            'should check for programming exercise duplicated titles and short names when unselecting exercises',
            (attrToCheck) => {
                const duplicatesToCheck = component[attrToCheck];
                const duplicatedTitles = attrToCheck === 'exercisesWithDuplicatedTitles';

                duplicatesToCheck.set(programmingExercise.id!, duplicatedTitles ? programmingExercise.title! : programmingExercise.shortName!);
                duplicatesToCheck.set(programmingExercise2.id!, duplicatedTitles ? programmingExercise.title! : programmingExercise.shortName!);

                if (duplicatedTitles) {
                    programmingExercise2.title = programmingExercise.title;
                } else {
                    programmingExercise2.shortName = programmingExercise.shortName;
                }

                component.onSelectExercise(programmingExercise2, exerciseGroup6);
                expect(duplicatesToCheck.size).toBe(0);

                component.onSelectExercise(programmingExercise2, exerciseGroup6);
                expect(duplicatesToCheck.size).toBe(2);
                expect(duplicatesToCheck.has(programmingExercise.id!)).toBeTrue();
                expect(duplicatesToCheck.has(programmingExercise2.id!)).toBeTrue();
            },
        );

        it.each(['exercisesWithDuplicatedTitles', 'exercisesWithDuplicatedShortNames'])('should ignore unselected exercises', (attrToCheck) => {
            const duplicatesToCheck = component[attrToCheck];
            const duplicatedTitles = attrToCheck === 'exercisesWithDuplicatedTitles';

            component.selectedExercises.get(exerciseGroup6)?.delete(programmingExercise3);

            if (duplicatedTitles) {
                programmingExercise3.title = programmingExercise.title;
            } else {
                programmingExercise3.shortName = programmingExercise.shortName;
            }

            component.checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(programmingExercise3, exerciseGroup6, duplicatedTitles);
            expect(duplicatesToCheck.size).toBe(0);

            component.checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(programmingExercise, exerciseGroup6, duplicatedTitles);
            expect(duplicatesToCheck.size).toBe(0);
        });
    });
});
