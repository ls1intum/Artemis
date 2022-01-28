import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

describe('File Upload Exercise Group Cell Component', () => {
    let comp: FileUploadExerciseGroupCellComponent;
    let fixture: ComponentFixture<FileUploadExerciseGroupCellComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FileUploadExerciseGroupCellComponent, TranslatePipeMock],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExerciseGroupCellComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should display file pattern', () => {
        const exercise: FileUploadExercise = {
            id: 1,
            type: ExerciseType.FILE_UPLOAD,
            filePattern: '*.pdf',
        } as any as FileUploadExercise;
        comp.exercise = exercise;

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain(exercise.filePattern);
    });

    it('should not display anything for other exercise types', () => {
        comp.exercise = {
            id: 1,
            type: ExerciseType.TEXT,
            filePattern: '*.pdf',
        } as any as FileUploadExercise;
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toBe('');
    });
});
