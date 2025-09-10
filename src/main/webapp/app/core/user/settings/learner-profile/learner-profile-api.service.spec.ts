import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LearnerProfileApiService } from './learner-profile-api.service';
import { CourseLearnerProfileDTO } from './dto/course-learner-profile-dto.model';
import { LearnerProfileDTO } from './dto/learner-profile-dto.model';

// Mock data
const mockCourseLearnerProfiles: CourseLearnerProfileDTO[] = [
    Object.assign(new CourseLearnerProfileDTO(), {
        id: 1,
        courseId: 101,
        courseTitle: 'Course 101',
        aimForGradeOrBonus: 3,
        timeInvestment: 4,
        repetitionIntensity: 2,
    }),
];

const mockLearnerProfile: LearnerProfileDTO = new LearnerProfileDTO({
    id: 1,
    feedbackDetail: 2,
    feedbackFormality: 2,
});

describe('LearnerProfileApiService', () => {
    let service: LearnerProfileApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LearnerProfileApiService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(LearnerProfileApiService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should get course learner profiles for current user', async () => {
        const promise = service.getCourseLearnerProfilesForCurrentUser();
        const req = httpMock.expectOne('api/atlas/course-learner-profiles');
        expect(req.request.method).toBe('GET');
        req.flush(mockCourseLearnerProfiles);
        const result = await promise;
        expect(result).toEqual(mockCourseLearnerProfiles);
    });

    it('should update a course learner profile', async () => {
        const updatedProfile = Object.assign(new CourseLearnerProfileDTO(), {
            ...mockCourseLearnerProfiles[0],
            aimForGradeOrBonus: 4,
        });
        const promise = service.putUpdatedCourseLearnerProfile(updatedProfile);
        const req = httpMock.expectOne(`api/atlas/course-learner-profiles/${updatedProfile.id}`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(updatedProfile);
        req.flush(updatedProfile);
        const result = await promise;
        expect(result).toEqual(updatedProfile);
    });

    it('should get learner profile for current user', async () => {
        const promise = service.getLearnerProfileForCurrentUser();
        const req = httpMock.expectOne('api/atlas/learner-profile');
        expect(req.request.method).toBe('GET');
        req.flush(mockLearnerProfile);
        const result = await promise;
        expect(result).toEqual(mockLearnerProfile);
    });

    it('should update learner profile', async () => {
        const updatedProfile = new LearnerProfileDTO({
            id: 1,
            feedbackDetail: 3,
            feedbackFormality: 1,
        });
        const promise = service.putUpdatedLearnerProfile(updatedProfile);
        const req = httpMock.expectOne('api/atlas/learner-profile');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(updatedProfile);
        req.flush(updatedProfile);
        const result = await promise;
        expect(result).toEqual(updatedProfile);
    });
});
