import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

import { ProgrammingExerciseInstructorExerciseDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-exercise-download.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent } from 'ng-mocks';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';

describe('ProgrammingExerciseInstructorExerciseDownloadComponent', () => {
    let component: ProgrammingExerciseInstructorExerciseDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorExerciseDownloadComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseInstructorExerciseDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorExerciseDownloadComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ProgrammingExerciseService);
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should not download when there is no exercise', () => {
        const spy = jest.spyOn(service, 'exportInstructorExercise');
        component.exportExercise();
        expect(spy).not.toHaveBeenCalled();
        spy.mockRestore();
    });

    it('should download the exercise', () => {
        const spy = jest.spyOn(service, 'exportInstructorExercise');
        component.exerciseId = 1;
        component.exportExercise();
        expect(spy).toHaveBeenCalledOnce();
        spy.mockRestore();
    });
});
