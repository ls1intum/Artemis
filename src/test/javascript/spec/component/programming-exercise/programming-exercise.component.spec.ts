import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';

describe('ProgrammingExercise Management Component', () => {
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    const programmingExercise2 = new ProgrammingExercise(course, undefined);
    programmingExercise2.id = 457;
    const programmingExercise3 = new ProgrammingExercise(course, undefined);
    programmingExercise3.id = 458;

    let comp: ProgrammingExerciseComponent;
    let fixture: ComponentFixture<ProgrammingExerciseComponent>;
    let service: CourseExerciseService;

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
            ],
        })
            .overrideTemplate(ProgrammingExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(CourseExerciseService);

        comp.programmingExercises = [programmingExercise, programmingExercise2, programmingExercise3];
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
        expect(comp.programmingExercises[0]).toEqual(expect.objectContaining({ id: programmingExercise.id }));
    });

    describe('ProgrammingExercise Select Exercises', () => {
        it('Should add selected exercise to list', () => {
            // WHEN
            comp.toggleProgrammingExercise(programmingExercise);

            // THEN
            expect(comp.selectedProgrammingExercises[0]).toEqual(jasmine.objectContaining({ id: programmingExercise.id }));
        });

        it('Should remove selected exercise to list', () => {
            // WHEN
            comp.toggleProgrammingExercise(programmingExercise);
            comp.toggleProgrammingExercise(programmingExercise);

            // THEN
            expect(comp.selectedProgrammingExercises.length).toEqual(0);
        });

        it('Should select all', () => {
            // WHEN
            comp.toggleAllProgrammingExercises();

            // THEN
            expect(comp.selectedProgrammingExercises.length).toEqual(comp.programmingExercises.length);
        });

        it('Should deselect all', () => {
            // WHEN
            comp.toggleAllProgrammingExercises(); // Select all
            comp.toggleAllProgrammingExercises(); // Deselect all

            // THEN
            expect(comp.selectedProgrammingExercises.length).toEqual(0);
        });

        it('Should check correctly if selected', () => {
            // WHEN
            comp.toggleProgrammingExercise(programmingExercise);

            // THEN
            expect(comp.isExerciseSelected(programmingExercise)).toBeTruthy();
            expect(comp.isExerciseSelected(programmingExercise2)).toBeFalsy();
        });
    });
});
