import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';

describe('ScoresStorageService', () => {
    let scoresStorageService = new ScoresStorageService();

    it('should return an undefined result for an undefined participation id', () => {
        expect(scoresStorageService.getStoredParticipationResult(undefined)).toBeUndefined();
    });
});
