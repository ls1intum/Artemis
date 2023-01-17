import { BaseEntity } from 'app/shared/model/base-entity';
import { ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from './course.model';

export interface ExamExercise extends BaseEntity {
    navigationTitle?: string;
    title?: string;
    maxPoints?: number;
    type?: ExerciseType;
    studentParticipations?: Array<StudentParticipation>;
    includedInOverallScore?: IncludedInOverallScore;
    course?: Course;
    exerciseGroup?: ExerciseGroup;
    exampleSolutionPublished?: boolean;
    bonusPoints?: number;
}
