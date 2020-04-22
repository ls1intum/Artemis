import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../../mocks/mock-sync.storage';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../mocks/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { MockOrionConnectorService } from '../../../mocks/mock-orion-connector.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { MockCourseExerciseService } from '../../../mocks/mock-course-exercise.service';

describe('Component Tests', () => {
    describe('ProgrammingExercise Management Component', () => {
        const course = { id: 123 } as Course;
        const programmingExercise = new ProgrammingExercise(course);
        programmingExercise.id = 456;

        let comp: ProgrammingExerciseComponent;
        let fixture: ComponentFixture<ProgrammingExerciseComponent>;
        let service: CourseExerciseService;

        const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ProgrammingExerciseComponent],
                providers: [
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: ActivatedRoute, useValue: route },
                    { provide: OrionConnectorService, useClass: MockOrionConnectorService },
                    { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                ],
            })
                .overrideTemplate(ProgrammingExerciseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ProgrammingExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(CourseExerciseService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'findAllProgrammingExercisesForCourse').and.returnValue(
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
            expect(service.findAllProgrammingExercisesForCourse).toHaveBeenCalled();
            expect(comp.programmingExercises[0]).toEqual(jasmine.objectContaining({ id: programmingExercise.id }));
        });
    });
});
