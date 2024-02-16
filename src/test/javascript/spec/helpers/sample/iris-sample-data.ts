import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IrisArtemisClientMessage, IrisSender, IrisServerMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { ExerciseComponent, IrisExercisePlan, IrisExercisePlanStep, IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisChatWebsocketDTO, IrisChatWebsocketMessageType } from 'app/iris/chat-websocket.service';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { IrisCodeEditorWebsocketDTO, IrisCodeEditorWebsocketMessageType, StepExecutionException, StepExecutionSuccess } from 'app/iris/code-editor-websocket.service';

const map = new Map<string, any>();
map.set('model', 'gpt-4');

export const mockMessageContent = {
    textContent: 'Hello, world!',
} as IrisTextMessageContent;

export const mockExercisePlanStep = {
    id: 2,
    plan: 2,
    component: ExerciseComponent.PROBLEM_STATEMENT,
} as IrisExercisePlanStep;

export const mockExercisePlanStepTemplate = {
    id: 3,
    plan: 2,
    component: ExerciseComponent.TEMPLATE_REPOSITORY,
} as IrisExercisePlanStep;

export const mockExercisePlanStepSolution = {
    id: 4,
    plan: 2,
    component: ExerciseComponent.SOLUTION_REPOSITORY,
} as IrisExercisePlanStep;

export const mockExercisePlanStepTest = {
    id: 5,
    plan: 2,
    component: ExerciseComponent.TEST_REPOSITORY,
} as IrisExercisePlanStep;
export const mockExercisePlan = {
    id: 2,
    steps: [mockExercisePlanStep],
} as IrisExercisePlan;
export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

export const mockServerPlanMessage = {
    sender: IrisSender.LLM,
    id: 2,
    content: [mockExercisePlan],
    sentAt: dayjs(),
} as IrisServerMessage;

export const mockServerMessage = {
    sender: IrisSender.LLM,
    id: 1,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisServerMessage;

export const mockArtemisClientMessage = {
    sender: IrisSender.ARTEMIS_CLIENT,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisArtemisClientMessage;

export const mockPSExecutionSuccess = {
    messageId: 1,
    planId: 1,
    stepId: 1,
    component: ExerciseComponent.PROBLEM_STATEMENT,
    updatedProblemStatement: 'hello ps',
} as StepExecutionSuccess;

export const mockSolutionExecutionSuccess = {
    messageId: 1,
    planId: 1,
    stepId: 2,
    component: ExerciseComponent.SOLUTION_REPOSITORY,
    paths: ['test'],
} as StepExecutionSuccess;

export const mockTemplateExecutionSuccess = {
    messageId: 1,
    planId: 1,
    stepId: 3,
    component: ExerciseComponent.TEMPLATE_REPOSITORY,
    paths: [''],
} as StepExecutionSuccess;

export const mockTestExecutionSuccess = {
    messageId: 1,
    planId: 1,
    stepId: 4,
    component: ExerciseComponent.TEST_REPOSITORY,
    paths: [''],
} as StepExecutionSuccess;

export const mockStepExecutionException = {
    messageId: 1,
    planId: 1,
    stepId: 2,
    errorMessage: 'error',
    errorTranslationKey: IrisErrorMessageKey.INTERNAL_PYRIS_ERROR,
} as StepExecutionException;

export const mockWebsocketMessage = {
    type: IrisChatWebsocketMessageType.MESSAGE,
    message: mockServerMessage,
} as IrisChatWebsocketDTO;

export const mockClientMessage = {
    sender: IrisSender.USER,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisUserMessage;

export const mockWebsocketServerMessage = {
    type: IrisChatWebsocketMessageType.MESSAGE,
    message: mockServerMessage,
} as IrisChatWebsocketDTO;

export const mockWebsocketClientMessage = {
    type: IrisChatWebsocketMessageType.MESSAGE,
    message: mockClientMessage,
} as IrisChatWebsocketDTO;

export const mockWebsocketKnownError = {
    type: IrisChatWebsocketMessageType.ERROR,
    errorTranslationKey: IrisErrorMessageKey.NO_MODEL_AVAILABLE,
    translationParams: map,
} as IrisChatWebsocketDTO;

export const mockWebsocketUnknownError = {
    type: IrisChatWebsocketMessageType.ERROR,
} as IrisChatWebsocketDTO;

export const mockCodeEditorWebsocketServerMessage = {
    type: IrisCodeEditorWebsocketMessageType.MESSAGE,
    message: mockServerMessage,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketClientMessage = {
    type: IrisCodeEditorWebsocketMessageType.MESSAGE,
    message: mockClientMessage,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketStepSuccess = {
    type: IrisCodeEditorWebsocketMessageType.STEP_SUCCESS,
    stepExecutionSuccess: mockPSExecutionSuccess,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketSolutionSuccess = {
    type: IrisCodeEditorWebsocketMessageType.STEP_SUCCESS,
    stepExecutionSuccess: mockSolutionExecutionSuccess,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketTemplateSuccess = {
    type: IrisCodeEditorWebsocketMessageType.STEP_SUCCESS,
    stepExecutionSuccess: mockTemplateExecutionSuccess,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketTestSuccess = {
    type: IrisCodeEditorWebsocketMessageType.STEP_SUCCESS,
    stepExecutionSuccess: mockTestExecutionSuccess,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketStepException = {
    type: IrisCodeEditorWebsocketMessageType.STEP_EXCEPTION,
    executionException: mockStepExecutionException,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketKnownError = {
    type: IrisCodeEditorWebsocketMessageType.ERROR,
    errorTranslationKey: IrisErrorMessageKey.NO_MODEL_AVAILABLE,
    translationParams: map,
} as IrisCodeEditorWebsocketDTO;

export const mockCodeEditorWebsocketUnknownError = {
    type: IrisCodeEditorWebsocketMessageType.ERROR,
} as IrisCodeEditorWebsocketDTO;

export const mockConversation = {
    id: 1,
    exercise: irisExercise,
    messages: [mockClientMessage, mockServerMessage],
} as IrisSession;

export const mockPlanConversation = {
    id: 2,
    exercise: irisExercise,
    messages: [mockClientMessage, mockServerPlanMessage],
} as IrisSession;

export const mockState = {
    messages: [],
    isLoading: false,
    error: null,
    numNewMessages: 0,
    sessionId: 0,
    serverResponseTimeout: null,
    currentMessageCount: -1,
    rateLimit: -1,
    rateLimitTimeframeHours: -1,
};
