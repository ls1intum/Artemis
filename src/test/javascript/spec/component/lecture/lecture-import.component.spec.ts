import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { Lecture } from 'app/entities/lecture.model';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
            imports: [FormsModule, MockComponent(NgbPagination)],
            declarations: [LectureImportComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective), MockComponent(FaIconComponent)],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
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
