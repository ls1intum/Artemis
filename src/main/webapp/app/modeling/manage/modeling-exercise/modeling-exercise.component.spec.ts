import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ModelingExerciseComponent } from 'app/modeling/manage/modeling-exercise/modeling-exercise.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { SortService } from 'app/shared/service/sort.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { UMLDiagramType } from '@ls1intum/apollon';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ModelingExercise Management Component', () => {
    setupTestBed({ zoneless: true });

    let comp: ModelingExerciseComponent;
    let fixture: ComponentFixture<ModelingExerciseComponent>;
    let courseExerciseService: CourseExerciseService;
    let modelingExerciseService: ModelingExerciseService;
    let eventManager: EventManager;
    let sortService: SortService;

    const course: Course = { id: 123 } as Course;
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
    modelingExercise.id = 456;
    modelingExercise.title = 'UML Exercise';
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                MockProvider(EventManager),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).overrideTemplate(ModelingExerciseComponent, '');

        fixture = TestBed.createComponent(ModelingExerciseComponent);
        comp = fixture.componentInstance;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        modelingExerciseService = TestBed.inject(ModelingExerciseService);
        sortService = TestBed.inject(SortService);

        eventManager = TestBed.inject(EventManager);

        comp.modelingExercises = [modelingExercise];
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        const findStub = vi.spyOn(courseExerciseService, 'findAllModelingExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [modelingExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.course = course;
        comp.ngOnInit();

        // THEN
        expect(findStub).toHaveBeenCalledOnce();
        expect(comp.modelingExercises[0]).toEqual(modelingExercise);
    });

    it('should delete exercise', () => {
        vi.spyOn(modelingExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        comp.course = course;
        comp.ngOnInit();
        comp.deleteModelingExercise(456);
        expect(modelingExerciseService.delete).toHaveBeenCalledWith(456);
        expect(modelingExerciseService.delete).toHaveBeenCalledOnce();
    });

    it('should return exercise id', () => {
        expect(comp.trackId(0, modelingExercise)).toBe(456);
    });

    describe('ModelingExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('UML', '', 'modeling');

            // THEN
            expect(comp.modelingExercises).toHaveLength(1);
            expect(comp.filteredModelingExercises).toHaveLength(1);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.modelingExercises).toHaveLength(1);
            expect(comp.filteredModelingExercises).toHaveLength(0);
        });
    });

    it('should return items id when tracked', () => {
        const item = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        item.id = 123;
        expect(comp.trackId(2, item)).toBe(123);
    });

    it('should delete the given exercise', async () => {
        const deleteStub = vi.spyOn(modelingExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
        comp.deleteModelingExercise(2);
        expect(deleteStub).toHaveBeenCalledWith(2);
        expect(deleteStub).toHaveBeenCalledOnce();
        await fixture.whenStable();
        expect(broadcastSpy).toHaveBeenCalledWith({
            name: 'modelingExerciseListModification',
            content: 'Deleted an modelingExercise',
        });
        expect(broadcastSpy).toHaveBeenCalledOnce();
    });

    it('should sort rows', () => {
        const sortSpy = vi.spyOn(sortService, 'sortByProperty');
        comp.modelingExercises = [new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined)];
        comp.predicate = 'testPredicate';
        comp.reverse = true;
        comp.exerciseFilter = new ExerciseFilter();
        comp.sortRows();
        expect(sortSpy).toHaveBeenCalledWith(comp.modelingExercises, comp.predicate, comp.reverse);
        expect(sortSpy).toHaveBeenCalledOnce();
    });

    it('should have working selection', () => {
        // WHEN
        comp.toggleExercise(modelingExercise);

        // THEN
        expect(comp.selectedExercises[0]).toMatchObject({ id: modelingExercise.id });
        expect(comp.allChecked).toEqual(comp.selectedExercises.length === comp.modelingExercises.length);
    });
});
