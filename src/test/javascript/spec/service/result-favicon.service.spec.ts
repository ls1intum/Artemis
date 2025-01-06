import { TestBed } from '@angular/core/testing';
import { ResultFaviconService } from 'app/exercises/shared/result/result-favicon/result-favicon.service';
import { MissingResultInformation, Result, ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import * as resultUtils from 'app/exercises/shared/result/result.utils';
import { Exercise } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';

describe('ResultFaviconService', () => {
    let service: ResultFaviconService;

    // Mock DOM elements
    let faviconLink1: HTMLLinkElement;
    let faviconLink2: HTMLLinkElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ResultFaviconService],
        });
        service = TestBed.inject(ResultFaviconService);

        // Create mock favicons in DOM
        faviconLink1 = document.createElement('link');
        faviconLink1.rel = 'icon';
        faviconLink1.href = 'http://example.com/favicon1.png';

        faviconLink2 = document.createElement('link');
        faviconLink2.rel = 'shortcut icon';
        faviconLink2.href = 'http://example.com/favicon2.png';

        document.head.appendChild(faviconLink1);
        document.head.appendChild(faviconLink2);

        // Mock image loading and canvas rendering
        jest.spyOn(HTMLImageElement.prototype, 'addEventListener').mockImplementation((type, callback) => {
            if (type === 'load' && callback instanceof Function) {
                callback(new Event('load'));
            }
        });
        jest.spyOn(CanvasRenderingContext2D.prototype, 'drawImage').mockReturnValue();
        jest.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/png' + Math.random() + new Date().valueOf());
    });

    afterEach(() => {
        // Clean up the DOM after each test
        document.head.removeChild(faviconLink1);
        document.head.removeChild(faviconLink2);

        jest.clearAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('updateFavicon()', () => {
        it('should replace the favicon and set up intervals when building', () => {
            const mockResult: Result = {};
            const mockExercise: Exercise = {};
            const mockParticipation: Participation = {};
            const mockMissingResult: MissingResultInformation = {};
            const isBuilding = true;
            const isQueued = false;

            // Mock result utils
            jest.spyOn(resultUtils, 'evaluateTemplateStatus').mockReturnValue(ResultTemplateStatus.IS_BUILDING);
            jest.spyOn(resultUtils, 'getResultIconClass').mockReturnValue({
                icon: [1024, 1024, [], '', 'M0 0 H1024 V1024 H0 Z'],
                prefix: 'fas',
                iconName: '0',
            });
            jest.spyOn(resultUtils, 'getTextColorClass').mockReturnValue('text-success');
            jest.spyOn(global, 'setInterval');

            service.updateFavicon(mockResult, mockExercise, mockParticipation, isBuilding, isQueued, mockMissingResult);

            // Both favicons should have been replaced with a data URL
            expect(faviconLink1.href).toContain('data:image/png');
            expect(faviconLink2.href).toContain('data:image/png');

            // An interval should have been set
            expect(setInterval).toHaveBeenCalled();
        });

        it('should not set intervals if not building or queued', () => {
            const mockResult: Result = {};
            const mockExercise: Exercise = {};
            const mockParticipation: Participation = {};
            const mockMissingResult: MissingResultInformation = {};
            const isBuilding = false;
            const isQueued = false;

            jest.spyOn(resultUtils, 'evaluateTemplateStatus').mockReturnValue(ResultTemplateStatus.HAS_FEEDBACK);
            jest.spyOn(resultUtils, 'getResultIconClass').mockReturnValue({
                icon: [1024, 1024, [], '', 'M0 0 H1024 V1024 H0 Z'],
                prefix: 'fas',
                iconName: '0',
            });
            jest.spyOn(resultUtils, 'getTextColorClass').mockReturnValue('text-success');
            jest.spyOn(global, 'setInterval');

            service.updateFavicon(mockResult, mockExercise, mockParticipation, isBuilding, isQueued, mockMissingResult);

            // Both favicons should have been replaced with a data URL
            expect(faviconLink1.href).toContain('data:image/png');
            expect(faviconLink2.href).toContain('data:image/png');

            // No intervals should have been set
            expect(setInterval).not.toHaveBeenCalled();
        });
    });

    describe('removeFavicon()', () => {
        it('should restore original favicons', () => {
            const mockResult: Result = {};
            const mockExercise: Exercise = {};
            const mockParticipation: Participation = {};
            const mockMissingResult: MissingResultInformation = {};

            jest.spyOn(resultUtils, 'evaluateTemplateStatus').mockReturnValue(ResultTemplateStatus.IS_BUILDING);
            jest.spyOn(resultUtils, 'getResultIconClass').mockReturnValue({
                icon: [1024, 1024, [], '', 'M0 0 H1024 V1024 H0 Z'],
                prefix: 'fas',
                iconName: '0',
            });
            jest.spyOn(resultUtils, 'getTextColorClass').mockReturnValue('text-success');

            service.updateFavicon(mockResult, mockExercise, mockParticipation, true, false, mockMissingResult);

            // Confirm it replaced the favicons
            expect(faviconLink1.href).toContain('data:image/png');
            expect(faviconLink2.href).toContain('data:image/png');

            // Now remove
            service.removeFavicon();

            // The hrefs should be restored
            expect(faviconLink1.href).toBe('http://example.com/favicon1.png');
            expect(faviconLink2.href).toBe('http://example.com/favicon2.png');
        });

        it('should do nothing if no replacement was done yet', () => {
            // Just call removeFavicon without having called updateFavicon
            service.removeFavicon();

            // The hrefs should remain the same
            expect(faviconLink1.href).toBe('http://example.com/favicon1.png');
            expect(faviconLink2.href).toBe('http://example.com/favicon2.png');
        });
    });
});
