import { TestBed } from '@angular/core/testing';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ArtemisTestModule } from '../../test.module';
import { Course } from 'app/entities/course.model';

describe('Header Course Component', () => {
    let component: HeaderCourseComponent;

    const courseWithLongDescription: Course = {
        id: 123,
        title: 'Course Title1',
        shortName: 'ShortName1',
        description: 'a'.repeat(1000),
    };

    const courseWithShortDescription: Course = {
        id: 234,
        title: 'Course Title2',
        shortName: 'ShortName2',
        description: 'a'.repeat(100),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [HeaderCourseComponent],
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                component = TestBed.createComponent(HeaderCourseComponent).componentInstance;
            });
        window['innerWidth'] = 1920;
    });

    it('should toggle long course description', () => {
        component.course = courseWithLongDescription;

        component.ngOnChanges();
        expect(component.enableShowMore).toBeTrue();
        expect(component.longDescriptionShown).toBeFalse();
        expect(component.courseDescription).toBe('a'.repeat(480) + '…');

        component.toggleCourseDescription();

        expect(component.longDescriptionShown).toBeTrue();
        expect(component.courseDescription).toBe(courseWithLongDescription.description);

        component.toggleCourseDescription();

        expect(component.longDescriptionShown).toBeFalse();
        expect(component.courseDescription).toBe('a'.repeat(480) + '…');
    });

    it('should not enable show more for course with short description', () => {
        component.course = courseWithShortDescription;

        component.ngOnChanges();
        expect(component.enableShowMore).toBeFalse();
        expect(component.longDescriptionShown).toBeFalse();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);
    });

    it('should not enable show more for course with short description foo', () => {
        component.course = courseWithShortDescription;

        component.ngOnChanges();
        expect(component.enableShowMore).toBeFalse();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);

        window['innerWidth'] = 100;
        component.onResize();
        expect(component.enableShowMore).toBeTrue();
        expect(component.courseDescription).toBe('a'.repeat(25) + '…');

        component.toggleCourseDescription();
        expect(component.enableShowMore).toBeTrue();
        expect(component.longDescriptionShown).toBeTrue();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);

        window['innerWidth'] = 1920;
        component.onResize();
        expect(component.enableShowMore).toBeFalse();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);
    });
});
