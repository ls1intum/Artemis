import { SimpleChanges } from '@angular/core';

export const hasParticipationChanged = (changes: SimpleChanges) => {
    return (
        changes.participation &&
        changes.participation.currentValue &&
        (!changes.participation.previousValue || changes.participation.previousValue.id !== changes.participation.currentValue.id)
    );
};
