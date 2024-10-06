import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
    CourseCompetencyImportSettings,
    ImportCourseCompetenciesSettingsComponent,
} from 'app/course/competencies/components/import-course-competencies-settings/import-course-competencies-settings.component';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ImportCourseCompetenciesSettingsComponent', () => {
    let component: ImportCourseCompetenciesSettingsComponent;
    let fixture: ComponentFixture<ImportCourseCompetenciesSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportCourseCompetenciesSettingsComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
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

    it('should set reference date', () => {
        fixture.detectChanges();

        const referenceDateTypeSelect = fixture.debugElement.nativeElement.querySelector('#reference-date-type-select');
        fixture.detectChanges();

        expect(referenceDateTypeSelect.disabled).toBeTrue();

        const date = new Date(2022, 0, 1);
        const dateEvent = { value: date.toDateString() } as HTMLInputElement;
        component.setReferenceDate(dateEvent);

        expect(component.importSettings().referenceDate).toEqual(date);
        expect(component.importSettings().isReleaseDate).toBeTrue();
    });

    it('should set reference date type', () => {
        fixture.detectChanges();

        const referenceDateTypeSelect = fixture.debugElement.nativeElement.querySelector('#reference-date-type-select');
        referenceDateTypeSelect.value = 'true';
        referenceDateTypeSelect.dispatchEvent(new Event('change'));

        expect(component.importSettings().isReleaseDate).toBeTrue();

        referenceDateTypeSelect.value = 'false';
        referenceDateTypeSelect.dispatchEvent(new Event('change'));

        expect(component.importSettings().isReleaseDate).toBeFalse();
    });

    it('should set reference date when dateEvent is provided', () => {
        const date = new Date(2022, 0, 1);
        const dateEvent = { value: date.toDateString() } as HTMLInputElement;
        component.setReferenceDate(dateEvent);

        expect(component.importSettings().referenceDate).toEqual(date);
        expect(component.importSettings().isReleaseDate).toBeTrue();
    });

    it('should unset reference date when dateEvent is not provided', () => {
        component.setReferenceDate();

        expect(component.importSettings().referenceDate).toBeUndefined();
        expect(component.importSettings().isReleaseDate).toBeUndefined();
    });

    it('should retain isReleaseDate when reference date is already set', () => {
        const initialDate = new Date(2022, 0, 1);
        component.importSettings.update((settings) => ({
            ...settings,
            referenceDate: initialDate,
            isReleaseDate: false,
        }));

        const newDate = new Date(2023, 0, 1);
        const dateEvent = { value: newDate.toDateString() } as HTMLInputElement;
        component.setReferenceDate(dateEvent);

        expect(component.importSettings().referenceDate).toEqual(newDate);
        expect(component.importSettings().isReleaseDate).toBeFalse();
    });

    it('should set isReleaseDate to true when reference date is set for the first time', () => {
        const date = new Date(2022, 0, 1);
        const dateEvent = { value: date.toDateString() } as HTMLInputElement;
        component.setReferenceDate(dateEvent);

        expect(component.importSettings().referenceDate).toEqual(date);
        expect(component.importSettings().isReleaseDate).toBeTrue();
    });
});
