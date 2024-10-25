package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

class SarifParserTest {

    static class FullDescriptionCategorizer implements RuleCategorizer {

        @Override
        public String categorizeRule(ReportingDescriptor rule) {
            return rule.getFullDescription().orElseThrow().getText();
        }
    }

    @Test
    void testEmpty() {
        String report = """
                {
                    "runs": [
                        {
                            "tool": {
                                "driver": {
                                    "rules": []
                                }
                            },
                            "results": []
                        }
                    ]
                }
                """;

        SarifParser parser = new SarifParser(StaticCodeAnalysisTool.OTHER, new IdCategorizer());
        StaticCodeAnalysisReportDTO parsedReport = parser.parse(report);

        assertThat(parsedReport.issues()).isEmpty();
    }

    @Test
    void testMetadata() {
        String report = """
                {
                    "runs": [
                        {
                            "tool": {
                                "driver": {
                                    "rules": [
                                        {
                                            "fullDescription": {
                                                "text": "CATEGORY"
                                            },
                                            "id": "RULE_ID"
                                        }
                                    ]
                                }
                            },
                            "results": [
                                {
                                    "level": "error",
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uri": "file:///path/to/file.txt"
                                                },
                                                "region": {
                                                    "startLine": 10,
                                                    "startColumn": 20,
                                                    "endLine": 30,
                                                    "endColumn": 40
                                                }
                                            }
                                        }
                                    ],
                                    "message": {
                                        "text": "MESSAGE"
                                    },
                                    "ruleId": "RULE_ID",
                                    "ruleIndex": 0
                                }
                            ]
                        }
                    ]
                }
                """;
        StaticCodeAnalysisIssue expected = new StaticCodeAnalysisIssue("/path/to/file.txt", 10, 30, 20, 40, "RULE_ID", "CATEGORY", "MESSAGE", "error", null);

        SarifParser parser = new SarifParser(StaticCodeAnalysisTool.OTHER, new FullDescriptionCategorizer());
        StaticCodeAnalysisReportDTO parsedReport = parser.parse(report);

        assertThat(parsedReport.issues()).singleElement().isEqualTo(expected);
    }

    @Test
    void testMessageLookup() {
        String report = """
                {
                    "runs": [
                        {
                            "tool": {
                                "driver": {
                                    "rules": [
                                        {
                                            "fullDescription": {
                                                "text": "FULL_DESCRIPTION"
                                            },
                                            "id": "A001",
                                            "messageStrings": {
                                                "MESSAGE_ID_A": {
                                                    "text": "RULE_MESSAGE_CONTENT_A"
                                                }
                                            }
                                        }
                                    ],
                                    "globalMessageStrings": {
                                        "MESSAGE_ID_A": {
                                            "text": "GLOBAL_MESSAGE_CONTENT_A"
                                        },
                                        "MESSAGE_ID_B": {
                                            "text": "GLOBAL_MESSAGE_CONTENT_B"
                                        }
                                    }
                                }
                            },
                            "results": [
                                {
                                    "level": "error",
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uri": "file:///path/to/file.txt"
                                                },
                                                "region": {
                                                    "startLine": 1
                                                }
                                            }
                                        }
                                    ],
                                    "message": {
                                        "id": "MESSAGE_ID_A"
                                    },
                                    "ruleId": "A001",
                                    "ruleIndex": 0
                                },
                                {
                                    "level": "error",
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uri": "file:///path/to/file.txt"
                                                },
                                                "region": {
                                                    "startLine": 1
                                                }
                                            }
                                        }
                                    ],
                                    "message": {
                                        "id": "MESSAGE_ID_B"
                                    },
                                    "ruleId": "B001"
                                }
                            ]
                        }
                    ]
                }
                """;

        SarifParser parser = new SarifParser(StaticCodeAnalysisTool.OTHER, new IdCategorizer());
        StaticCodeAnalysisReportDTO parsedReport = parser.parse(report);

        assertThat(parsedReport.issues()).anyMatch(issue -> issue.rule().equals("A001") && issue.message().equals("RULE_MESSAGE_CONTENT_A"))
                .anyMatch(issue -> issue.rule().equals("B001") && issue.message().equals("GLOBAL_MESSAGE_CONTENT_B"));
    }

    @Test
    void testHierarchicalRuleIdLookup() {
        String report = """
                {
                    "runs": [
                        {
                            "tool": {
                                "driver": {
                                    "rules": [
                                        {
                                            "fullDescription": {
                                                "text": "FULL_DESCRIPTION"
                                            },
                                            "id": "A123"
                                        }
                                    ]
                                }
                            },
                            "results": [
                                {
                                    "level": "error",
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uri": "file:///path/to/file.txt"
                                                },
                                                "region": {
                                                    "startLine": 1
                                                }
                                            }
                                        }
                                    ],
                                    "message": {
                                        "text": "MESSAGE"
                                    },
                                    "ruleId": "A123/subrule"
                                }
                            ]
                        }
                    ]
                }
                """;

        SarifParser parser = new SarifParser(StaticCodeAnalysisTool.OTHER, new FullDescriptionCategorizer());
        StaticCodeAnalysisReportDTO parsedReport = parser.parse(report);

        assertThat(parsedReport.issues()).singleElement().matches(issue -> issue.category().equals("FULL_DESCRIPTION"));
    }

    @Test
    void testRuleIndexLookup() {
        String report = """
                {
                    "runs": [
                        {
                            "tool": {
                                "driver": {
                                    "rules": [
                                        {
                                            "fullDescription": {
                                                "text": "FULL_DESCRIPTION_A"
                                            },
                                            "id": "RULE_ID"
                                        },
                                        {
                                            "fullDescription": {
                                                "text": "FULL_DESCRIPTION_B"
                                            },
                                            "id": "RULE_ID"
                                        }
                                    ]
                                }
                            },
                            "results": [
                                {
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uri": "file:///path/to/file.txt"
                                                },
                                                "region": {
                                                    "startLine": 1
                                                }
                                            }
                                        }
                                    ],
                                    "message": {
                                        "text": "MESSAGE"
                                    },
                                    "ruleId": "RULE_ID",
                                    "ruleIndex": 1
                                }
                            ]
                        }
                    ]
                }
                """;

        SarifParser parser = new SarifParser(StaticCodeAnalysisTool.OTHER, new FullDescriptionCategorizer());
        StaticCodeAnalysisReportDTO parsedReport = parser.parse(report);

        assertThat(parsedReport.issues()).singleElement().matches(issue -> issue.category().equals("FULL_DESCRIPTION_B"));
    }

    @Test
    void testInvalidJSON() {
        String report = """
                {
                    "runs": [
                        {
                    ]
                }
                """;

        SarifParser parser = new SarifParser(StaticCodeAnalysisTool.OTHER, new IdCategorizer());
        assertThatThrownBy(() -> parser.parse(report)).hasCauseInstanceOf(JsonProcessingException.class);
    }
}
