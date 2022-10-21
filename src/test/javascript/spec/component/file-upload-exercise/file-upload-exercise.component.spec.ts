import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise.component';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

import { ArtemisTestModule } from '../../test.module';

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
            imports: [ArtemisTestModule],
            declarations: [FileUploadExerciseComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(FileUploadExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(CourseExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);

        comp.fileUploadExercises = [fileUploadExercise];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call loadExercises on init', () => {
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

        // THEN
        expect(service.findAllFileUploadExercisesForCourse).toHaveBeenCalledOnce();
        expect(comp.fileUploadExercises[0]).toEqual(fileUploadExercise);
    });

    it('should delete exercise', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );

        comp.course = course;
        comp.ngOnInit();
        comp.deleteFileUploadExercise(456);
        expect(fileUploadExerciseService.delete).toHaveBeenCalledWith(456);
        expect(fileUploadExerciseService.delete).toHaveBeenCalledOnce();
    });

    it('should return exercise id', () => {
        expect(comp.trackId(0, fileUploadExercise)).toBe(456);
    });

    describe('FileUploadExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('pdf', '', 'file-upload');

            // THEN
            expect(comp.fileUploadExercises).toHaveLength(1);
            expect(comp.filteredFileUploadExercises).toHaveLength(1);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.fileUploadExercises).toHaveLength(1);
            expect(comp.filteredFileUploadExercises).toHaveLength(0);
        });
    });
});
