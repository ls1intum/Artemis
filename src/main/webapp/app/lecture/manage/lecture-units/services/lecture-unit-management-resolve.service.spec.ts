import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AttachmentVideoUnitResolve } from 'app/lecture/manage/lecture-units/services/lecture-unit-management-resolve.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';

describe('AttachmentVideoUnitResolve', () => {
    let resolver: AttachmentVideoUnitResolve;
    let service: AttachmentVideoUnitService;
    let route: ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AttachmentVideoUnitResolve, { provide: AttachmentVideoUnitService, useValue: { findById: jest.fn() } }],
        });

        resolver = TestBed.inject(AttachmentVideoUnitResolve);
        service = TestBed.inject(AttachmentVideoUnitService);
        route = new ActivatedRouteSnapshot();
    });

    it('should fetch and return the attachment video unit when ID is present', () => {
        const mockUnit = new AttachmentVideoUnit();
        (service.findById as jest.Mock).mockReturnValue(of(new HttpResponse({ body: mockUnit, status: 200 })));

        route.params = { lectureId: 7, attachmentVideoUnitId: 123 };

        let result: AttachmentVideoUnit | undefined;
        resolver.resolve(route).subscribe((res) => (result = res));

        expect(service.findById).toHaveBeenCalledWith(123, 7);
        expect(result).toBe(mockUnit);
    });

    it('should return a new AttachmentVideoUnit when no ID is provided', () => {
        route.params = { lectureId: 7 };

        let result: AttachmentVideoUnit | undefined;
        resolver.resolve(route).subscribe((res) => (result = res));

        expect(result).toBeInstanceOf(AttachmentVideoUnit);
        expect(service.findById).not.toHaveBeenCalled();
    });
});
