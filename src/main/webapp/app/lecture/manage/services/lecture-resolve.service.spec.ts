import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot } from '@angular/router';
import { LectureResolve } from 'app/lecture/manage/services/lecture-resolve.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';

describe('Resolvers', () => {
    describe('LectureResolve', () => {
        let resolver: LectureResolve;
        let service: LectureService;
        let route: ActivatedRouteSnapshot;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [LectureResolve, { provide: LectureService, useValue: { find: vi.fn() } }],
            });
            resolver = TestBed.inject(LectureResolve);
            service = TestBed.inject(LectureService);
            route = new ActivatedRouteSnapshot();
        });

        it('should return lecture when lectureId param is present', () => {
            const mockLecture = new Lecture();
            vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: mockLecture, status: 200 })));

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
});
