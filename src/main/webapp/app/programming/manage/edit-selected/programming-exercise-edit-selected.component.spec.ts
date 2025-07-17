import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/storage/local-storage.service';
import { SessionStorageService } from 'app/shared/storage/session-storage.service';
import { of, throwError } from 'rxjs';

import dayjs from 'dayjs/esm';

import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/edit-selected/programming-exercise-edit-selected.component';
import { MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('ProgrammingExercise Edit Selected Component', () => {
    let comp: ProgrammingExerciseEditSelectedComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditSelectedComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                MockProvider(NgbActiveModal),
                provideHttpClient(),
            ],
        })
            .overrideTemplate(ProgrammingExerciseEditSelectedComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseEditSelectedComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
    });

    describe('saveAll', () => {
        it('should update each selected exercise', fakeAsync(() => {
            // GIVEN
            // the exercise containing the values to update the selected ones
            const newProgrammingExercise = new ProgrammingExercise(new Course(), undefined);
            newProgrammingExercise.releaseDate = dayjs();
            newProgrammingExercise.dueDate = dayjs().add(7, 'days');
            const selectedProgrammingExercises = [];
            const entityOne = new ProgrammingExercise(new Course(), undefined);
            entityOne.id = 123;
            entityOne.releaseDate = dayjs().add(1, 'days');
            const entityTwo = new ProgrammingExercise(new Course(), undefined);
            entityTwo.id = 123;
            entityTwo.releaseDate = dayjs().add(1, 'days');
            selectedProgrammingExercises.push(entityOne);
            selectedProgrammingExercises.push(entityTwo);
            comp.selectedProgrammingExercises = selectedProgrammingExercises;
            comp.newProgrammingExercise = newProgrammingExercise;
            comp.notificationText = 'A Notification Text';

            jest.spyOn(programmingExerciseService, 'updateTimeline').mockReturnValue(of(new HttpResponse({ body: entityOne })));

            // WHEN
            comp.saveAll();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.updateTimeline).toHaveBeenCalledWith(entityOne, { notificationText: comp.notificationText });
            expect(programmingExerciseService.updateTimeline).toHaveBeenCalledWith(entityTwo, { notificationText: comp.notificationText });
            expect(comp.selectedProgrammingExercises[0].dueDate).toEqual(newProgrammingExercise.dueDate);
            expect(comp.selectedProgrammingExercises[1].dueDate).toEqual(newProgrammingExercise.dueDate);
            expect(comp.selectedProgrammingExercises[0].releaseDate).toEqual(newProgrammingExercise.releaseDate);
            expect(comp.selectedProgrammingExercises[1].releaseDate).toEqual(newProgrammingExercise.releaseDate);
            expect(comp.isSaving).toBeFalse();
        }));

        it('should display error and not close modal', fakeAsync(() => {
            // GIVEN
            const newProgrammingExercise = new ProgrammingExercise(new Course(), undefined);
            newProgrammingExercise.releaseDate = dayjs();
            newProgrammingExercise.dueDate = dayjs().add(7, 'days');
            const selectedProgrammingExercises = [];
            const entityOne = new ProgrammingExercise(new Course(), undefined);
            entityOne.id = 123;
            entityOne.releaseDate = dayjs().add(1, 'days');
            selectedProgrammingExercises.push(entityOne);
            comp.selectedProgrammingExercises = selectedProgrammingExercises;
            comp.newProgrammingExercise = newProgrammingExercise;

            jest.spyOn(programmingExerciseService, 'updateTimeline').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            jest.spyOn(comp, 'closeModal');
            // WHEN
            comp.saveAll();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.updateTimeline).toHaveBeenCalledWith(entityOne, {});
            expect(comp.failureOccurred).toBeTrue();
            expect(comp.isSaving).toBeFalse();
            expect(comp.closeModal).not.toHaveBeenCalled();
        }));
    });
});
