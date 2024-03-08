import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { CompetencyImportCourseComponent } from 'app/course/competencies/competency-management/competency-import-course.component';
import { Course } from 'app/entities/course.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CompetencyImportCourseComponent', () => {
    let fixture: ComponentFixture<CompetencyImportCourseComponent>;
    let component: CompetencyImportCourseComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [
                MockPipe(ArtemisTranslatePipe),
                MockRouter,
                CompetencyImportCourseComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyImportCourseComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should extract competency properties', () => {
        const course: Course = {
            title: 'Course Title',
            shortName: 'CT',
            semester: 'WS24',
        };

        expect(component.columns).toBeDefined();
        expect(component.columns).toHaveLength(3);

        const titleProperty = component.columns[0];
        expect(titleProperty.getProperty(course)).toBe(course.title);

        const shortNameProperty = component.columns[1];
        expect(shortNameProperty.getProperty(course)).toBe(course.shortName);

        const semesterProperty = component.columns[2];
        expect(semesterProperty.getProperty(course)).toBe(course.semester);
    });
});
