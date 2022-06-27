import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';

describe('ProgrammingExercise Re-Evaluate Button Component', () => {
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';

    let comp: ProgrammingExerciseReEvaluateButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseReEvaluateButtonComponent>;
    let gradingService: ProgrammingExerciseGradingService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseReEvaluateButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgModel)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseReEvaluateButtonComponent);
        comp = fixture.componentInstance;
        gradingService = fixture.debugElement.injector.get(ProgrammingExerciseGradingService);

        comp.exercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should reEvaluate exercise', () => {
        jest.spyOn(gradingService, 'reEvaluate');

        const button = fixture.debugElement.nativeElement.querySelector('#re-evaluate-button button');
        button.click();

        expect(gradingService.reEvaluate).toHaveBeenCalledOnce();
        expect(gradingService.reEvaluate).toHaveBeenCalledWith(programmingExercise.id);
    });
});
