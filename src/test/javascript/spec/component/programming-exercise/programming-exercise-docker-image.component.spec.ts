import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseDockerImageComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-docker-image/programming-exercise-docker-image.component';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseDockerImageComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseDockerImageComponent],
            providers: [],
        })
            .compileComponents()
            .then();

        const fixture = TestBed.createComponent(ProgrammingExerciseDockerImageComponent);
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
