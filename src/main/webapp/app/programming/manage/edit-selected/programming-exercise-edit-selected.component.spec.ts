import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';

import dayjs from 'dayjs/esm';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseTimelineComponent } from 'app/programming/shared/programming-exercise-update-timeline/programming-exercise-timeline.component';

import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/edit-selected/programming-exercise-edit-selected.component';

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
        it('should not save when the timeline is invalid', () => {
            comp.newProgrammingExercise.releaseDate = dayjs();
            fixture.componentRef.setInput('selectedProgrammingExercises', [new ProgrammingExercise(new Course(), undefined)]);
            comp.timelineStatus.set({ valid: false, empty: false, invalidItems: [] });
            const updateSpy = vi.spyOn(programmingExerciseService, 'updateTimeline');

            comp.saveAll();

            expect(updateSpy).not.toHaveBeenCalled();
            expect(comp.isSaving()).toBe(false);
        });

        it('should update each selected exercise', () => {
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
            fixture.componentRef.setInput('selectedProgrammingExercises', selectedProgrammingExercises);
            comp.newProgrammingExercise = newProgrammingExercise;
            comp.notificationText = 'A Notification Text';

            vi.spyOn(programmingExerciseService, 'updateTimeline').mockReturnValue(of(new HttpResponse({ body: entityOne })));

            // WHEN
            // The mocked service emits synchronously via of(...), so the subscribe callback runs during saveAll().
            comp.saveAll();

            // THEN
            expect(programmingExerciseService.updateTimeline).toHaveBeenCalledWith(entityOne, { notificationText: comp.notificationText });
            expect(programmingExerciseService.updateTimeline).toHaveBeenCalledWith(entityTwo, { notificationText: comp.notificationText });
            expect(comp.selectedProgrammingExercises()[0].dueDate).toEqual(newProgrammingExercise.dueDate);
            expect(comp.selectedProgrammingExercises()[1].dueDate).toEqual(newProgrammingExercise.dueDate);
            expect(comp.selectedProgrammingExercises()[0].releaseDate).toEqual(newProgrammingExercise.releaseDate);
            expect(comp.selectedProgrammingExercises()[1].releaseDate).toEqual(newProgrammingExercise.releaseDate);
            expect(comp.isSaving()).toBe(false);
        });

        it('should display error and not close modal', () => {
            // GIVEN
            const newProgrammingExercise = new ProgrammingExercise(new Course(), undefined);
            newProgrammingExercise.releaseDate = dayjs();
            newProgrammingExercise.dueDate = dayjs().add(7, 'days');
            const selectedProgrammingExercises = [];
            const entityOne = new ProgrammingExercise(new Course(), undefined);
            entityOne.id = 123;
            entityOne.releaseDate = dayjs().add(1, 'days');
            selectedProgrammingExercises.push(entityOne);
            fixture.componentRef.setInput('selectedProgrammingExercises', selectedProgrammingExercises);
            comp.newProgrammingExercise = newProgrammingExercise;

            vi.spyOn(programmingExerciseService, 'updateTimeline').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            vi.spyOn(comp, 'closeModal');
            // WHEN
            // The mocked service emits synchronously via throwError(...), so the error callback runs during saveAll().
            comp.saveAll();

            // THEN
            expect(programmingExerciseService.updateTimeline).toHaveBeenCalledWith(entityOne, {});
            expect(comp.failureOccurred()).toBe(true);
            expect(comp.isSaving()).toBe(false);
            expect(comp.closeModal).not.toHaveBeenCalled();
        });
    });
});

describe('ProgrammingExercise Edit Selected Component template', () => {
    let comp: ProgrammingExerciseEditSelectedComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditSelectedComponent>;

    const exerciseWithTitle = (title: string) => {
        const exercise = new ProgrammingExercise(new Course(), undefined);
        exercise.title = title;
        return exercise;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
            ],
        }).overrideComponent(ProgrammingExerciseEditSelectedComponent, {
            remove: { imports: [ProgrammingExerciseTimelineComponent] },
            add: { imports: [MockComponent(ProgrammingExerciseTimelineComponent)] },
        });
        fixture = TestBed.createComponent(ProgrammingExerciseEditSelectedComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('selectedProgrammingExercises', [exerciseWithTitle('Sorting'), exerciseWithTitle('Graphs')]);
        fixture.componentRef.setInput('visible', true);
    });

    afterEach(() => {
        fixture.destroy();
    });

    it('should list the selected exercises inside the table layout before saving', () => {
        fixture.detectChanges();

        const layout = document.querySelector('tumaet-ui-table-layout')!;
        const titles = Array.from(layout.querySelectorAll('tbody td')).map((cell) => cell.textContent!.trim());
        expect(titles).toEqual(['Sorting', 'Graphs']);
    });

    it('should list the failed exercises instead of the selection after a failed save', () => {
        fixture.detectChanges(); // opening the dialog resets the result state
        comp.failureOccurred.set(true);
        comp.failedExercises.set(['Graphs']);
        fixture.detectChanges();

        const layout = document.querySelector('tumaet-ui-table-layout')!;
        expect(Array.from(layout.querySelectorAll('tbody td')).map((cell) => cell.textContent!.trim())).toEqual(['Graphs']);
        expect(document.querySelectorAll('tumaet-ui-table-layout')).toHaveLength(1);
    });
});
