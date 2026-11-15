/**
 * A change in the form of a content item that is edited in place and saved automatically.
 */
export interface UnitFormChange<T> {
    /** The whole form, as the save request needs it. */
    data: T;
    /** Save right away, as for a choice such as the release date; typed text is saved after a pause instead. */
    immediate: boolean;
    /** Whether the form can be saved as it is; an invalid form waits until it is corrected. */
    valid: boolean;
}
