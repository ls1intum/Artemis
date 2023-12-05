import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

export interface ExamExercise {
    id?: number;
    type?: ExerciseType;
    exerciseGroup?: ExerciseGroup;
    studentParticipations?: StudentParticipation[];
    navigationTitle?: string;
    overviewTitle?: string;
    maxPoints?: number;
}
