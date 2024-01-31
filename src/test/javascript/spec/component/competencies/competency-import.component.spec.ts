import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';
import { Competency } from 'app/entities/competency.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('CompetencyImportComponent', () => {
    let fixture: ComponentFixture<CompetencyImportComponent>;
    let comp: CompetencyImportComponent;
    const competency: Competency = {
        title: 'Competency title',
        course: {
            title: 'Course title',
            semester: 'WS24',
        },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [CompetencyImportComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyImportComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should extract competency properties', () => {
        expect(comp.columns).toBeDefined();
        // title, course title, semester
        expect(comp.columns).toHaveLength(3);

        const titleProperty = comp.columns[0];
        expect(titleProperty.name).toBe('TITLE');
        expect(titleProperty.getProperty(competency)).toBe('Competency title');

        const courseTitleProperty = comp.columns[1];
        expect(courseTitleProperty.name).toBe('COURSE_TITLE');
        expect(courseTitleProperty.getProperty(competency)).toBe('Course title');

        const semesterProperty = comp.columns[2];
        expect(semesterProperty.name).toBe('SEMESTER');
        expect(semesterProperty.getProperty(competency)).toBe('WS24');
    });
});
