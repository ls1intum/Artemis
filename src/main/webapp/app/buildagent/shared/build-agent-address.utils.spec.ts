import { describe, expect, it } from 'vitest';
import { createAddressToNameMap, extractHost, getAgentNameByAddress, looksLikeAddress } from './build-agent-address.utils';
import { BuildAgentInformation } from './entities/build-agent-information.model';

describe('build-agent-address.utils', () => {
    describe('looksLikeAddress', () => {
        it('should return true for IPv4 address with port', () => {
            expect(looksLikeAddress('[192.168.1.1]:5701')).toBe(true);
        });

        it('should return true for IPv6 address with port', () => {
            expect(looksLikeAddress('[2001:db8::1]:5702')).toBe(true);
        });

        it('should return true for hostname with port', () => {
            expect(looksLikeAddress('[build-agent-1]:5701')).toBe(true);
        });

        it('should return false for plain agent name', () => {
            expect(looksLikeAddress('build-agent-1')).toBe(false);
        });

        it('should return false for address without brackets', () => {
            expect(looksLikeAddress('192.168.1.1:5701')).toBe(false);
        });

        it('should return false for address without port', () => {
            expect(looksLikeAddress('[192.168.1.1]')).toBe(false);
        });

        it('should return false for empty string', () => {
            expect(looksLikeAddress('')).toBe(false);
        });
    });

    describe('extractHost', () => {
        it('should extract host from IPv4 address', () => {
            expect(extractHost('[192.168.1.1]:5701')).toBe('192.168.1.1');
        });

        it('should extract host from IPv6 address', () => {
            expect(extractHost('[2001:db8::1]:5702')).toBe('2001:db8::1');
        });

        it('should extract host from hostname address', () => {
            expect(extractHost('[build-agent-1]:5701')).toBe('build-agent-1');
        });

        it('should return original value if not matching format', () => {
            expect(extractHost('build-agent-1')).toBe('build-agent-1');
        });

        it('should return original value for empty string', () => {
            expect(extractHost('')).toBe('');
        });
    });

    describe('createAddressToNameMap', () => {
        const mockBuildAgents: BuildAgentInformation[] = [
            {
                buildAgent: {
                    name: 'build-agent-1',
                    memberAddress: '[192.168.1.1]:5701',
                    displayName: 'Build Agent 1',
                },
            },
            {
                buildAgent: {
                    name: 'build-agent-2',
                    memberAddress: '[2001:db8::1]:5702',
                    displayName: 'Build Agent 2',
                },
            },
        ];

        it('should create mapping from host to agent name', () => {
            const map = createAddressToNameMap(mockBuildAgents);
            expect(map.size).toBe(2);
            expect(map.get('192.168.1.1')).toBe('build-agent-1');
            expect(map.get('2001:db8::1')).toBe('build-agent-2');
        });

        it('should return empty map for empty agents list', () => {
            const map = createAddressToNameMap([]);
            expect(map.size).toBe(0);
        });

        it('should skip agents without address', () => {
            const agents: BuildAgentInformation[] = [
                {
                    buildAgent: {
                        name: 'build-agent-1',
                        displayName: 'Build Agent 1',
                    },
                },
            ];
            const map = createAddressToNameMap(agents);
            expect(map.size).toBe(0);
        });

        it('should skip agents without name', () => {
            const agents: BuildAgentInformation[] = [
                {
                    buildAgent: {
                        memberAddress: '[192.168.1.1]:5701',
                        displayName: 'Build Agent 1',
                    },
                },
            ];
            const map = createAddressToNameMap(agents);
            expect(map.size).toBe(0);
        });
    });

    describe('getAgentNameByAddress', () => {
        const mockMap = new Map<string, string>([
            ['192.168.1.1', 'build-agent-1'],
            ['2001:db8::1', 'build-agent-2'],
        ]);

        it('should return agent name for known address', () => {
            expect(getAgentNameByAddress('[192.168.1.1]:5701', mockMap)).toBe('build-agent-1');
        });

        it('should return agent name for known IPv6 address', () => {
            expect(getAgentNameByAddress('[2001:db8::1]:5702', mockMap)).toBe('build-agent-2');
        });

        it('should return original address for unknown address', () => {
            expect(getAgentNameByAddress('[10.0.0.1]:9999', mockMap)).toBe('[10.0.0.1]:9999');
        });

        it('should return empty string for undefined address', () => {
            expect(getAgentNameByAddress(undefined, mockMap)).toBe('');
        });

        it('should return empty string for empty address', () => {
            expect(getAgentNameByAddress('', mockMap)).toBe('');
        });

        it('should handle different ports for same host', () => {
            // Agent reconnected with different ephemeral port
            expect(getAgentNameByAddress('[192.168.1.1]:9999', mockMap)).toBe('build-agent-1');
        });
    });
});
