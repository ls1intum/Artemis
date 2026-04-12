import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';

/**
 * Mirrors the server-side IrisMessageContentResponseDTO record.
 * Represents a single content block within a message as received over the wire.
 */
export interface IrisMessageContentResponseDTO {
    id?: number;
    type: string;
    textContent?: string;
    attributes?: string;
}

/**
 * Mirrors the server-side IrisMessageResponseDTO record.
 * This is the wire format for messages returned by REST endpoints and WebSocket payloads.
 * Use this type at the HTTP/WebSocket boundary; map to IrisMessage domain types for internal use.
 */
export interface IrisMessageResponseDTO {
    id?: number;
    sentAt?: string;
    helpful?: boolean;
    sender: string;
    content: IrisMessageContentResponseDTO[];
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
    messageDifferentiator?: number;
}
