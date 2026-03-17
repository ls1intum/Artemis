import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseRequest } from 'app/core/shared/entities/course-request.model';
import dayjs from 'dayjs/esm';
import { of, throwError } from 'rxjs';

import { CourseRequestComponent } from 'app/core/course/request/course-request.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { CourseRequestFormComponent } from 'app/core/course/request/course-request-form.component';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CourseRequestComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseRequestComponent;
    let courseRequestService: {
        create: ReturnType<typeof vi.fn>;
        findAdminOverview: ReturnType<typeof vi.fn>;
        acceptRequest: ReturnType<typeof vi.fn>;
        rejectRequest: ReturnType<typeof vi.fn>;
    };
    let alertService: {
        success: ReturnType<typeof vi.fn>;
        error: ReturnType<typeof vi.fn>;
        warning: ReturnType<typeof vi.fn>;
    };

    beforeEach(async () => {
        courseRequestService = {
            create: vi.fn(),
            findAdminOverview: vi.fn(),
            acceptRequest: vi.fn(),
            rejectRequest: vi.fn(),
        };

        alertService = {
            success: vi.fn(),
            error: vi.fn(),
            warning: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [CourseRequestComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: CourseRequestService, useValue: courseRequestService },
                { provide: AlertService, useValue: alertService },
            ],
        })
            .overrideComponent(CourseRequestComponent, {
                set: {
                    imports: [MockComponent(CourseRequestFormComponent), MockComponent(ButtonComponent), MockDirective(TranslateDirective)],
                },
            })
            .compileComponents();

        component = TestBed.createComponent(CourseRequestComponent).componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
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

        expect(component.dateRangeInvalid).toBe(true);
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

        expect(component.dateRangeInvalid).toBe(false);
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
        expect(component.isSubmitting).toBe(false);
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

    it('should not submit when semester is empty', () => {
        component.form.patchValue({
            title: 'New Course',
            shortName: 'ABC',
            semester: '',
            reason: 'A valid reason for the request',
        });

        component.submit();

        expect(component.form.get('semester')?.invalid).toBe(true);
        expect(courseRequestService.create).not.toHaveBeenCalled();
    });

    it('should have semester pre-filled with default value', () => {
        // The semester should be pre-filled based on the default semester logic
        expect(component.form.get('semester')?.value).toBeTruthy();
        expect(component.form.get('semester')?.value).toMatch(/^(WS|SS)\d+/);
    });

    it('should reset form after successful submission', () => {
        courseRequestService.create.mockReturnValue(of({} as CourseRequest));
        component.form.patchValue({
            title: 'New Course',
            shortName: 'ABC',
            reason: 'A valid reason for the request',
        });

        component.submit();

        expect(component.form.get('title')?.value).toBe('');
        expect(component.form.get('shortName')?.value).toBe('');
        expect(component.form.get('reason')?.value).toBe('');
        expect(component.isSubmitting).toBe(false);
    });

    it('should not submit when form is invalid', () => {
        component.form.patchValue({
            title: '', // Invalid - required
            shortName: 'ABC',
            reason: 'A valid reason',
        });

        component.submit();

        expect(courseRequestService.create).not.toHaveBeenCalled();
        expect(component.form.get('title')?.touched).toBe(true);
    });
});
