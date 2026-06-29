/**
 * A request to point the student to a position in the lecture combined view, emitted when a COMMAND
 * (point-out) message arrives or when the student clicks its marker in the chat history.
 */
export interface IrisPointOutNavigation {
    lectureUnitId: number;
    page?: number;
    timestamp?: number;
    /**
     * When true (marker clicked), the combined view is (re)opened even if currently closed.
     * When false (auto-navigation on message arrival), navigation only happens if it is already open.
     */
    forceOpen: boolean;
}
