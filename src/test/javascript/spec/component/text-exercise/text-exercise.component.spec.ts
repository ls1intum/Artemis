import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { ArtemisTestModule } from '../../test.module';
import { TextExerciseComponent } from 'app/exercises/text/manage/text-exercise/text-exercise.component';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

describe('TextExercise Management Component', () => {
    let comp: TextExerciseComponent;
    let fixture: ComponentFixture<TextExerciseComponent>;
    let courseExerciseService: CourseExerciseService;

    const course = { id: 123 } as Course;
    const textExercise: TextExercise = { id: 456 } as TextExercise;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextExerciseComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(TextExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseComponent);
        comp = fixture.componentInstance;
        courseExerciseService = fixture.debugElement.injector.get(CourseExerciseService);
    });

    it('Should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(courseExerciseService, 'findAllTextExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [textExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.course = course;
        comp.ngOnInit();

        // THEN
        expect(courseExerciseService.findAllTextExercisesForCourse).toHaveBeenCalled();
    });
});
