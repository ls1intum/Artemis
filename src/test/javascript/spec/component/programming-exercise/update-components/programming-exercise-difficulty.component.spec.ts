import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-difficulty.component';

describe('ProgrammingExerciseDifficultyComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseDifficultyComponent>;
    let comp: ProgrammingExerciseDifficultyComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseDifficultyComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseDifficultyComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    }));
});
