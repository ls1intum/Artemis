import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseType } from 'app/entities/exercise.model';

export interface ExamExercise {
    id?: number;
    type?: ExerciseType;
    studentParticipations?: StudentParticipation[];
    navigationTitle?: string;
}
