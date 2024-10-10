import '@angular/localize/init';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ImportAllCourseCompetenciesModalComponent } from 'app/course/competencies/components/import-all-course-competencies-modal/import-all-course-competencies-modal.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseCompetencyImportSettings } from 'app/course/competencies/components/import-course-competencies-settings/import-course-competencies-settings.component';
import { Course } from 'app/entities/course.model';

describe('ImportAllCourseCompetenciesModalComponent', () => {
    let component: ImportAllCourseCompetenciesModalComponent;
    let fixture: ComponentFixture<ImportAllCourseCompetenciesModalComponent>;

    let activeModal: NgbActiveModal;
    let closeModalSpy: jest.SpyInstance;

    const courseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportAllCourseCompetenciesModalComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: NgbActiveModal,
                    useValue: {
                        close(result?: any): Promise<any | undefined> {
                            return Promise.resolve(result);
                        },
                    },
                },
            ],
        }).compileComponents();

        activeModal = TestBed.inject(NgbActiveModal);
        closeModalSpy = jest.spyOn(activeModal, 'close');

        fixture = TestBed.createComponent(ImportAllCourseCompetenciesModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toEqual(courseId);
        const settings = new CourseCompetencyImportSettings();
        expect(component.importSettings()).toEqual(settings);
    });

    it('should set disabledIds', () => {
        expect(component.disabledIds()).toEqual([courseId]);
    });

    it('should close modal on close button', () => {
        fixture.detectChanges();

        const closeButton = fixture.debugElement.nativeElement.querySelector('#close-button');
        closeButton.click();

        expect(closeModalSpy).toHaveBeenCalledOnce();
    });

    it('should select course and close modal', () => {
        const course = <Course>{ id: 2, title: 'Course 02', shortName: 'C2' };
        component.selectCourse(course);

        expect(closeModalSpy).toHaveBeenCalledWith({
            course: course,
            courseCompetencyImportOptions: {
                sourceCourseId: course.id,
                importExercises: false,
                importLectures: false,
                importRelations: false,
                referenceDate: undefined,
                isReleaseDate: undefined,
            },
        });
    });

    it('should initialize table columns correctly', () => {
        expect(component['tableColumns']).toEqual([
            { name: 'TITLE', getProperty: expect.any(Function) },
            { name: 'SHORT_NAME', getProperty: expect.any(Function) },
            { name: 'SEMESTER', getProperty: expect.any(Function) },
        ]);
    });

    it('should get correct property for TITLE column', () => {
        const course = { title: 'Course Title' } as Course;
        const titleColumn = component['tableColumns'].find((col) => col.name === 'TITLE');
        expect(titleColumn?.getProperty(course)).toBe('Course Title');
    });

    it('should get correct property for SHORT_NAME column', () => {
        const course = { shortName: 'CS101' } as Course;
        const shortNameColumn = component['tableColumns'].find((col) => col.name === 'SHORT_NAME');
        expect(shortNameColumn?.getProperty(course)).toBe('CS101');
    });

    it('should get correct property for SEMESTER column', () => {
        const course = { semester: 'Fall 2023' } as Course;
        const semesterColumn = component['tableColumns'].find((col) => col.name === 'SEMESTER');
        expect(semesterColumn?.getProperty(course)).toBe('Fall 2023');
    });
});
