import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';

import { CourseRequestComponent } from 'app/core/course/request/course-request.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';

describe('CourseRequestComponent', () => {
    let component: CourseRequestComponent;
    let courseRequestService: jest.Mocked<CourseRequestService>;
    let alertService: jest.Mocked<AlertService>;

    beforeEach(async () => {
        courseRequestService = {
            create: jest.fn(),
            findAllForAdmin: jest.fn(),
            acceptRequest: jest.fn(),
            rejectRequest: jest.fn(),
        } as unknown as jest.Mocked<CourseRequestService>;

        alertService = {
            success: jest.fn(),
            error: jest.fn(),
        } as unknown as jest.Mocked<AlertService>;

        await TestBed.configureTestingModule({
            imports: [CourseRequestComponent, HttpClientTestingModule, TranslateModule.forRoot()],
            providers: [
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
});
