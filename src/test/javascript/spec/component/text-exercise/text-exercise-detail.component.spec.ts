import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ArtemisTestModule } from '../../test.module';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { Course } from 'app/entities/course.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';

describe('TextExercise Management Detail Component', () => {
    let comp: TextExerciseDetailComponent;
    let fixture: ComponentFixture<TextExerciseDetailComponent>;
    let service: TextExerciseService;
    const course: Course = { id: 123 } as Course;
    const route = ({ params: of({ courseId: course.id }) } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextExerciseDetailComponent],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .overrideTemplate(TextExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(TextExerciseDetailComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(TextExerciseService);
    });

    describe('OnInit', () => {
        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: course,
                        headers,
                    }),
                ),
            );
            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(service.find).toHaveBeenCalled();
            expect(comp.textExercise).toEqual(jasmine.objectContaining({ id: course.id }));
        });
    });
});
