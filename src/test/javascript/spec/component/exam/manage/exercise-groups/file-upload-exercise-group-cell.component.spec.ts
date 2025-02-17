import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

describe('File Upload Exercise Group Cell Component', () => {
    let fixture: ComponentFixture<FileUploadExerciseGroupCellComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExerciseGroupCellComponent);
            });
    });

    it('should display file pattern', () => {
        const exercise: FileUploadExercise = {
            id: 1,
            type: ExerciseType.FILE_UPLOAD,
            filePattern: '*.pdf',
        } as any as FileUploadExercise;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain(exercise.filePattern);
    });

    it('should not display anything for other exercise types', () => {
        const exercise: FileUploadExercise = {
            id: 1,
            type: ExerciseType.TEXT,
            filePattern: '*.pdf',
        } as any as FileUploadExercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toBe('');
    });
});
