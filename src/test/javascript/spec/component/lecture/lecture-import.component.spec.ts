import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { Lecture } from 'app/entities/lecture.model';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('LectureImportComponent', () => {
    let fixture: ComponentFixture<LectureImportComponent>;
    let comp: LectureImportComponent;

    const lecture: Lecture = {
        title: 'Lecture title',
        course: {
            title: 'Course title',
            semester: 'WS24',
        },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [LectureImportComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
            providers: [
                // Overwrite MockRouter declaration in ArtemisTestModule which just returns 'testValue'
                {
                    provide: Router,
                    useClass: Router,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureImportComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should extract lecture properties', () => {
        expect(comp.columns).toBeDefined();
        // title, course title, semester
        expect(comp.columns).toHaveLength(3);

        const titleProperty = comp.columns[0];
        expect(titleProperty.name).toBe('TITLE');
        expect(titleProperty.getProperty(lecture)).toBe('Lecture title');

        const courseTitleProperty = comp.columns[1];
        expect(courseTitleProperty.name).toBe('COURSE_TITLE');
        expect(courseTitleProperty.getProperty(lecture)).toBe('Course title');

        const semesterProperty = comp.columns[2];
        expect(semesterProperty.name).toBe('SEMESTER');
        expect(semesterProperty.getProperty(lecture)).toBe('WS24');
    });
});
