import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';

describe('CourseDiscussionComponent', () => {
    let fixture: ComponentFixture<CourseDiscussionComponent>;
    let component: CourseDiscussionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDiscussionComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
