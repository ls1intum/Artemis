/**
 * Lifecycle events emitted by the Iris ask-user-mode pipeline over the chat websocket, used to drive
 * the client-side quiz timer and UI state.
 */
export enum IrisPipeEvent {
    BUILD_WITH_POINTS = 'BUILD_WITH_POINTS',
    USER_STARTS_QUIZ = 'USER_STARTS_QUIZ',
    FIRST_QUESTION = 'FIRST_QUESTION',
    NEXT_QUESTION = 'NEXT_QUESTION',
    QUIZ_FINISHED = 'QUIZ_FINISHED',
}
