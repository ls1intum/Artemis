import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
    CourseCompetencyImportSettings,
    ImportCourseCompetenciesSettingsComponent,
} from 'app/atlas/manage/import-course-competencies-settings/import-course-competencies-settings.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

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

        expect(component.importSettings().importRelations).toBeTruthy();
    });

    test.each(['importRelations', 'importExercises', 'importLectures'])('should toggle import setting', (setting) => {
        fixture.detectChanges();

        const importRelationsToggle = fixture.debugElement.nativeElement.querySelector(`#${setting}-checkbox`);
        importRelationsToggle.click();

        fixture.detectChanges();

        expect(component.importSettings()[setting as keyof CourseCompetencyImportSettings]).toBeTruthy();
        importRelationsToggle.click();

        fixture.detectChanges();

        expect(component.importSettings()[setting as keyof CourseCompetencyImportSettings]).toBeFalsy();
    });

    it('should set reference date', () => {
        fixture.detectChanges();

        const referenceDateTypeSelect = fixture.debugElement.nativeElement.querySelector('#reference-date-type-select');
        fixture.detectChanges();

        expect(referenceDateTypeSelect.disabled).toBeTruthy();

        const date = dayjs('2022-01-01');
        component.setReferenceDate(date);

        expect(component.importSettings().referenceDate).toEqual(date);
        expect(component.importSettings().isReleaseDate).toBeTruthy();
    });

    it('should set reference date type', () => {
        fixture.detectChanges();

        const referenceDateTypeSelect = fixture.debugElement.nativeElement.querySelector('#reference-date-type-select');
        referenceDateTypeSelect.value = 'true';
        referenceDateTypeSelect.dispatchEvent(new Event('change'));

        expect(component.importSettings().isReleaseDate).toBeTruthy();

        referenceDateTypeSelect.value = 'false';
        referenceDateTypeSelect.dispatchEvent(new Event('change'));

        expect(component.importSettings().isReleaseDate).toBeFalsy();
    });

    it('should set reference date when a date is provided', () => {
        const date = dayjs('2022-01-01');
        component.setReferenceDate(date);

        expect(component.importSettings().referenceDate).toEqual(date);
        expect(component.importSettings().isReleaseDate).toBeTruthy();
    });

    it('should unset reference date when no date is provided', () => {
        component.setReferenceDate();

        expect(component.importSettings().referenceDate).toBeUndefined();
        expect(component.importSettings().isReleaseDate).toBeUndefined();
    });

    it('should unset reference date when an invalid date is provided', () => {
        component.setReferenceDate(dayjs('not-a-date'));

        expect(component.importSettings().referenceDate).toBeUndefined();
        expect(component.importSettings().isReleaseDate).toBeUndefined();
    });

    it('should retain isReleaseDate when reference date is already set', () => {
        const initialDate = dayjs('2022-01-01');
        component.importSettings.update((settings) => ({
            ...settings,
            referenceDate: initialDate,
            isReleaseDate: false,
        }));

        const newDate = dayjs('2023-01-01');
        component.setReferenceDate(newDate);

        expect(component.importSettings().referenceDate).toEqual(newDate);
        expect(component.importSettings().isReleaseDate).toBeFalsy();
    });

    it('should set isReleaseDate to true when reference date is set for the first time', () => {
        const date = dayjs('2022-01-01');
        component.setReferenceDate(date);

        expect(component.importSettings().referenceDate).toEqual(date);
        expect(component.importSettings().isReleaseDate).toBeTruthy();
    });
    describe('reference date picker', () => {
        function pickerInput(): HTMLInputElement {
            return fixture.nativeElement.querySelector('#reference-date-field');
        }

        function type(text: string) {
            const input = pickerInput();
            input.value = text;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        }

        it('should label the picker and keep it empty by default', () => {
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('label[for="reference-date-field"]')).not.toBeNull();
            expect(pickerInput().value).toBe('');
        });

        it('should store a typed date and enable the date type select', () => {
            fixture.detectChanges();

            type('01.02.2022 14:30');

            expect(component.importSettings().referenceDate?.format('YYYY-MM-DD HH:mm')).toBe('2022-02-01 14:30');
            expect(component.importSettings().isReleaseDate).toBeTruthy();
            expect(fixture.nativeElement.querySelector('#reference-date-type-select').disabled).toBeFalsy();
        });

        it('should report whether the text of the picker is a date while the stored date stays', () => {
            const emitSpy = vi.spyOn(component.referenceDateTextValidChange, 'emit');
            component.importSettings.update((settings) => ({ ...settings, referenceDate: dayjs('2022-02-01T14:30') }));
            fixture.detectChanges();

            type('not a date');

            expect(emitSpy).toHaveBeenLastCalledWith(false);
            // The picker keeps its last valid value, which is why the host has to be told about the text.
            expect(component.importSettings().referenceDate?.format('YYYY-MM-DD HH:mm')).toBe('2022-02-01 14:30');

            type('02.02.2022 10:00');

            expect(emitSpy).toHaveBeenLastCalledWith(true);
        });

        it('should show the stored reference date', () => {
            component.importSettings.update((settings) => ({ ...settings, referenceDate: dayjs('2022-02-01T14:30') }));
            fixture.detectChanges();

            expect(pickerInput().value).toBe('01.02.2022 14:30');
        });

        it('should clear the reference date and the date type with the picker clear button', () => {
            component.importSettings.update((settings) => ({ ...settings, referenceDate: dayjs('2022-02-01T14:30'), isReleaseDate: false }));
            fixture.detectChanges();

            fixture.nativeElement.querySelector('button[aria-label]:not([aria-haspopup])')?.click();
            fixture.detectChanges();

            expect(component.importSettings().referenceDate).toBeUndefined();
            expect(component.importSettings().isReleaseDate).toBeUndefined();
        });
    });
});
