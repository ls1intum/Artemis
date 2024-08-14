import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseBuildConfigurationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, ArtemisProgrammingExerciseUpdateModule],
            declarations: [ProgrammingExerciseBuildConfigurationComponent],
            providers: [],
        })
            .compileComponents()
            .then();

        const fixture = TestBed.createComponent(ProgrammingExerciseBuildConfigurationComponent);
        comp = fixture.componentInstance;

        comp.dockerImage = 'testImage';
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should update docker image', () => {
        expect(comp.dockerImage).toBe('testImage');
        comp.dockerImageChange.subscribe((value) => expect(value).toBe('newImage'));
        comp.dockerImageChange.emit('newImage');
    });
});
