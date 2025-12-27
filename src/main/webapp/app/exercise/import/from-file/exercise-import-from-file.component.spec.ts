import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import JSZip from 'jszip';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { UMLDiagramType } from '@ls1intum/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';

describe('ExerciseImportFromFileComponent', () => {
    let component: ExerciseImportFromFileComponent;
    let fixture: ComponentFixture<ExerciseImportFromFileComponent>;
    let activeModal: NgbActiveModal;
    let alertService: AlertService;
    let alertServiceSpy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(HelpIconComponent), ButtonComponent, FaIconComponent],
            declarations: [ExerciseImportFromFileComponent, MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }, MockProvider(FeatureToggleService)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportFromFileComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        alertService = TestBed.inject(AlertService);

        fixture.detectChanges();
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exercise = { id: undefined } as ProgrammingExercise;
        // WHEN
        component.openImport(exercise);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith(exercise);
    });

    // using fakeasync and tick didn't work here, that's why I used whenStable and async
    it.each([ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])(
        'should raise error alert if not supported exercise type',
        async (exerciseType: ExerciseType) => {
            // GIVEN
            const alertServiceSpy = jest.spyOn(alertService, 'error');
            component.exerciseType = exerciseType;
            fixture.changeDetectorRef.detectChanges();
            component.fileForImport = (await generateValidTestZipFileWithExerciseType(exerciseType)) as File;
            await fixture.whenStable();
            fixture.changeDetectorRef.detectChanges();
            // WHEN
            await component.uploadExercise();
            await fixture.whenStable();
            fixture.changeDetectorRef.detectChanges();
            // THEN
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.notSupportedExerciseType', { exerciseType: exerciseType });
            expect(component.exercise).toBeUndefined();
        },
    );

    it('should raise error alert if not one json file at the root level', async () => {
        //
        alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.changeDetectorRef.detectChanges();
        component.fileForImport = (await generateTestZipFileWithoutJsonFile()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
        component.fileForImport = (await generateTestZipFileWithTwoJsonFiles()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
    });

    it('should raise error alert if exercise type does not match exercise type of imported exercise', async () => {
        alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.changeDetectorRef.detectChanges();
        component.exerciseType = ExerciseType.TEXT;
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File;
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
    });

    it('should set exercise attributes and open import dialog', async () => {
        const openImportSpy = jest.spyOn(component, 'openImport');
        component.exerciseType = ExerciseType.PROGRAMMING;
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File;
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // THEN
        expect(component.exercise.id).toBeUndefined();
        expect(component.exercise.zipFileForImport).toBe(component.fileForImport);
        expect(component.exercise).toMatchObject({ type: 'programming', id: undefined, title: 'Test exercise' });
        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(component.exercise);
    });

    it('should load build configs in the old format', async () => {
        component.exerciseType = ExerciseType.PROGRAMMING;
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING, true)) as File;
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // THEN
        expect((component.exercise as ProgrammingExercise).buildConfig).toBeDefined();
    });

    it('should disable upload button as long as no file is selected', () => {
        component.fileForImport = undefined;
        fixture.changeDetectorRef.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled).toBeTrue();
    });

    it('should enable upload button once file is selected', () => {
        component.fileForImport = new File([''], 'test.zip', { type: 'application/zip' });
        fixture.changeDetectorRef.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled).toBeFalse();
    });

    async function assertErrorAlertIsRaisedWithoutOneValidJsonFile() {
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.noExerciseDetailsJsonAtRootLevel');
        alertServiceSpy.mockClear();
    }

    it('should raise error alert when more than one file is selected', () => {
        // GIVEN
        alertServiceSpy = jest.spyOn(alertService, 'error');
        const file = new File([''], 'test.zip', { type: 'application/zip' });
        const file2 = new File([''], 'test2.zip', { type: 'application/zip' });
        const event = {
            target: {
                files: [file, file2],
            },
        };
        // WHEN
        component.setFileForExerciseImport(event);
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.fileCountError');
        expect(component.fileForImport).toBeUndefined();
    });

    it('should raise error alert when not a zip file is selected', () => {
        // GIVEN
        alertServiceSpy = jest.spyOn(alertService, 'error');
        const file = new File([''], 'test.txt', { type: 'application/zip' });
        const event = {
            target: {
                files: [file],
            },
        };
        // WHEN
        component.setFileForExerciseImport(event);
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.fileExtensionError');
        expect(component.fileForImport).toBeUndefined();
    });

    it('should set file for import if no errors occur', () => {
        // GIVEN
        alertServiceSpy = jest.spyOn(alertService, 'error');
        const file = new File([''], 'test.zip', { type: 'application/zip' });
        const event = {
            target: {
                files: [file],
            },
        };
        // WHEN
        component.setFileForExerciseImport(event);
        // THEN
        expect(component.fileForImport).toEqual(file);
    });

    // Verifies that exercise categories are correctly parsed from stringified JSON entries during import.
    it('should correctly parse exercise categories during import', async () => {
        // GIVEN
        component.exerciseType = ExerciseType.PROGRAMMING;

        const progEx = new ProgrammingExercise(undefined, undefined);
        progEx.id = 999;
        progEx.title = 'Category test exercise';
        progEx.type = ExerciseType.PROGRAMMING;

        (progEx as any).categories = ['{"color":"#0d3cc2","category":"Testing categories"}', '{"color":"#691b0b","category":"Issue"}'];

        const zip = new JSZip();
        zip.file('exercise.json', JSON.stringify(progEx));
        const zipBlob = await zip.generateAsync({ type: 'blob' });

        component.fileForImport = zipBlob as any;
        const openImportSpy = jest.spyOn(component, 'openImport');
        await fixture.whenStable();

        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();

        // THEN
        const importedEx = component.exercise as ProgrammingExercise;
        expect(importedEx).toBeDefined();
        expect(importedEx.title).toBe('Category test exercise');
        expect(importedEx.type).toBe(ExerciseType.PROGRAMMING);

        expect(importedEx.categories!).toHaveLength(2);

        expect(importedEx.categories![0]).toBeInstanceOf(ExerciseCategory);
        expect(importedEx.categories![0].category).toBe('Testing categories');
        expect(importedEx.categories![0].color).toBe('#0d3cc2');

        expect(importedEx.categories![1]).toBeInstanceOf(ExerciseCategory);
        expect(importedEx.categories![1].category).toBe('Issue');
        expect(importedEx.categories![1].color).toBe('#691b0b');

        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(importedEx);
    });

    // Ensures backward compatibility when old exercise JSONs do not contain 'categories' field.
    // parseExerciseCategories() should safely handle undefined categories without throwing errors.
    it('should import exercise correctly even if categories are missing', async () => {
        // GIVEN
        component.exerciseType = ExerciseType.PROGRAMMING;
        const progEx = new ProgrammingExercise(undefined, undefined);
        progEx.id = 555;
        progEx.title = 'No category test exercise';
        progEx.type = ExerciseType.PROGRAMMING;

        delete (progEx as any).categories;

        const zip = new JSZip();
        zip.file('exercise.json', JSON.stringify(progEx));
        const zipBlob = await zip.generateAsync({ type: 'blob' });

        component.fileForImport = zipBlob as any;
        const openImportSpy = jest.spyOn(component, 'openImport');
        await fixture.whenStable();

        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();

        // THEN
        const importedEx = component.exercise as ProgrammingExercise;
        expect(importedEx).toBeDefined();
        expect(importedEx.title).toBe('No category test exercise');
        expect(importedEx.type).toBe(ExerciseType.PROGRAMMING);

        expect(importedEx.categories).toBeUndefined();

        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(importedEx);
    });
});

async function generateValidTestZipFileWithExerciseType(exerciseType: ExerciseType, oldStyleBuildConfig: boolean = false): Promise<Blob> {
    const zip = new JSZip();
    let exercise;

    switch (exerciseType) {
        case ExerciseType.MODELING:
            exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, undefined, undefined);
            break;
        case ExerciseType.FILE_UPLOAD:
            exercise = new FileUploadExercise(undefined, undefined);
            break;
        case ExerciseType.TEXT:
            exercise = new TextExercise(undefined, undefined);
            break;
        case ExerciseType.QUIZ:
            exercise = new QuizExercise(undefined, undefined);
            break;
        case ExerciseType.PROGRAMMING:
            exercise = new ProgrammingExercise(undefined, undefined);
            if (oldStyleBuildConfig) {
                // old format: build config is not a separate object, but the attributes are part of the exercise itself
                // simplification: we just remove it completely here, the component will generate a new default config object
                exercise.buildConfig = undefined;
            }
            break;
        default:
            throw new Error('Unexpected exercise type');
    }
    exercise.id = 1246;
    exercise.title = 'Test exercise';
    const zipFile = zip.file('test.json', JSON.stringify(exercise));
    return zipFile.generateAsync({ type: 'blob' });
}

async function generateTestZipFileWithoutJsonFile(): Promise<Blob> {
    const zip = new JSZip();
    const zipFile = zip.file('test.txt', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    return zipFile.generateAsync({ type: 'blob' });
}

async function generateTestZipFileWithTwoJsonFiles(): Promise<Blob> {
    const zip = new JSZip();
    const zipFile = zip
        .file('test.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }')
        .file('test2.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    return zipFile.generateAsync({ type: 'blob' });
}
