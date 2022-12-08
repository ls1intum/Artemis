import { Injectable } from '@angular/core';
import { CourseScoresForStudentStatisticsDTO } from 'app/course/course-scores-for-student-statistics-dto';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ExerciseType, ExerciseTypeTOTAL } from 'app/entities/exercise.model';

@Injectable({ providedIn: 'root' })
export class ScoresStorageService {
    private storedScoresPerExerciseType: Map<number, Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresForStudentStatisticsDTO>> = new Map();

    private participantScores: Map<number, ParticipantScoreDTO[]> = new Map();

    getStoredScoresPerExerciseType(courseId: number) {
        return this.storedScoresPerExerciseType.get(courseId);
    }

    setStoredScoresPerExerciseType(courseId: number, scoresPerExerciseType: Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresForStudentStatisticsDTO>) {
        this.storedScoresPerExerciseType.set(courseId, scoresPerExerciseType);
    }

    getParticipantScores(courseId: number) {
        return this.participantScores.get(courseId);
    }

    setParticipantScores(courseId: number, participantScores: ParticipantScoreDTO[]) {
        this.participantScores.set(courseId, participantScores);
    }
}
