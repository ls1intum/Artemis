import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise.component';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { CourseExerciseService } from 'app/course/manage/course-management.service';

describe('FileUploadExercise Management Component', () => {
    let comp: FileUploadExerciseComponent;
    let fixture: ComponentFixture<FileUploadExerciseComponent>;
    let service: CourseExerciseService;

    const course: Course = { id: 123 } as Course;
    const fileUploadExercise = new FileUploadExercise(course, undefined);
    fileUploadExercise.id = 456;
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
        service = fixture.debugElement.injector.get(CourseExerciseService);
    });

    it('Should call loadExercises on init', () => {
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
        expect(service.findAllFileUploadExercisesForCourse).toHaveBeenCalled();
        expect(comp.fileUploadExercises[0]).toEqual(fileUploadExercise);
    });
});
