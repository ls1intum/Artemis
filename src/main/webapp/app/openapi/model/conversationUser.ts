/**
 * Artemis Application Server API
 *
 * Contact: krusche@tum.de
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


export interface ConversationUser { 
    id?: number;
    login?: string;
    name?: string;
    firstName?: string;
    lastName?: string;
    imageUrl?: string;
    isInstructor?: boolean;
    isEditor?: boolean;
    isTeachingAssistant?: boolean;
    isStudent?: boolean;
    isChannelModerator?: boolean;
    isRequestingUser?: boolean;
}

