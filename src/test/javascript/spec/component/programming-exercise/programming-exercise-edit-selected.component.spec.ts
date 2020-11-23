import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import * as moment from 'moment';

import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProgrammingLanguageFeatureService } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';

describe('ProgrammingExercise Edit Selected Component', () => {
    let comp: ProgrammingExerciseEditSelectedComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditSelectedComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseEditSelectedComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .overrideTemplate(ProgrammingExerciseEditSelectedComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseEditSelectedComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        exerciseGroupService = fixture.debugElement.injector.get(ExerciseGroupService);
        programmingExerciseFeatureService = fixture.debugElement.injector.get(ProgrammingLanguageFeatureService);
    });

    describe('saveAll', () => {
        it('Should update each selected exercise', fakeAsync(() => {
            // GIVEN
            // the exercise containing the values to update the selected ones
            const newProgrammingExercise = new ProgrammingExercise(new Course(), undefined);
            newProgrammingExercise.releaseDate = moment();
            newProgrammingExercise.dueDate = moment().add(7, 'days');
            const selectedProgrammingExercises = [];
            const entityOne = new ProgrammingExercise(new Course(), undefined);
            entityOne.id = 123;
            entityOne.releaseDate = moment().add(1, 'days');
            const entityTwo = new ProgrammingExercise(new Course(), undefined);
            entityTwo.id = 123;
            entityTwo.releaseDate = moment().add(1, 'days');
            selectedProgrammingExercises.push(entityOne);
            selectedProgrammingExercises.push(entityTwo);
            comp.selectedProgrammingExercises = selectedProgrammingExercises;
            comp.newProgrammingExercise = newProgrammingExercise;

            spyOn(programmingExerciseService, 'update').and.returnValue(of(new HttpResponse({ body: entityOne })));

            // WHEN
            comp.saveAll();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entityOne, {});
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entityTwo, {});
            expect(comp.selectedProgrammingExercises[0].dueDate).toEqual(newProgrammingExercise.dueDate);
            expect(comp.selectedProgrammingExercises[1].dueDate).toEqual(newProgrammingExercise.dueDate);
            expect(comp.selectedProgrammingExercises[0].releaseDate).toEqual(newProgrammingExercise.releaseDate);
            expect(comp.selectedProgrammingExercises[1].releaseDate).toEqual(newProgrammingExercise.releaseDate);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should display error and not close modal', fakeAsync(() => {
            // GIVEN
            const newProgrammingExercise = new ProgrammingExercise(new Course(), undefined);
            newProgrammingExercise.releaseDate = moment();
            newProgrammingExercise.dueDate = moment().add(7, 'days');
            const selectedProgrammingExercises = [];
            const entityOne = new ProgrammingExercise(new Course(), undefined);
            entityOne.id = 123;
            entityOne.releaseDate = moment().add(1, 'days');
            selectedProgrammingExercises.push(entityOne);
            comp.selectedProgrammingExercises = selectedProgrammingExercises;
            comp.newProgrammingExercise = newProgrammingExercise;

            spyOn(programmingExerciseService, 'update').and.returnValue(of(new HttpErrorResponse({ error: 'Internal Server Error' })));
            spyOn(comp, 'closeModal');
            // WHEN
            comp.saveAll();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entityOne, {});
            // expect(comp.failedExercises.length).toBeGreaterThan(0);
            expect(comp.isSaving).toEqual(false);
            expect(comp.closeModal).toHaveBeenCalledTimes(0);
        }));
    });
});
