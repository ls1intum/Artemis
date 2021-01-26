import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CourseDetailComponent } from 'app/course/manage/course-detail.component';
import { Course } from 'app/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('Course Management Detail Component', () => {
    let comp: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const route = ({ data: of({ courseId: course.id }), children: [] } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseDetailComponent],
            providers: [{ provide: ActivatedRoute, useValue: route }, MockProvider(TranslateService)],
        })
            .overrideTemplate(CourseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        comp = fixture.componentInstance;
    });

    describe('OnInit', () => {
        it('Should call registerChangeInCourses on init', () => {
            const registerSpy = spyOn(comp, 'registerChangeInCourses');

            fixture.detectChanges();
            comp.ngOnInit();

            expect(registerSpy).toHaveBeenCalled();
        });
    });
});
