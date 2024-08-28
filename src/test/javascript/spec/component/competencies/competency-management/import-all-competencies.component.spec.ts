import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { ImportAllCompetenciesComponent } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { Course } from 'app/entities/course.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from 'app/forms/forms.module';

describe('ImportAllCompetenciesComponent', () => {
    let fixture: ComponentFixture<ImportAllCompetenciesComponent>;
    let component: ImportAllCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ImportAllCompetenciesComponent, ArtemisTestModule, FormsModule, MockComponent(NgbPagination), ArtemisSharedCommonModule, ArtemisSharedComponentModule],
            declarations: [MockRouter, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ImportAllCompetenciesComponent);
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
