import { BuildAgentInformation } from './entities/build-agent-information.model';

/**
 * Information about a build agent for display and navigation purposes.
 */
export interface AgentInfo {
    /** The agent's short name (stable identifier, used for navigation) */
    name: string;
    /** The agent's display name (human-readable, used for display) */
    displayName: string;
}

/**
 * Checks if a value looks like a Hazelcast member address (e.g., "[host]:port").
 */
export function looksLikeAddress(value: string): boolean {
    return /^\[.+\]:\d+$/.test(value);
}

/**
 * Extracts the host portion from a Hazelcast member address.
 * For "[192.168.1.1]:5701" returns "192.168.1.1".
 * For "[2001:db8::1]:5702" returns "2001:db8::1".
 * Returns the original value if it doesn't match the expected format.
 */
export function extractHost(address: string): string {
    const match = address.match(/^\[(.+)\]:\d+$/);
    return match ? match[1] : address;
}

/**
 * Creates a mapping from build agent host (without port) to agent info (name and displayName).
 * Uses host-only matching to handle Hazelcast ephemeral ports that change on reconnection.
 */
export function createAddressToAgentInfoMap(agents: BuildAgentInformation[]): Map<string, AgentInfo> {
    const map = new Map<string, AgentInfo>();
    for (const agent of agents) {
        const address = agent.buildAgent?.memberAddress;
        const name = agent.buildAgent?.name;
        const displayName = agent.buildAgent?.displayName;
        if (address && name) {
            const host = extractHost(address);
            map.set(host, {
                name,
                displayName: displayName || name,
            });
        }
    }
    return map;
}

/**
 * Creates a mapping from build agent host (without port) to agent name.
 * Uses host-only matching to handle Hazelcast ephemeral ports that change on reconnection.
 * @deprecated Use createAddressToAgentInfoMap instead for access to both name and displayName
 */
export function createAddressToNameMap(agents: BuildAgentInformation[]): Map<string, string> {
    const map = new Map<string, string>();
    for (const agent of agents) {
        const address = agent.buildAgent?.memberAddress;
        const name = agent.buildAgent?.name;
        if (address && name) {
            const host = extractHost(address);
            map.set(host, name);
        }
    }
    return map;
}

/**
 * Gets the agent info for a given address using the address-to-agent-info map.
 * Returns the agent info if found, otherwise returns undefined.
 */
export function getAgentInfoByAddress(address: string | undefined, addressToAgentInfoMap: Map<string, AgentInfo>): AgentInfo | undefined {
    if (!address) return undefined;
    const host = extractHost(address);
    return addressToAgentInfoMap.get(host);
}

/**
 * Gets the agent name for a given address using the address-to-name map.
 * Returns the agent name if found, otherwise returns the original address.
 */
export function getAgentNameByAddress(address: string | undefined, addressToNameMap: Map<string, string>): string {
    if (!address) return '';
    const host = extractHost(address);
    return addressToNameMap.get(host) ?? address;
}
