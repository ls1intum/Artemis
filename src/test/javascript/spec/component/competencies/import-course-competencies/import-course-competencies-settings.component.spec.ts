import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
    CourseCompetencyImportSettings,
    ImportCourseCompetenciesSettingsComponent,
} from 'app/course/competencies/components/import-course-competencies-settings/import-course-competencies-settings.component';

describe('ImportCourseCompetenciesSettingsComponent', () => {
    let component: ImportCourseCompetenciesSettingsComponent;
    let fixture: ComponentFixture<ImportCourseCompetenciesSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportCourseCompetenciesSettingsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportCourseCompetenciesSettingsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('importSettings', new CourseCompetencyImportSettings());
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should toggle import setting', () => {
        fixture.detectChanges();

        const importRelationsToggle = fixture.debugElement.nativeElement.querySelector('#importRelations-checkbox');
        importRelationsToggle.click();

        fixture.detectChanges();

        expect(component.importSettings().importRelations).toBeTrue();
    });

    test.each(['importRelations', 'importExercises', 'importLectures'])('should toggle import setting', (setting) => {
        fixture.detectChanges();

        const importRelationsToggle = fixture.debugElement.nativeElement.querySelector(`#${setting}-checkbox`);
        importRelationsToggle.click();

        fixture.detectChanges();

        expect(component.importSettings()[setting as keyof CourseCompetencyImportSettings]).toBeTrue();
        importRelationsToggle.click();

        fixture.detectChanges();

        expect(component.importSettings()[setting as keyof CourseCompetencyImportSettings]).toBeFalse();
    });

    // it('should set reference date', () => {
    //     fixture.detectChanges();
    //     const referenceDateTypeSelect = fixture.debugElement.nativeElement.querySelector('#reference-date-type-select');
    //     fixture.detectChanges();
    //     expect(referenceDateTypeSelect.disabled).toBeTrue();
    //
    //     const referenceDateInput = fixture.debugElement.nativeElement.querySelector('#reference-date-field');
    //     referenceDateInput.value = '2022-01-01';
    //     referenceDateInput.dispatchEvent(new Event('input'));
    //     expect(component.importSettings().referenceDate).toEqual(new Date('2022-01-01'));
    // });
});
