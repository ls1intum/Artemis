import { TestBed } from '@angular/core/testing';
import { ProgrammingLanguageFeatureService } from './programming-language-feature.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('ProgrammingLanguageFeatureService', () => {
    let service: ProgrammingLanguageFeatureService;
    let mockProfileService: jest.Mocked<ProfileService>;

    const mockProgrammingLanguageFeatures: ProgrammingLanguageFeature[] = [
        {
            programmingLanguage: ProgrammingLanguage.JAVA,
            sequentialTestRuns: true,
            staticCodeAnalysis: true,
            plagiarismCheckSupported: true,
            packageNameRequired: true,
            checkoutSolutionRepositoryAllowed: true,
            projectTypes: ['PLAIN_GRADLE', 'GRADLE_WRAPPER'] as any,
            auxiliaryRepositoriesSupported: true,
        },
        {
            programmingLanguage: ProgrammingLanguage.PYTHON,
            sequentialTestRuns: false,
            staticCodeAnalysis: false,
            plagiarismCheckSupported: true,
            packageNameRequired: false,
            checkoutSolutionRepositoryAllowed: false,
            projectTypes: ['PLAIN'] as any,
            auxiliaryRepositoriesSupported: false,
        },
    ];

    beforeEach(() => {
        const profileSpy = {
            getProfileInfo: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [ProgrammingLanguageFeatureService, { provide: ProfileService, useValue: profileSpy }],
        });

        mockProfileService = TestBed.inject(ProfileService) as jest.Mocked<ProfileService>;
        mockProfileService.getProfileInfo.mockReturnValue({
            programmingLanguageFeatures: mockProgrammingLanguageFeatures,
        } as any);

        service = TestBed.inject(ProgrammingLanguageFeatureService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should initialize programming language features from profile service', () => {
        expect(mockProfileService.getProfileInfo).toHaveBeenCalled();
    });

    describe('getProgrammingLanguageFeature', () => {
        it('should return the programming language feature for a supported language', () => {
            const feature = service.getProgrammingLanguageFeature(ProgrammingLanguage.JAVA);

            expect(feature).toEqual(mockProgrammingLanguageFeatures[0]);
        });

        it('should return undefined for an unsupported language', () => {
            const feature = service.getProgrammingLanguageFeature(ProgrammingLanguage.C);

            expect(feature).toBeUndefined();
        });

        it('should return the correct feature for Python', () => {
            const feature = service.getProgrammingLanguageFeature(ProgrammingLanguage.PYTHON);

            expect(feature).toEqual(mockProgrammingLanguageFeatures[1]);
        });
    });

    describe('supportsProgrammingLanguage', () => {
        it('should return true for a supported programming language', () => {
            const isSupported = service.supportsProgrammingLanguage(ProgrammingLanguage.JAVA);

            expect(isSupported).toBeTrue();
        });

        it('should return false for an unsupported programming language', () => {
            const isSupported = service.supportsProgrammingLanguage(ProgrammingLanguage.C);

            expect(isSupported).toBeFalse();
        });

        it('should return true for Python', () => {
            const isSupported = service.supportsProgrammingLanguage(ProgrammingLanguage.PYTHON);

            expect(isSupported).toBeTrue();
        });
    });
});
