import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { ProgrammingExerciseComponent } from 'app/programming/manage/exercise/programming-exercise.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/edit-selected/programming-exercise-edit-selected.component';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('ProgrammingExercise Management Component', () => {
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';
    const programmingExercise2 = new ProgrammingExercise(course, undefined);
    programmingExercise2.id = 457;
    programmingExercise2.title = 'Exercise 2a';
    const programmingExercise3 = new ProgrammingExercise(course, undefined);
    programmingExercise3.id = 458;
    programmingExercise3.title = 'Exercise 2b';

    let comp: ProgrammingExerciseComponent;
    let fixture: ComponentFixture<ProgrammingExerciseComponent>;
    let courseExerciseService: CourseExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let modalService: NgbModal;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                SessionStorageService,
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(EventManager),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ProgrammingExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseComponent);
        comp = fixture.componentInstance;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        modalService = TestBed.inject(NgbModal);

        comp.programmingExercises = [programmingExercise, programmingExercise2, programmingExercise3];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call load all on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(courseExerciseService, 'findAllProgrammingExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [programmingExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.course = course;
        fixture.changeDetectorRef.detectChanges();
        comp.ngOnInit();

        // THEN
        expect(courseExerciseService.findAllProgrammingExercisesForCourse).toHaveBeenCalledTimes(2);
        expect(comp.programmingExercises[0]).toEqual(expect.objectContaining({ id: programmingExercise.id }));
        expect(comp.filteredProgrammingExercises[0]).toEqual(expect.objectContaining({ id: programmingExercise.id }));
    });

    it('should delete exercise', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(programmingExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );
        const mockSubscriber = jest.fn();
        comp.dialogError$.subscribe(mockSubscriber);

        comp.course = course;
        comp.ngOnInit();
        comp.deleteProgrammingExercise(456, { deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: true });
        expect(programmingExerciseService.delete).toHaveBeenCalledWith(456, true, true);
        expect(programmingExerciseService.delete).toHaveBeenCalledOnce();
        expect(mockSubscriber).toHaveBeenCalledWith('');
        expect(mockSubscriber).toHaveBeenCalledOnce();
    });

    it('should delete multiple exercises', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(programmingExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );
        const mockSubscriber = jest.fn();
        comp.dialogError$.subscribe(mockSubscriber);

        comp.course = course;
        comp.ngOnInit();
        comp.deleteMultipleProgrammingExercises([{ id: 441 }, { id: 442 }, { id: 443 }] as ProgrammingExercise[], {
            deleteStudentReposBuildPlans: true,
            deleteBaseReposBuildPlans: true,
        });
        expect(programmingExerciseService.delete).toHaveBeenCalledTimes(3);
        expect(mockSubscriber).toHaveBeenCalledTimes(3);
    });

    it('should not delete exercise on error', () => {
        const httpErrorResponse = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        jest.spyOn(programmingExerciseService, 'delete').mockReturnValue(throwError(() => httpErrorResponse));
        const mockSubscriber = jest.fn();
        comp.dialogError$.subscribe(mockSubscriber);

        comp.course = course;
        comp.ngOnInit();
        comp.deleteProgrammingExercise(456, { deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: true });
        expect(programmingExerciseService.delete).toHaveBeenCalledWith(456, true, true);
        expect(programmingExerciseService.delete).toHaveBeenCalledOnce();
        expect(mockSubscriber).toHaveBeenCalledWith(httpErrorResponse.message);
        expect(mockSubscriber).toHaveBeenCalledOnce();
    });

    it('should open edit selected modal', () => {
        const mockReturnValue = { componentInstance: {}, closed: of() } as unknown as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openEditSelectedModal();
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingExerciseEditSelectedComponent, {
            size: 'xl',
            backdrop: 'static',
        });
        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('should return exercise id', () => {
        expect(comp.trackId(0, programmingExercise)).toBe(456);
    });

    describe('ProgrammingExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Exercise', '', 'programming');

            // THEN
            expect(comp.programmingExercises).toHaveLength(3);
            expect(comp.filteredProgrammingExercises).toHaveLength(3);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Exercise', '', 'text');

            // THEN
            expect(comp.programmingExercises).toHaveLength(3);
            expect(comp.filteredProgrammingExercises).toHaveLength(0);
        });

        it('should show first exercise', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Exercise 1');

            // THEN
            expect(comp.programmingExercises).toHaveLength(3);
            expect(comp.filteredProgrammingExercises).toHaveLength(1);
        });

        it('should show last 2 exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('2');

            // THEN
            expect(comp.programmingExercises).toHaveLength(3);
            expect(comp.filteredProgrammingExercises).toHaveLength(2);
        });
    });

    describe('ProgrammingExercise Select Exercises', () => {
        it('should add selected exercise to list', () => {
            // WHEN
            comp.toggleExercise(programmingExercise);

            // THEN
            expect(comp.selectedExercises[0]).toContainEntry(['id', programmingExercise.id]);
        });

        it('should remove selected exercise to list', () => {
            // WHEN
            comp.toggleExercise(programmingExercise);
            comp.toggleExercise(programmingExercise);

            // THEN
            expect(comp.selectedExercises).toHaveLength(0);
        });

        it('should select all', () => {
            // WHEN
            comp.toggleMultipleExercises(comp.programmingExercises);

            // THEN
            expect(comp.selectedExercises).toHaveLength(comp.programmingExercises.length);
        });

        it('should deselect all', () => {
            // WHEN
            comp.toggleMultipleExercises(comp.programmingExercises); // Select all
            comp.toggleMultipleExercises(comp.programmingExercises); // Deselect all

            // THEN
            expect(comp.selectedExercises).toHaveLength(0);
        });

        it('should check correctly if selected', () => {
            // WHEN
            comp.toggleExercise(programmingExercise);

            // THEN
            expect(comp.isExerciseSelected(programmingExercise)).toBeTrue();
            expect(comp.isExerciseSelected(programmingExercise2)).toBeFalse();
        });
    });
});
