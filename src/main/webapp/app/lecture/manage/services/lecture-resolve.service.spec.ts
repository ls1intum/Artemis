import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot } from '@angular/router';
import { AttachmentResolve, LectureResolve } from 'app/lecture/manage/services/lecture-resolve.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';

describe('Resolvers', () => {
    describe('LectureResolve', () => {
        let resolver: LectureResolve;
        let service: LectureService;
        let route: ActivatedRouteSnapshot;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [LectureResolve, { provide: LectureService, useValue: { find: jest.fn() } }],
            });
            resolver = TestBed.inject(LectureResolve);
            service = TestBed.inject(LectureService);
            route = new ActivatedRouteSnapshot();
        });

        it('should return lecture when lectureId param is present', () => {
            const mockLecture = new Lecture();
            jest.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: mockLecture, status: 200 })));

            route.params = { lectureId: 42 };
            let result: Lecture | undefined;

            resolver.resolve(route).subscribe((res) => (result = res));

            expect(service.find).toHaveBeenCalledWith(42);
            expect(result).toBe(mockLecture);
        });

        it('should return new Lecture when no lectureId param is provided', () => {
            route.params = {};
            let result: Lecture | undefined;

            resolver.resolve(route).subscribe((res) => (result = res));

            expect(result).toBeInstanceOf(Lecture);
            expect(service.find).not.toHaveBeenCalled();
        });
    });

    describe('AttachmentResolve', () => {
        let resolver: AttachmentResolve;
        let service: AttachmentService;
        let route: ActivatedRouteSnapshot;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [AttachmentResolve, { provide: AttachmentService, useValue: { find: jest.fn() } }],
            });
            resolver = TestBed.inject(AttachmentResolve);
            service = TestBed.inject(AttachmentService);
            route = new ActivatedRouteSnapshot();
        });

        it('should return attachment when attachmentId param is present', () => {
            const mockAttachment = new Attachment();
            jest.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: mockAttachment, status: 200 })));

            route.params = { attachmentId: 99 };
            let result: Attachment | undefined;

            resolver.resolve(route).subscribe((res) => (result = res));

            expect(service.find).toHaveBeenCalledWith(99);
            expect(result).toBe(mockAttachment);
        });

        it('should return new Attachment when no attachmentId param is provided', () => {
            route.params = {};
            let result: Attachment | undefined;

            resolver.resolve(route).subscribe((res) => (result = res));

            expect(result).toBeInstanceOf(Attachment);
            expect(service.find).not.toHaveBeenCalled();
        });
    });
});
