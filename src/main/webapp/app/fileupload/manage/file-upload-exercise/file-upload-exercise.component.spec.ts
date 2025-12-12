import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { FileUploadExerciseComponent } from 'app/fileupload/manage/file-upload-exercise/file-upload-exercise.component';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockProvider } from 'ng-mocks';

describe('FileUploadExercise Management Component', () => {
    let comp: FileUploadExerciseComponent;
    let fixture: ComponentFixture<FileUploadExerciseComponent>;
    let service: CourseExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;

    const course: Course = { id: 123 } as Course;
    const fileUploadExercise = new FileUploadExercise(course, undefined);
    fileUploadExercise.id = 456;
    fileUploadExercise.title = 'PDF Upload';
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(LocalStorageService),
                MockProvider(SessionStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(EventManager),
                provideHttpClient(),
            ],
            imports: [FileUploadExerciseComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(CourseExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);

        fixture.componentRef.setInput('fileUploadExercises', [fileUploadExercise]);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call loadExercises on init', fakeAsync(() => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'findAllFileUploadExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [fileUploadExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.course = course;
        comp.ngOnInit();
        tick();

        // THEN
        expect(service.findAllFileUploadExercisesForCourse).toHaveBeenCalledOnce();
        expect(comp.fileUploadExercises()[0]).toEqual(fileUploadExercise);
    }));

    it('should delete exercise', fakeAsync(() => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'findAllFileUploadExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [fileUploadExercise],
                    headers,
                }),
            ),
        );

        comp.course = course;
        comp.ngOnInit();
        tick();
        comp.deleteFileUploadExercise(456);
        tick();
        expect(fileUploadExerciseService.delete).toHaveBeenCalledWith(456);
        expect(fileUploadExerciseService.delete).toHaveBeenCalledOnce();
    }));

    it('should return exercise id', () => {
        expect(comp.trackId(0, fileUploadExercise)).toBe(456);
    });

    describe('FileUploadExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('pdf', '', 'file-upload');
            // @ts-expect-error accessing protected method for testing
            comp.applyFilter();

            // THEN
            expect(comp.fileUploadExercises()).toHaveLength(1);
            expect(comp.filteredFileUploadExercises()).toHaveLength(1);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');
            // @ts-expect-error accessing protected method for testing
            comp.applyFilter();

            // THEN
            expect(comp.fileUploadExercises()).toHaveLength(1);
            expect(comp.filteredFileUploadExercises()).toHaveLength(0);
        });
    });

    it('should have working selection', () => {
        // WHEN
        comp.toggleExercise(fileUploadExercise);

        // THEN
        expect(comp.selectedExercises[0]).toContainEntry(['id', fileUploadExercise.id]);
        expect(comp.allChecked).toEqual(comp.selectedExercises.length === comp.fileUploadExercises().length);
    });
});
