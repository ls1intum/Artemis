import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AthenaService } from 'app/assessment/athena.service';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { ArtemisTestModule } from '../test.module';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../helpers/mocks/service/mock-profile.service';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

describe('AthenaService', () => {
    let athenaService: AthenaService;
    let profileService: ProfileService;
    let httpTestingController: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [{ provide: ProfileService, useClass: MockProfileService }],
        })
            .compileComponents()
            .then(() => {
                athenaService = TestBed.inject(AthenaService);
                httpTestingController = TestBed.inject(HttpTestingController);
                profileService = TestBed.inject(ProfileService);
            });
    });

    it('should get feedback suggestions when athena is enabled', fakeAsync(() => {
        const feedbackSuggestions: TextBlockRef[] = [];
        let response: TextBlockRef[] | null = null;

        const mockProfileInfo = { activeProfiles: ['athena'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getFeedbackSuggestions(1, 2).subscribe((suggestions: TextBlockRef[]) => {
            response = suggestions;
        });

        const requestWrapper = httpTestingController.expectOne({ url: 'api/athena/exercises/1/submissions/2/feedback-suggestions' });
        requestWrapper.flush(feedbackSuggestions);

        tick();

        expect(requestWrapper.request.method).toBe('GET');
        expect(response).toEqual(feedbackSuggestions);
    }));

    it('should return no feedback suggestions when athena is disabled', fakeAsync(() => {
        let response: TextBlockRef[] | null = null;

        const mockProfileInfo = { activeProfiles: ['something'] } as ProfileInfo;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(mockProfileInfo));

        athenaService.getFeedbackSuggestions(1, 2).subscribe((suggestions: TextBlockRef[]) => {
            response = suggestions;
        });

        tick();

        expect(response).toEqual([]);
    }));
});
