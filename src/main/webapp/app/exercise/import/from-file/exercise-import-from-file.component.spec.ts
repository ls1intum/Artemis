import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockComponent, MockProvider } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import JSZip from 'jszip';
import { UMLDiagramType } from '@tumaet/apollon';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ExerciseImportFromFileComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExerciseImportFromFileComponent;
    let fixture: ComponentFixture<ExerciseImportFromFileComponent>;
    let dialogRef: DynamicDialogRef;
    let alertService: AlertService;
    let alertServiceSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(ButtonComponent), MockComponent(HelpIconComponent)],
            providers: [MockProvider(DynamicDialogRef), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportFromFileComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
        alertService = TestBed.inject(AlertService);
    });

    it('should close the dialog with result', () => {
        const dialogRefSpy = vi.spyOn(dialogRef, 'close');
        const exercise = { id: undefined } as ProgrammingExercise;

        component.openImport(exercise);

        expect(dialogRefSpy).toHaveBeenCalledOnce();
        expect(dialogRefSpy).toHaveBeenCalledWith(exercise);
    });

    it.each([ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])(
        'should raise error alert if not supported exercise type',
        async (exerciseType: ExerciseType) => {
            const alertServiceSpy = vi.spyOn(alertService, 'error');
            fixture.componentRef.setInput('exerciseType', exerciseType);
            component.fileForImport = (await generateValidTestZipFileWithExerciseType(exerciseType)) as File;

            await component.uploadExercise();

            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.notSupportedExerciseType', { exerciseType });
            expect(component.exercise).toBeUndefined();
        },
    );

    it('should raise error alert if not one json file at the root level', async () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        component.fileForImport = (await generateTestZipFileWithoutJsonFile()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
        component.fileForImport = (await generateTestZipFileWithTwoJsonFiles()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
    });

    it('should raise error alert if exercise type does not match exercise type of imported exercise', async () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File;

        await component.uploadExercise();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
    });

    it('should set exercise attributes and open import dialog', async () => {
        const openImportSpy = vi.spyOn(component, 'openImport');
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File;

        await component.uploadExercise();

        expect(component.exercise?.id).toBeUndefined();
        expect(component.exercise?.zipFileForImport).toBe(component.fileForImport);
        expect(component.exercise).toMatchObject({ type: 'programming', id: undefined, title: 'Test exercise' });
        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(component.exercise);
    });

    it('should load build configs in the old format', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING, true)) as File;

        await component.uploadExercise();

        expect((component.exercise as ProgrammingExercise).buildConfig).toBeDefined();
    });

    it('should disable upload button as long as no file is selected', () => {
        component.fileForImport = undefined;
        fixture.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled()).toBe(true);
    });

    it('should enable upload button once file is selected', () => {
        component.fileForImport = new File([''], 'test.zip', { type: 'application/zip' });
        fixture.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled()).toBe(false);
    });

    async function assertErrorAlertIsRaisedWithoutOneValidJsonFile() {
        await component.uploadExercise();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.noExerciseDetailsJsonAtRootLevel');
        alertServiceSpy.mockClear();
    }

    it('should raise error alert when more than one file is selected', () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        const file = new File([''], 'test.zip', { type: 'application/zip' });
        const file2 = new File([''], 'test2.zip', { type: 'application/zip' });
        const event = {
            target: {
                files: [file, file2],
            },
        } as unknown as Event;

        component.setFileForExerciseImport(event);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.fileCountError');
        expect(component.fileForImport).toBeUndefined();
    });

    it('should raise error alert when not a zip file is selected', () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        const file = new File([''], 'test.txt', { type: 'application/zip' });
        const event = {
            target: {
                files: [file],
            },
        } as unknown as Event;

        component.setFileForExerciseImport(event);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.fileExtensionError');
        expect(component.fileForImport).toBeUndefined();
    });

    it('should set file for import if no errors occur', () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        const file = new File([''], 'test.zip', { type: 'application/zip' });
        const event = {
            target: {
                files: [file],
            },
        } as unknown as Event;

        component.setFileForExerciseImport(event);

        expect(component.fileForImport).toEqual(file);
    });

    it('should correctly parse exercise categories during import', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);

        const progEx = new ProgrammingExercise(undefined, undefined);
        progEx.id = 999;
        progEx.title = 'Category test exercise';
        progEx.type = ExerciseType.PROGRAMMING;

        (progEx as any).categories = ['{"color":"#0d3cc2","category":"Testing categories"}', '{"color":"#691b0b","category":"Issue"}'];

        const zip = new JSZip();
        zip.file('exercise.json', JSON.stringify(progEx));
        const zipBlob = await zip.generateAsync({ type: 'blob' });

        component.fileForImport = zipBlob as any;
        const openImportSpy = vi.spyOn(component, 'openImport');

        await component.uploadExercise();

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

    it('should import exercise correctly even if categories are missing', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        const progEx = new ProgrammingExercise(undefined, undefined);
        progEx.id = 555;
        progEx.title = 'No category test exercise';
        progEx.type = ExerciseType.PROGRAMMING;

        delete (progEx as any).categories;

        const zip = new JSZip();
        zip.file('exercise.json', JSON.stringify(progEx));
        const zipBlob = await zip.generateAsync({ type: 'blob' });

        component.fileForImport = zipBlob as any;
        const openImportSpy = vi.spyOn(component, 'openImport');

        await component.uploadExercise();

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
