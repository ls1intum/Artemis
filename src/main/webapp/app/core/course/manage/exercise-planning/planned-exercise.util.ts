import { Dayjs } from 'dayjs/esm';

export function getFirstAvailableDatePropertyOf(plannedExerciseOrDto: { releaseDate?: Dayjs; startDate?: Dayjs; dueDate?: Dayjs; assessmentDueDate?: Dayjs }): Dayjs {
    if (plannedExerciseOrDto.releaseDate) return plannedExerciseOrDto.releaseDate;
    if (plannedExerciseOrDto.startDate) return plannedExerciseOrDto.startDate;
    if (plannedExerciseOrDto.dueDate) return plannedExerciseOrDto.dueDate;
    return plannedExerciseOrDto.assessmentDueDate!;
}
