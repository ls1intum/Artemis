import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExamExerciseImportDialogComponent } from 'app/exam/manage/exercise-groups/exercise-import-dialog/exam-exercise-import-dialog.component';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('ExamExerciseImportDialogComponent', () => {
    let fixture: ComponentFixture<ExamExerciseImportDialogComponent>;
    let component: ExamExerciseImportDialogComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamExerciseImportDialogComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExamExerciseImportDialogComponent, {
                remove: { imports: [ExerciseImportComponent, ExerciseImportFromFileComponent, ArtemisTranslatePipe] },
                add: { imports: [MockComponent(ExerciseImportComponent), MockComponent(ExerciseImportFromFileComponent), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExamExerciseImportDialogComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        document.body.querySelectorAll('.cdk-overlay-container').forEach((container) => container.replaceChildren());
    });

    function open(exerciseType: ExerciseType): void {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        fixture.componentRef.setInput('courseId', 3);
        component.visible.set(true);
        fixture.detectChanges();
    }

    it.each([
        [ExerciseType.TEXT, 'artemisApp.textExercise.home.importLabel'],
        [ExerciseType.FILE_UPLOAD, 'artemisApp.fileUploadExercise.home.importLabel'],
        [ExerciseType.PROGRAMMING, 'artemisApp.programmingExercise.home.importLabel'],
    ])('should use the import label of the %s exercise type as header', (exerciseType, headerKey) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        expect(component['headerKey']()).toBe(headerKey);
    });

    it.each([ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.TEXT])('should show the existing %s exercises first and the file import after switching the tab', (type) => {
        open(type);
        expect(document.body.querySelector('[data-testid="exercise-import-tabs"]')).not.toBeNull();
        expect(document.body.querySelector('jhi-exercise-import')).not.toBeNull();
        expect(document.body.querySelector('jhi-exercise-import-from-file')).toBeNull();

        component['selectTab']('file');
        fixture.detectChanges();

        expect(document.body.querySelector('jhi-exercise-import')).toBeNull();
        expect(document.body.querySelector('jhi-exercise-import-from-file')).not.toBeNull();
    });

    it('should start on the existing exercises again after the dialog was closed on the file tab', () => {
        open(ExerciseType.PROGRAMMING);
        component['selectTab']('file');
        fixture.detectChanges();

        component.visible.set(false);
        fixture.detectChanges();
        component.visible.set(true);
        fixture.detectChanges();

        expect(document.body.querySelector('jhi-exercise-import')).not.toBeNull();
        expect(document.body.querySelector('jhi-exercise-import-from-file')).toBeNull();
    });
});
