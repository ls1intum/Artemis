import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('getLatestResultOfStudentParticipation', () => {
    const buildResult: Result = { id: 18, rated: true, assessmentType: AssessmentType.AUTOMATIC, completionDate: dayjs('2026-10-03T14:24:56Z') };
    // Requested alongside the build, the AI feedback completes after it
    const aiFeedback: Result = { id: 17, rated: true, successful: true, assessmentType: AssessmentType.AUTOMATIC_ATHENA, completionDate: dayjs('2026-10-03T14:24:57Z') };

    const participationOfType = (type: ParticipationType, results: Result[]) => ({ type, submissions: [{ id: 1, results }] }) as StudentParticipation;

    it('should not let finished AI feedback stand for a programming participation', () => {
        const participation = participationOfType(ParticipationType.PROGRAMMING, [buildResult, aiFeedback]);

        expect(getLatestResultOfStudentParticipation(participation, true, true)).toBe(buildResult);
    });

    it('should still show AI feedback on code that is being generated', () => {
        const inProgress: Result = { ...aiFeedback, successful: undefined, completionDate: dayjs().add(5, 'minutes') };
        const participation = participationOfType(ParticipationType.PROGRAMMING, [buildResult, inProgress]);

        expect(getLatestResultOfStudentParticipation(participation, false, true)).toBe(inProgress);
    });

    it('should keep AI feedback as the latest result of a text participation', () => {
        const participation = participationOfType(ParticipationType.STUDENT, [buildResult, aiFeedback]);

        expect(getLatestResultOfStudentParticipation(participation, true)).toBe(aiFeedback);
    });
});
