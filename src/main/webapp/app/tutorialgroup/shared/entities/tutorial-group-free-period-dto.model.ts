import { convertDateFromClient } from 'app/shared/util/date.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

/**
 * Data Transfer Object representing a tutorial group free period.
 *
 * All date fields are serialized as ISO 8601 strings.
 */
export interface TutorialGroupFreePeriodDTO {
    id?: number;
    start: string;
    end: string;
    reason?: string;
    tutorialGroupConfigurationId: number;
}

/**
 * Converts a {@link TutorialGroupFreePeriod} entity into a
 * {@link TutorialGroupFreePeriodDTO}.
 *
 * If the provided entity is undefined, the function returns undefined.
 * This allows safe usage in optional mapping chains.
 *
 * @param entity the free period entity to convert
 * @returns the corresponding DTO or undefined if the entity is undefined
 */
export const entityToTutorialGroupFreePeriodDTO = (entity?: TutorialGroupFreePeriod): TutorialGroupFreePeriodDTO | undefined => {
    if (!entity) {
        return undefined;
    }
    return {
        id: entity.id,
        start: convertDateFromClient(entity.start)!,
        end: convertDateFromClient(entity.end)!,
        reason: entity.reason,
        tutorialGroupConfigurationId: entity.tutorialGroupConfiguration!.id!,
    };
};
