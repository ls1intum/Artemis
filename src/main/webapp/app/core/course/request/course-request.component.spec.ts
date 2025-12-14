import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CourseRequest } from 'app/core/shared/entities/course-request.model';
import dayjs from 'dayjs/esm';
import { of, throwError } from 'rxjs';

import { CourseRequestComponent } from 'app/core/course/request/course-request.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CourseRequestComponent', () => {
    let component: CourseRequestComponent;
    let courseRequestService: jest.Mocked<CourseRequestService>;
    let alertService: jest.Mocked<AlertService>;

    beforeEach(async () => {
        courseRequestService = {
            create: jest.fn(),
            findAdminOverview: jest.fn(),
            acceptRequest: jest.fn(),
            rejectRequest: jest.fn(),
        } as unknown as jest.Mocked<CourseRequestService>;

        alertService = {
            success: jest.fn(),
            error: jest.fn(),
            warning: jest.fn(),
        } as unknown as jest.Mocked<AlertService>;

        await TestBed.configureTestingModule({
            imports: [
                CourseRequestComponent,
                TranslateModule.forRoot(),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(ButtonComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: CourseRequestService, useValue: courseRequestService },
                { provide: AlertService, useValue: alertService },
            ],
        }).compileComponents();

        component = TestBed.createComponent(CourseRequestComponent).componentInstance;
    });

    it('should mark invalid date ranges and not submit', () => {
        component.form.patchValue({
            title: 'New Course',
            shortName: 'ABC',
            reason: 'A valid reason for the request',
            startDate: dayjs('2025-01-10'),
            endDate: dayjs('2025-01-05'),
        });

        component.submit();

        expect(component.dateRangeInvalid).toBeTrue();
        expect(courseRequestService.create).not.toHaveBeenCalled();
    });

    it('should submit when the form is valid', () => {
        courseRequestService.create.mockReturnValue(of({} as CourseRequest));
        component.form.patchValue({
            title: 'New Course',
            shortName: 'ABC',
            reason: 'A valid reason for the request',
            startDate: dayjs('2025-01-01'),
            endDate: dayjs('2025-02-01'),
            testCourse: true,
        });

        component.submit();

        expect(component.dateRangeInvalid).toBeFalse();
        expect(courseRequestService.create).toHaveBeenCalledWith(
            expect.objectContaining({
                title: 'New Course',
                shortName: 'ABC',
                testCourse: true,
            }),
        );
        expect(alertService.success).toHaveBeenCalled();
    });

    it('should handle short name conflict error and apply suggested short name', () => {
        const suggestedShortName = 'NC2025';
        const errorResponse = new HttpErrorResponse({
            error: {
                errorKey: 'courseShortNameExists',
                params: { suggestedShortName },
            },
            status: 400,
        });
        courseRequestService.create.mockReturnValue(throwError(() => errorResponse));
        component.form.patchValue({
            title: 'New Course',
            shortName: 'EXISTING',
            reason: 'A valid reason for the request',
        });

        component.submit();

        expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName });
        expect(component.form.get('shortName')?.value).toBe(suggestedShortName);
        expect(component.isSubmitting).toBeFalse();
    });

    it('should handle course request short name conflict error', () => {
        const suggestedShortName = 'NC2025';
        const errorResponse = new HttpErrorResponse({
            error: {
                errorKey: 'courseRequestShortNameExists',
                params: { suggestedShortName },
            },
            status: 400,
        });
        courseRequestService.create.mockReturnValue(throwError(() => errorResponse));
        component.form.patchValue({
            title: 'New Course',
            shortName: 'EXISTING',
            reason: 'A valid reason for the request',
        });

        component.submit();

        expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName });
        expect(component.form.get('shortName')?.value).toBe(suggestedShortName);
    });

    it('should generate short name from title and semester', () => {
        component.form.patchValue({
            title: 'Introduction To Programming',
            semester: 'WS25/26',
        });

        component.generateShortName();

        // "ITP" from title + "2526" from "WS25/26"
        expect(component.form.get('shortName')?.value).toBe('ITP2526');
    });

    it('should generate short name with minimum length padding', () => {
        component.form.patchValue({
            title: 'AI',
            semester: '',
        });

        component.generateShortName();

        // 'AI' as a single word contributes only 'A' (first letter of each word)
        // Then padding: 'A' + 'CRS'.substring(0, 3-1) = 'A' + 'CR' = 'ACR'
        expect(component.form.get('shortName')?.value).toBe('ACR');
    });

    it('should not submit when semester is empty', () => {
        component.form.patchValue({
            title: 'New Course',
            shortName: 'ABC',
            semester: '',
            reason: 'A valid reason for the request',
        });

        component.submit();

        expect(component.form.get('semester')?.invalid).toBeTrue();
        expect(courseRequestService.create).not.toHaveBeenCalled();
    });

    it('should have semester pre-filled with default value', () => {
        // The semester should be pre-filled based on the default semester logic
        expect(component.form.get('semester')?.value).toBeTruthy();
        expect(component.form.get('semester')?.value).toMatch(/^(WS|SS)\d+/);
    });
});
