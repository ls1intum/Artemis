import { HttpClient, HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TutorialGroupsNotificationService } from 'app/course/tutorial-groups/services/tutorial-groups-notification.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { of } from 'rxjs';

describe('TutorialGroupsNotificationService', () => {
    let service: TutorialGroupsNotificationService;
    let tutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(TutorialGroupsNotificationService);

        tutorialGroup = new TutorialGroup();
        tutorialGroup.id = 1;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should make http call only when cache is empty', fakeAsync(() => {
        const http = TestBed.inject(HttpClient);
        const response: HttpResponse<TutorialGroup[]> = new HttpResponse({
            body: [tutorialGroup],
            status: 200,
        });
        const getSpy = jest.spyOn(http, 'get').mockReturnValue(of(response));

        // cache empty
        expect(service.tutorialGroupsForNotifications$).toBeUndefined();
        service.getTutorialGroupsForNotifications().subscribe((tutorialGroups) => {
            expect(tutorialGroups).toEqual([tutorialGroup]);
            expect(getSpy).toHaveBeenCalledWith('api/tutorial-groups/for-notifications', { observe: 'response' });
            expect(getSpy).toHaveBeenCalledOnce();
            getSpy.mockClear();
            // cache not empty anymore
            expect(service.tutorialGroupsForNotifications$).toBeDefined();
            service.getTutorialGroupsForNotifications().subscribe((tutorialGroups2) => {
                expect(tutorialGroups2).toEqual([tutorialGroup]);
                expect(getSpy).not.toHaveBeenCalled();
            });
        });
        tick();
        tick();
    }));
});
