import dayjs from 'dayjs/esm';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { hasSubmittedForStatistics } from './course-statistics.utils';

describe('hasSubmittedForStatistics', () => {
    it('retains submission status when pending content is withheld', () => {
        expect(hasSubmittedForStatistics({ ...new StudentParticipation(), hasSubmitted: true, submissions: [] })).toBe(true);
    });

    it('accepts a new submission after an earlier overview reported no submission', () => {
        expect(hasSubmittedForStatistics({ ...new StudentParticipation(), hasSubmitted: false, submissions: [{ submitted: true }] })).toBe(true);
    });

    it('distinguishes a dated draft from a programming commit', () => {
        const submissions = [{ submitted: false, submissionDate: dayjs() }];
        expect(hasSubmittedForStatistics({ ...new StudentParticipation(), submissions })).toBe(false);
        expect(hasSubmittedForStatistics({ ...new StudentParticipation(ParticipationType.PROGRAMMING), submissions })).toBe(true);
    });
});
