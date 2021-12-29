import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';
import { ModelingExerciseComponent } from 'app/exercises/modeling/manage/modeling-exercise.component';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { SortService } from 'app/shared/service/sort.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ModelingExerciseImportComponent } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

describe('ModelingExercise Management Component', () => {
    let comp: ModelingExerciseComponent;
    let fixture: ComponentFixture<ModelingExerciseComponent>;
    let courseExerciseService: CourseExerciseService;
    let modelingExerciseService: ModelingExerciseService;
    let eventManager: EventManager;
    let sortService: SortService;
    let modalService: NgbModal;

    const course: Course = { id: 123 } as Course;
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
    modelingExercise.id = 456;
    modelingExercise.title = 'UML Exercise';
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModelingExerciseComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .overrideTemplate(ModelingExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseComponent);
        comp = fixture.componentInstance;
        courseExerciseService = fixture.debugElement.injector.get(CourseExerciseService);
        modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);
        sortService = fixture.debugElement.injector.get(SortService);
        modalService = fixture.debugElement.injector.get(NgbModal);

        eventManager = fixture.debugElement.injector.get(EventManager);

        comp.modelingExercises = [modelingExercise];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        const findStub = jest.spyOn(courseExerciseService, 'findAllModelingExercisesForCourse').mockReturnValue(
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
        expect(findStub).toHaveBeenCalled();
        expect(comp.modelingExercises[0]).toEqual(modelingExercise);
    });

    it('Should delete exercise', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(modelingExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );

        comp.course = course;
        comp.ngOnInit();
        comp.deleteModelingExercise(456);
        expect(modelingExerciseService.delete).toHaveBeenCalledWith(456);
        expect(modelingExerciseService.delete).toHaveBeenCalledTimes(1);
    });

    it('Should open modal', () => {
        const mockReturnValue = { result: Promise.resolve({ id: 456 } as ModelingExercise) } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ModelingExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledTimes(1);
    });

    it('Should return exercise id', () => {
        expect(comp.trackId(0, modelingExercise)).toBe(456);
    });

    describe('ModelingExercise Search Exercises', () => {
        it('Should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('UML', '', 'modeling');

            // THEN
            expect(comp.modelingExercises).toHaveLength(1);
            expect(comp.filteredModelingExercises).toHaveLength(1);
        });

        it('Should show no exercises', () => {
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

    it('should delete the given exercise', fakeAsync(() => {
        const deleteStub = jest.spyOn(modelingExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<{}>));
        const broadcastSpy = jest.spyOn(eventManager, 'broadcast');
        comp.deleteModelingExercise(2);
        expect(deleteStub).toHaveBeenCalledWith(2);
        expect(deleteStub).toHaveBeenCalledTimes(1);
        tick();
        expect(broadcastSpy).toHaveBeenCalledWith({
            name: 'modelingExerciseListModification',
            content: 'Deleted an modelingExercise',
        });
        expect(broadcastSpy).toBeCalledTimes(1);
    }));

    it('should sort rows', () => {
        const sortSpy = jest.spyOn(sortService, 'sortByProperty');
        comp.modelingExercises = [new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined)];
        comp.predicate = 'testPredicate';
        comp.reverse = true;
        comp.exerciseFilter = new ExerciseFilter();
        comp.sortRows();
        expect(sortSpy).toHaveBeenCalledWith(comp.modelingExercises, comp.predicate, comp.reverse);
        expect(sortSpy).toHaveBeenCalledTimes(1);
    });
});
