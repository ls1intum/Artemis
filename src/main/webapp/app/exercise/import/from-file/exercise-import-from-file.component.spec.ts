import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumAetUiDialogRef } from '@tumaet/ui-angular';
import { MockComponent, MockProvider } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ZipBuilder } from 'app/foundation/util/zip.util';
import { UMLDiagramType } from '@tumaet/apollon';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import dayjs from 'dayjs/esm';

describe('ExerciseImportFromFileComponent', () => {
    let component: ExerciseImportFromFileComponent;
    let fixture: ComponentFixture<ExerciseImportFromFileComponent>;
    let dialogRef: TumAetUiDialogRef;
    let alertService: AlertService;
    let fileUploaderService: FileUploaderService;
    let alertServiceSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(ButtonComponent), MockComponent(HelpIconComponent)],
            providers: [
                MockProvider(TumAetUiDialogRef),
                MockProvider(AlertService),
                MockProvider(FileUploaderService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportFromFileComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(TumAetUiDialogRef);
        alertService = TestBed.inject(AlertService);
        fileUploaderService = TestBed.inject(FileUploaderService);
    });

    it('should close the dialog with result', () => {
        const dialogRefSpy = vi.spyOn(dialogRef, 'close');
        const exercise = { id: undefined } as ProgrammingExercise;

        component.openImport(exercise);

        expect(dialogRefSpy).toHaveBeenCalledOnce();
        expect(dialogRefSpy).toHaveBeenCalledWith(exercise);
    });

    it.each([ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])(
        'should open the editor of a new %s exercise with the settings from the file',
        async (exerciseType: ExerciseType) => {
            const openImportSpy = vi.spyOn(component, 'openImport');
            fixture.componentRef.setInput('exerciseType', exerciseType);
            component.fileForImport.set((await generateValidTestZipFileWithExerciseType(exerciseType)) as File);

            await component.uploadExercise();

            expect(component.exercise).toMatchObject({ type: exerciseType, id: undefined, title: 'Test exercise', channelName: undefined, dueDate: undefined });
            expect(component.exercise?.zipFileForImport).toBeUndefined();
            expect(component.exercise?.gradingCriteria?.[0].id).toBeUndefined();
            expect(component.exercise?.gradingCriteria?.[0].structuredGradingInstructions[0].id).toBeUndefined();
            expect(openImportSpy).toHaveBeenCalledExactlyOnceWith(component.exercise);
        },
    );

    it('should upload the problem statement images and point the problem statement at the copies', async () => {
        const uploadSpy = vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockResolvedValue({ path: '/api/core/files/markdown/Markdown_new.png' });
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        fixture.componentRef.setInput('courseId', 42);
        const exercise = new TextExercise(undefined, undefined);
        exercise.problemStatement =
            'See ![diagram](/api/core/files/markdown/Markdown_old.png) and <img src="/api/core/files/markdown/Markdown_old.png"> and <img src=/api/core/files/markdown/Markdown_old.png> and ![x](https://source.example/tenant=old/api/core/files/markdown/Markdown_old.png) and <a href=/api/core/files/markdown/Markdown_old.png>full size</a> and ![y](tenant=old/api/core/files/markdown/Markdown_old.png)';
        const zip = new ZipBuilder();
        zip.file('Exercise-Details-Test.json', JSON.stringify(exercise));
        zip.file('files/Markdown_old.png', 'image');
        zip.file('files/Unused.png', 'image');
        component.fileForImport.set((await zip.generateBlob()) as File);

        await component.uploadExercise();

        expect(uploadSpy).toHaveBeenCalledOnce();
        expect(uploadSpy.mock.calls[0][0].name).toBe('Markdown_old.png');
        expect(uploadSpy.mock.calls[0][1]).toBe(42);
        expect(component.exercise?.problemStatement).toBe(
            'See ![diagram](/api/core/files/markdown/Markdown_new.png) and <img src="/api/core/files/markdown/Markdown_new.png"> and <img src=/api/core/files/markdown/Markdown_new.png> and ![x](/api/core/files/markdown/Markdown_new.png) and <a href=/api/core/files/markdown/Markdown_new.png>full size</a> and ![y](/api/core/files/markdown/Markdown_new.png)',
        );
    });

    it('should upload and relink every image when several share one line', async () => {
        const uploadSpy = vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockImplementation(async (file: File) => ({ path: `/api/core/files/markdown/new_${file.name}` }));
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        const exercise = new TextExercise(undefined, undefined);
        exercise.problemStatement = '![a](/api/core/files/markdown/A.png) ![b](/api/core/files/markdown/B.png) ![c](/api/core/files/markdown/A.png2)';
        const zip = new ZipBuilder();
        zip.file('Exercise-Details-Test.json', JSON.stringify(exercise));
        zip.file('files/A.png', 'a');
        zip.file('files/B.png', 'b');
        component.fileForImport.set((await zip.generateBlob()) as File);

        await component.uploadExercise();

        expect(uploadSpy).toHaveBeenCalledTimes(2);
        expect(component.exercise?.problemStatement).toBe(
            '![a](/api/core/files/markdown/new_A.png) ![b](/api/core/files/markdown/new_B.png) ![c](/api/core/files/markdown/A.png2)',
        );
    });

    it('should not open the editor if a problem statement image cannot be uploaded', async () => {
        vi.spyOn(fileUploaderService, 'uploadMarkdownFile').mockRejectedValue(new Error('upload failed'));
        const alertSpy = vi.spyOn(alertService, 'error');
        const openImportSpy = vi.spyOn(component, 'openImport');
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        const exercise = new TextExercise(undefined, undefined);
        exercise.problemStatement = '![diagram](/api/core/files/markdown/Markdown_old.png)';
        const zip = new ZipBuilder();
        zip.file('Exercise-Details-Test.json', JSON.stringify(exercise));
        zip.file('files/Markdown_old.png', 'image');
        component.fileForImport.set((await zip.generateBlob()) as File);

        await component.uploadExercise();

        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.importFromFile.imageUploadFailed', { fileName: 'Markdown_old.png' });
        expect(openImportSpy).not.toHaveBeenCalled();
    });

    it('should read the file only once when the import is clicked twice', async () => {
        const openImportSpy = vi.spyOn(component, 'openImport');
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        component.fileForImport.set((await generateValidTestZipFileWithExerciseType(ExerciseType.TEXT)) as File);

        await Promise.all([component.uploadExercise(), component.uploadExercise()]);

        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(component.isImporting()).toBe(false);
    });

    it('should hand the drag and drop images of a quiz to the question editor', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.QUIZ);
        component.fileForImport.set((await generateQuizZip(true)) as File);

        await component.uploadExercise();

        const question = (component.exercise as QuizExercise).quizQuestions![0] as DragAndDropQuestion;
        expect(question.backgroundFilePath).toBe('background.png');
        expect(question.dragItems![0].pictureFilePath).toBe('item.png');
        expect([...question.importedFiles!.keys()]).toEqual(['background.png', 'item.png']);
    });

    it('should not open the editor if a drag and drop image is missing', async () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        const openImportSpy = vi.spyOn(component, 'openImport');
        fixture.componentRef.setInput('exerciseType', ExerciseType.QUIZ);
        component.fileForImport.set((await generateQuizZip(false)) as File);

        await component.uploadExercise();

        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.importFromFile.missingImage', { fileName: 'item.png' });
        expect(openImportSpy).not.toHaveBeenCalled();
    });

    it('should raise error alert if not one json file at the root level', async () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        component.fileForImport.set((await generateTestZipFileWithoutJsonFile()) as File);
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
        component.fileForImport.set((await generateTestZipFileWithTwoJsonFiles()) as File);
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
    });

    it('should raise error alert if exercise type does not match exercise type of imported exercise', async () => {
        alertServiceSpy = vi.spyOn(alertService, 'error');
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        component.fileForImport.set((await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File);

        await component.uploadExercise();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
    });

    it('should set exercise attributes and open import dialog', async () => {
        const openImportSpy = vi.spyOn(component, 'openImport');
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        component.fileForImport.set((await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File);

        await component.uploadExercise();

        expect(component.exercise?.id).toBeUndefined();
        expect(component.exercise?.zipFileForImport).toBe(component.fileForImport());
        expect(component.exercise).toMatchObject({ type: 'programming', id: undefined, title: 'Test exercise' });
        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(component.exercise);
    });

    it('should load build configs in the old format', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        component.fileForImport.set((await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING, true)) as File);

        await component.uploadExercise();

        expect((component.exercise as ProgrammingExercise).buildConfig).toBeDefined();
    });

    it('should disable upload button as long as no file is selected', () => {
        component.fileForImport.set(undefined);
        fixture.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled()).toBe(true);
    });

    it('should enable upload button once file is selected', () => {
        component.fileForImport.set(new File([''], 'test.zip', { type: 'application/zip' }));
        fixture.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled()).toBe(false);
    });

    async function assertErrorAlertIsRaisedWithoutOneValidJsonFile() {
        await component.uploadExercise();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.noExerciseDetailsJson');
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
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.fileCountError');
        expect(component.fileForImport()).toBeUndefined();
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
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.fileTypeNotSupportedError');
        expect(component.fileForImport()).toBeUndefined();
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

        expect(component.fileForImport()).toEqual(file);
    });

    it('should correctly parse exercise categories during import', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);

        const progEx = new ProgrammingExercise(undefined, undefined);
        progEx.id = 999;
        progEx.title = 'Category test exercise';
        progEx.type = ExerciseType.PROGRAMMING;

        (progEx as any).categories = ['{"color":"#0d3cc2","category":"Testing categories"}', '{"color":"#691b0b","category":"Issue"}'];

        const zip = new ZipBuilder();
        zip.file('exercise.json', JSON.stringify(progEx));
        const zipBlob = await zip.generateBlob();

        component.fileForImport.set(zipBlob as any);
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

        const zip = new ZipBuilder();
        zip.file('exercise.json', JSON.stringify(progEx));
        const zipBlob = await zip.generateBlob();

        component.fileForImport.set(zipBlob as any);
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
    const zip = new ZipBuilder();
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
    exercise.channelName = 'exercise-test';
    exercise.dueDate = dayjs();
    exercise.gradingCriteria = [
        { id: 3, title: 'Criterion', structuredGradingInstructions: [{ id: 4, credits: 1, gradingScale: '', instructionDescription: '', feedback: '', usageCount: 0 }] },
    ];
    zip.file('test.json', JSON.stringify(exercise));
    return zip.generateBlob();
}

async function generateTestZipFileWithoutJsonFile(): Promise<Blob> {
    const zip = new ZipBuilder();
    zip.file('test.txt', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    return zip.generateBlob();
}

async function generateTestZipFileWithTwoJsonFiles(): Promise<Blob> {
    const zip = new ZipBuilder();
    zip.file('test.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    zip.file('test2.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    return zip.generateBlob();
}

/** A quiz with one drag and drop question, exported with its background and, if requested, its drag item picture. */
async function generateQuizZip(withDragItemPicture: boolean): Promise<Blob> {
    const question = new DragAndDropQuestion();
    question.id = 7;
    question.backgroundFilePath = '/api/core/files/drag-and-drop/backgrounds/7/background.png';
    const dragItem = new DragItem();
    dragItem.pictureFilePath = '/api/core/files/drag-and-drop/drag-items/9/item.png';
    question.dragItems = [dragItem];
    const quiz = new QuizExercise(undefined, undefined);
    quiz.quizQuestions = [question];
    const zip = new ZipBuilder();
    zip.file('Exercise-Details-Quiz.json', JSON.stringify(quiz));
    zip.file('images-for-drag-and-drop-question-7/background.png', 'background');
    if (withDragItemPicture) {
        zip.file('images-for-drag-and-drop-question-7/item.png', 'item');
    }
    return zip.generateBlob();
}
