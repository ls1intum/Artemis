import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GocastService } from './gocast.service';
import { GocastBindingWithApproval, GocastCourse, GocastPlaybackToken, GocastStream } from './gocast.model';

describe('GocastService', () => {
    setupTestBed({ zoneless: true });

    let service: GocastService;
    let httpMock: HttpTestingController;

    const courseId = 42;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), GocastService],
        });

        service = TestBed.inject(GocastService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should list administered TUM Live courses via GET', () => {
        const mockCourses: GocastCourse[] = [{ id: 1, name: 'Advanced Math', slug: 'adv-math', year: 2026, teachingTerm: 'W', vodEnabled: true, visibility: 'loggedin' }];

        service.listAdministeredTumLiveCourses(courseId).subscribe((courses) => {
            expect(courses).toHaveLength(1);
            expect(courses[0].id).toBe(1);
            expect(courses[0].name).toBe('Advanced Math');
        });

        const req = httpMock.expectOne(`api/videosource/courses/${courseId}/tumlive-courses`);
        expect(req.request.method).toBe('GET');
        req.flush(mockCourses);
    });

    it('should list TUM Live streams for bound course via GET', () => {
        const mockStreams: GocastStream[] = [
            { streamId: 100, name: 'Lecture 1', private: false, start: '2026-10-01T10:00:00Z', end: '2026-10-01T12:00:00Z' },
            { streamId: 101, name: 'Lecture 2', private: true },
        ];

        service.listTumLiveStreams(courseId).subscribe((streams) => {
            expect(streams).toHaveLength(2);
            expect(streams[0].streamId).toBe(100);
            expect(streams[1].private).toBe(true);
        });

        const req = httpMock.expectOne(`api/videosource/courses/${courseId}/tumlive-streams`);
        expect(req.request.method).toBe('GET');
        req.flush(mockStreams);
    });

    it('should create a binding via POST and return GocastBindingWithApproval', () => {
        const mockResponse: GocastBindingWithApproval = {
            binding: {
                courseId,
                gocastCourseId: 7,
                gocastCourseSlug: 'eidi',
                status: 'PENDING',
            },
            approvalUrl: 'https://tum.live/admin/course/7/integration/confirm?service=99&redirect=https://artemis.tum.de/...',
        };

        service.createBinding(courseId, 7, 'eidi').subscribe((response) => {
            expect(response.binding.status).toBe('PENDING');
            expect(response.approvalUrl).toBeDefined();
            expect(response.binding.gocastCourseId).toBe(7);
        });

        const req = httpMock.expectOne(`api/videosource/courses/${courseId}/binding`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({ gocastCourseId: 7, gocastCourseSlug: 'eidi' });
        req.flush(mockResponse);
    });

    it('should get binding status via GET (triggers EP7 verification)', () => {
        const mockBindingWithApproval: GocastBindingWithApproval = {
            binding: {
                courseId,
                gocastCourseId: 7,
                gocastCourseSlug: 'eidi',
                status: 'ACTIVE',
            },
        };

        service.getBinding(courseId).subscribe((binding) => {
            expect(binding.binding.status).toBe('ACTIVE');
        });

        const req = httpMock.expectOne(`api/videosource/courses/${courseId}/binding`);
        expect(req.request.method).toBe('GET');
        req.flush(mockBindingWithApproval);
    });

    it('should delete (revoke) a binding via DELETE', () => {
        service.deleteBinding(courseId).subscribe();

        const req = httpMock.expectOne(`api/videosource/courses/${courseId}/binding`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null);
    });

    it('should get a playback token via POST', () => {
        const streamId = 100;
        const mockToken: GocastPlaybackToken = {
            playlistUrl: 'https://tum.live/...',
            expiresIn: 7200,
        };

        service.getPlaybackToken(courseId, streamId).subscribe((token) => {
            expect(token.playlistUrl).toBe('https://tum.live/...');
            expect(token.expiresIn).toBe(7200);
        });

        const req = httpMock.expectOne(`api/videosource/courses/${courseId}/streams/${streamId}/playback-tokens`);
        expect(req.request.method).toBe('POST');
        req.flush(mockToken);
    });
});
