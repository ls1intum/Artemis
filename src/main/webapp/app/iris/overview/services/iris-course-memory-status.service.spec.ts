import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { IrisCourseMemoryStatusService } from 'app/iris/overview/services/iris-course-memory-status.service';
import { CourseMemoryOperation, CourseMemoryStage, IrisCourseMemoryStatusDTO } from 'app/iris/shared/entities/iris-course-memory-status-dto.model';

describe('IrisCourseMemoryStatusService', () => {
    let service: IrisCourseMemoryStatusService;
    let websocketService: WebsocketService;
    let channel: Subject<IrisCourseMemoryStatusDTO>;

    beforeEach(() => {
        channel = new Subject<IrisCourseMemoryStatusDTO>();
        TestBed.configureTestingModule({
            providers: [IrisCourseMemoryStatusService, { provide: WebsocketService, useValue: { subscribe: vi.fn(() => channel.asObservable()) } }],
        });
        service = TestBed.inject(IrisCourseMemoryStatusService);
        websocketService = TestBed.inject(WebsocketService);
    });

    afterEach(() => vi.clearAllMocks());

    it('should subscribe to the user-scoped course topic', () => {
        service.subscribeToCourse(42);

        expect(websocketService.subscribe).toHaveBeenCalledWith('/user/topic/iris/course-memory/42');
    });

    it('should forward status updates to subscribers', () => {
        const received: IrisCourseMemoryStatusDTO[] = [];
        service.subscribeToCourse(42).subscribe((status) => received.push(status));

        const status = { operation: CourseMemoryOperation.INGEST, stage: CourseMemoryStage.COMPLETED, courseId: 42, postId: '7' };
        channel.next(status);

        expect(received).toEqual([status]);
    });

    it('should reuse one websocket subscription per course', () => {
        service.subscribeToCourse(42);
        service.subscribeToCourse(42);

        expect(websocketService.subscribe).toHaveBeenCalledOnce();
    });

    it('should report whether there was a subscription to release', () => {
        service.subscribeToCourse(42);

        expect(service.unsubscribeFromCourse(42)).toBe(true);
        expect(service.unsubscribeFromCourse(42)).toBe(false);
    });

    it('should reject a missing course id', () => {
        expect(() => service.subscribeToCourse(0)).toThrow('Course ID is required');
    });
});
