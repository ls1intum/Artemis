import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

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
    let service: CourseExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let exerciseService: ExerciseService;
    let modalService: NgbModal;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseComponent],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .overrideTemplate(ProgrammingExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(CourseExerciseService);
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
        modalService = fixture.debugElement.injector.get(NgbModal);

        comp.programmingExercises = [programmingExercise, programmingExercise2, programmingExercise3];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call load all on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'findAllProgrammingExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [programmingExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.course = course;
        fixture.detectChanges();
        comp.ngOnInit();

        // THEN
        expect(service.findAllProgrammingExercisesForCourse).toHaveBeenCalledTimes(2);
        expect(comp.programmingExercises[0]).toEqual(expect.objectContaining({ id: programmingExercise.id }));
        expect(comp.filteredProgrammingExercises[0]).toEqual(expect.objectContaining({ id: programmingExercise.id }));
    });

    it('should reset exercise', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(exerciseService, 'reset').mockReturnValue(
            of(
                new HttpResponse({
                    body: undefined,
                    headers,
                }),
            ),
        );
        const mockSubscriber = jest.fn();
        comp.dialogError$.subscribe(mockSubscriber);

        comp.course = course;
        comp.ngOnInit();
        comp.resetProgrammingExercise(456);
        expect(exerciseService.reset).toHaveBeenCalledWith(456);
        expect(exerciseService.reset).toHaveBeenCalledOnce();
        expect(mockSubscriber).toHaveBeenCalledWith('');
        expect(mockSubscriber).toHaveBeenCalledOnce();
    });

    it('should not reset exercise on error', () => {
        const httpErrorResponse = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        jest.spyOn(exerciseService, 'reset').mockReturnValue(throwError(() => httpErrorResponse));
        const mockSubscriber = jest.fn();
        comp.dialogError$.subscribe(mockSubscriber);

        comp.course = course;
        comp.ngOnInit();
        comp.resetProgrammingExercise(456);
        expect(exerciseService.reset).toHaveBeenCalledWith(456);
        expect(exerciseService.reset).toHaveBeenCalledOnce();
        expect(mockSubscriber).toHaveBeenCalledWith(httpErrorResponse.message);
        expect(mockSubscriber).toHaveBeenCalledOnce();
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

    it('should open import modal', () => {
        const mockReturnValue = { result: Promise.resolve({ id: 456 } as ProgrammingExercise) } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('should open edit selected modal', () => {
        const mockReturnValue = { componentInstance: {}, closed: of() } as unknown as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openEditSelectedModal();
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingExerciseEditSelectedComponent, { size: 'xl', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('should open repo export modal', () => {
        const mockReturnValue = { componentInstance: {} } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openRepoExportModal();
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingAssessmentRepoExportDialogComponent, { size: 'lg', backdrop: 'static' });
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
            comp.toggleProgrammingExercise(programmingExercise);

            // THEN
            expect(comp.selectedProgrammingExercises[0]).toContainEntry(['id', programmingExercise.id]);
        });

        it('should remove selected exercise to list', () => {
            // WHEN
            comp.toggleProgrammingExercise(programmingExercise);
            comp.toggleProgrammingExercise(programmingExercise);

            // THEN
            expect(comp.selectedProgrammingExercises).toHaveLength(0);
        });

        it('should select all', () => {
            // WHEN
            comp.toggleAllProgrammingExercises();

            // THEN
            expect(comp.selectedProgrammingExercises).toHaveLength(comp.programmingExercises.length);
        });

        it('should deselect all', () => {
            // WHEN
            comp.toggleAllProgrammingExercises(); // Select all
            comp.toggleAllProgrammingExercises(); // Deselect all

            // THEN
            expect(comp.selectedProgrammingExercises).toHaveLength(0);
        });

        it('should check correctly if selected', () => {
            // WHEN
            comp.toggleProgrammingExercise(programmingExercise);

            // THEN
            expect(comp.isExerciseSelected(programmingExercise)).toBeTrue();
            expect(comp.isExerciseSelected(programmingExercise2)).toBeFalse();
        });
    });
});
