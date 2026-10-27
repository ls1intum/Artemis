import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getLatestResultOfStudentParticipation } from './participation.utils';

describe('getLatestResultOfStudentParticipation', () => {
    it.each([undefined, true, false])('ignores AI feedback with successful=%s, including in practice mode', (successful) => {
        const official: Result = { id: 1, rated: true, score: 80, completionDate: dayjs().subtract(2, 'minutes') };
        const practice: Result = { id: 2, rated: false, score: 90, completionDate: dayjs().subtract(1, 'minute') };
        const ai: Result = { id: 3, rated: true, score: successful ? 20 : undefined, successful, assessmentType: AssessmentType.AUTOMATIC_ATHENA, completionDate: dayjs() };
        const participation = { submissions: [{ results: [ai, official, practice] }] } as StudentParticipation;

        expect(getLatestResultOfStudentParticipation(participation, false)).toBe(official);
        expect(getLatestResultOfStudentParticipation(participation, true)).toBe(practice);
        expect(participation.submissions![0].results).toContain(ai);
        expect(getLatestResultOfStudentParticipation({ submissions: [{ results: [ai] }] } as StudentParticipation, true)).toBeUndefined();
    });
});
