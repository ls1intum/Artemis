import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseImportFromFileComponent } from 'app/exercises/shared/import/from-file/exercise-import-from-file.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import JSZip from 'jszip';

describe('ExerciseImportFromFileComponent', () => {
    let component: ExerciseImportFromFileComponent;
    let fixture: ComponentFixture<ExerciseImportFromFileComponent>;
    let activeModal: NgbActiveModal;
    let alertService: AlertService;
    let alertServiceSpy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(HelpIconComponent), MockComponent(ButtonComponent)],
            declarations: [ExerciseImportFromFileComponent, MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportFromFileComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        alertService = TestBed.inject(AlertService);

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
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
    it('should raise error alert if not supported exercise type', async () => {
        // GIVEN
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.exerciseType = ExerciseType.TEXT;
        fixture.detectChanges();
        component.fileForImport = (await generateValidTestZipFileWithNotSupportedExerciseType()) as File;
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.notSupportedExerciseType', { exerciseType: ExerciseType.TEXT });
        expect(component.exercise).toBeUndefined();
    });
    it('should raise error alert if not one json file at the root level', async () => {
        //
        alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.detectChanges();
        component.fileForImport = (await generateTestZipFileWithoutJsonFile()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
        component.fileForImport = (await generateTestZipFileWithTwoJsonFiles()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
    });
    it('should raise error alert if exercise type does not match exercise type of imported exercise', async () => {
        alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.detectChanges();
        component.exerciseType = ExerciseType.TEXT;
        component.fileForImport = (await generateValidTestZipFile()) as File;
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
    });
    it.each([ProgrammingLanguage.SWIFT, ProgrammingLanguage.C, ProgrammingLanguage.EMPTY])(
        'should raise error alert when programming language or exercise type is not supported',
        async (programmingLanguage) => {
            alertServiceSpy = jest.spyOn(alertService, 'error');
            fixture.detectChanges();
            component.exerciseType = ExerciseType.PROGRAMMING;
            component.fileForImport = (await generateValidTestZipWithLanguage(programmingLanguage)) as File;
            await fixture.whenStable();
            fixture.detectChanges();
            // WHEN
            await component.uploadExercise();
            await fixture.whenStable();
            fixture.detectChanges();
            // THEN
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            if (programmingLanguage === ProgrammingLanguage.C) {
                expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.GccNotSupportedForC');
            } else {
                expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.notSupportedProgrammingLanguage', {
                    programmingLanguage: programmingLanguage,
                });
            }
        },
    );
    it('should set exercise attributes and open import dialog', async () => {
        const openImportSpy = jest.spyOn(component, 'openImport');
        component.exerciseType = ExerciseType.PROGRAMMING;
        component.fileForImport = (await generateValidTestZipFile()) as File;
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
        // THEN
        expect(component.exercise.id).toBeUndefined();
        expect(component.exercise.zipFileForImport).toBe(component.fileForImport);
        expect(component.exercise).toMatchObject({ type: 'programming', id: undefined, title: 'Test exercise' });
        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(component.exercise);
    });
    it('should disable upload button as long as no file is selected', () => {
        component.fileForImport = undefined;
        fixture.detectChanges();
        const uploadButton: HTMLButtonElement = fixture.debugElement.nativeElement.querySelector('#upload-exercise-btn');
        //that's the only way we can access the disabled property of the button, the standard way doesn't work
        // @ts-ignore
        expect(JSON.parse(uploadButton.attributes.getNamedItem('ng-reflect-disabled').value)).toBeTrue();
    });
    it('should enable upload button once file is selected', () => {
        component.fileForImport = new File([''], 'test.zip', { type: 'application/zip' });
        fixture.detectChanges();
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#upload-exercise-btn');
        //that's the only way we can access the disabled property of the button, the standard way doesn't work
        // @ts-ignore
        expect(JSON.parse(uploadButton.attributes.getNamedItem('ng-reflect-disabled').value)).toBeFalse();
    });

    async function assertErrorAlertIsRaisedWithoutOneValidJsonFile() {
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
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
});

async function generateValidTestZipFile(): Promise<Blob> {
    const zip = new JSZip();
    const zipFile = zip.file('test.json', '{ "type": "programming", "id": 1246, "title": "Test exercise" }');
    return zipFile.generateAsync({ type: 'blob' });
}

async function generateValidTestZipWithLanguage(language: ProgrammingLanguage): Promise<Blob> {
    const zip = new JSZip();
    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    programmingExercise.programmingLanguage = language;
    programmingExercise.id = 1246;
    programmingExercise.title = 'Test exercise';
    if (language === ProgrammingLanguage.C) {
        programmingExercise.projectType = ProjectType.GCC;
    }
    const zipFile = zip.file('test.json', JSON.stringify(programmingExercise));
    return zipFile.generateAsync({ type: 'blob' });
}

async function generateValidTestZipFileWithNotSupportedExerciseType(): Promise<Blob> {
    const zip = new JSZip();
    const zipFile = zip.file('test.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
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
