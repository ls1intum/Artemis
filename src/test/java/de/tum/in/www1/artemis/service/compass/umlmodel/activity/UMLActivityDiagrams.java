package de.tum.in.www1.artemis.service.compass.umlmodel.activity;

final class UMLActivityDiagrams {

    static final String ACTIVITY_MODEL_1 = """
            {
                "version": "2.0.0",
                "type": "ActivityDiagram",
                "size": {
                    "width": 1148.7999877929688,
                    "height": 501.6000061035156
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "605cfbee-defb-42fc-bc34-f2f7648c1901",
                        "name": "",
                        "type": "ActivityInitialNode",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 51.5,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "0e107a33-4250-4964-9955-96910726b5b3",
                        "name": "Open website",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 91.5,
                            "y": 55.50001525878906,
                            "width": 130,
                            "height": 40
                        }
                    },
                    {
                        "id": "670e7b53-f8ee-47d3-a4af-c7cde1ddcc70",
                        "name": "",
                        "type": "ActivityForkNode",
                        "owner": null,
                        "bounds": {
                            "x": 340.6999969482422,
                            "y": 41.5,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "5909d89c-5800-4a6d-9637-5874600be2c2",
                        "name": "choose pizza size",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 442.6999969482422,
                            "y": 16.500015258789062,
                            "width": 190,
                            "height": 40
                        }
                    },
                    {
                        "id": "adc031c0-61a1-47f3-bdab-9931abe3aaad",
                        "name": "choose pizza type",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 459.6999969482422,
                            "y": 58.50001525878906,
                            "width": 160,
                            "height": 30
                        }
                    },
                    {
                        "id": "9560ae3f-2402-49d3-98aa-24071ffba7aa",
                        "name": "choose toppings",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 458.6999969482422,
                            "y": 87.50001525878906,
                            "width": 160,
                            "height": 40
                        }
                    },
                    {
                        "id": "5c85bafa-3def-44f4-b9c2-5026bc2c7bcd",
                        "name": "pineapples?",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 437.6999969482422,
                            "y": 180.5,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "aa481227-72b2-4c67-841e-c9d276753726",
                        "name": "out of pineapples",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 441.6999969482422,
                            "y": 297.5,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "8f4a1f8a-5ace-43a9-90f8-14f5798fae4f",
                        "name": "cancel order",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 137.6999969482422,
                            "y": 301.5,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "67a7325d-f96c-4a20-84e0-a31617d25f1c",
                        "name": "",
                        "type": "ActivityFinalNode",
                        "owner": null,
                        "bounds": {
                            "x": 14.199996948242188,
                            "y": 315.50001525878906,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "6e074c7b-999f-4f71-a395-7c44cf86b11c",
                        "name": "",
                        "type": "ActivityForkNode",
                        "owner": null,
                        "bounds": {
                            "x": 722.6999969482422,
                            "y": 35.5,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "84188d46-8bd3-4724-8564-152b2d16b1ab",
                        "name": "another pizza?",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 813.6999969482422,
                            "y": 38.5,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "ea3a736f-089e-49af-9bde-0773373f60ab",
                        "name": "enter payment method",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 815.0999908447266,
                            "y": 171.3000030517578,
                            "width": 200,
                            "height": 40
                        }
                    },
                    {
                        "id": "d8eaa3c3-0e7c-4a0a-8672-82ca223c5fde",
                        "name": "payment methode declined?",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 786.6999664306641,
                            "y": 273.29998779296875,
                            "width": 210,
                            "height": 60
                        }
                    },
                    {
                        "id": "6a7bc8de-6482-4e14-95fc-bfc3834c4be4",
                        "name": "enter adress",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 816.6999664306641,
                            "y": 377.3000030517578,
                            "width": 140,
                            "height": 40
                        }
                    },
                    {
                        "id": "1659b3dc-275b-43a5-be71-91728819a570",
                        "name": "pay and send out order",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 587.6999664306641,
                            "y": 438.3000030517578,
                            "width": 200,
                            "height": 40
                        }
                    },
                    {
                        "id": "49a16d6f-f31a-44a9-8be7-b74ccb0aefed",
                        "name": "",
                        "type": "ActivityFinalNode",
                        "owner": null,
                        "bounds": {
                            "x": 440.19996643066406,
                            "y": 429.3000030517578,
                            "width": 45,
                            "height": 45
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "64c1e8c2-36a6-457d-b4d8-922530605d43",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 45,
                            "y": 75.50001525878906,
                            "width": 46.5,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 46.5,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "605cfbee-defb-42fc-bc34-f2f7648c1901"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "0e107a33-4250-4964-9955-96910726b5b3"
                        }
                    },
                    {
                        "id": "3bb4fded-2c6b-4723-b25e-01b9a9a0798d",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 221.5,
                            "y": 75.50001525878906,
                            "width": 119.19999694824219,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 119.19999694824219,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "0e107a33-4250-4964-9955-96910726b5b3"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "670e7b53-f8ee-47d3-a4af-c7cde1ddcc70"
                        }
                    },
                    {
                        "id": "1f5427f1-c1cd-4d80-b5d9-c1fb60aeb550",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 360.6999969482422,
                            "y": 36.50001525878906,
                            "width": 82,
                            "height": 34.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 34.99998474121094
                            },
                            {
                                "x": 40,
                                "y": 34.99998474121094
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 82,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "670e7b53-f8ee-47d3-a4af-c7cde1ddcc70"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "5909d89c-5800-4a6d-9637-5874600be2c2"
                        }
                    },
                    {
                        "id": "b31779af-b653-4b9a-abb2-4c0e2bc9b5fe",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 360.6999969482422,
                            "y": 78.50001525878906,
                            "width": 99,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 99,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "670e7b53-f8ee-47d3-a4af-c7cde1ddcc70"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "adc031c0-61a1-47f3-bdab-9931abe3aaad"
                        }
                    },
                    {
                        "id": "0ee9f2bd-a249-4982-9466-de65abed2b64",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 360.6999969482422,
                            "y": 71.5,
                            "width": 98,
                            "height": 36.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 36.00001525878906
                            },
                            {
                                "x": 98,
                                "y": 36.00001525878906
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "670e7b53-f8ee-47d3-a4af-c7cde1ddcc70"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "9560ae3f-2402-49d3-98aa-24071ffba7aa"
                        }
                    },
                    {
                        "id": "7df1e56d-cecf-45b4-bcf3-c1417e05769d",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 538.6999969482422,
                            "y": 127.50001525878906,
                            "width": 1,
                            "height": 52.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 52.99998474121094
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "9560ae3f-2402-49d3-98aa-24071ffba7aa"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "5c85bafa-3def-44f4-b9c2-5026bc2c7bcd"
                        }
                    },
                    {
                        "id": "4610e64c-96f9-4fb3-b0ad-1ae2255d02d0",
                        "name": "[yes]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 539.6999969482422,
                            "y": 240.5,
                            "width": 1,
                            "height": 57
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 57
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "5c85bafa-3def-44f4-b9c2-5026bc2c7bcd"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "aa481227-72b2-4c67-841e-c9d276753726"
                        }
                    },
                    {
                        "id": "2650a3cc-c894-4446-9309-6705e8cbc6a7",
                        "name": "[yes]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 337.6999969482422,
                            "y": 329.5,
                            "width": 104,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 104,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "aa481227-72b2-4c67-841e-c9d276753726"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "8f4a1f8a-5ace-43a9-90f8-14f5798fae4f"
                        }
                    },
                    {
                        "id": "01205187-b20a-4cf3-963a-9617fc51f06b",
                        "name": "[yes]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 59.19999694824219,
                            "y": 338.00001525878906,
                            "width": 78.5,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 78.5,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "8f4a1f8a-5ace-43a9-90f8-14f5798fae4f"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "67a7325d-f96c-4a20-84e0-a31617d25f1c"
                        }
                    },
                    {
                        "id": "6c74015c-0a22-40b4-bc5c-9a84b7af8c22",
                        "name": "[no]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 237.6999969482422,
                            "y": 107.50001525878906,
                            "width": 221,
                            "height": 193.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 193.99998474121094
                            },
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 221,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "8f4a1f8a-5ace-43a9-90f8-14f5798fae4f"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "9560ae3f-2402-49d3-98aa-24071ffba7aa"
                        }
                    },
                    {
                        "id": "273e0aaa-a124-4795-80cb-865c7370d8ad",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 632.6999969482422,
                            "y": 36.50001525878906,
                            "width": 90,
                            "height": 28.999984741210938
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 45,
                                "y": 0
                            },
                            {
                                "x": 45,
                                "y": 28.999984741210938
                            },
                            {
                                "x": 90,
                                "y": 28.999984741210938
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "5909d89c-5800-4a6d-9637-5874600be2c2"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "6e074c7b-999f-4f71-a395-7c44cf86b11c"
                        }
                    },
                    {
                        "id": "a970766b-b98e-46e4-9d27-6f3fe0409053",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 619.6999969482422,
                            "y": 65.5,
                            "width": 103,
                            "height": 8.000015258789062
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 8.000015258789062
                            },
                            {
                                "x": 51.5,
                                "y": 8.000015258789062
                            },
                            {
                                "x": 51.5,
                                "y": 0
                            },
                            {
                                "x": 103,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "adc031c0-61a1-47f3-bdab-9931abe3aaad"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "6e074c7b-999f-4f71-a395-7c44cf86b11c"
                        }
                    },
                    {
                        "id": "29935802-5634-4e23-bd9d-c2191152ac0c",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 618.6999969482422,
                            "y": 65.5,
                            "width": 104,
                            "height": 42.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 42.00001525878906
                            },
                            {
                                "x": 52,
                                "y": 42.00001525878906
                            },
                            {
                                "x": 52,
                                "y": 0
                            },
                            {
                                "x": 104,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "9560ae3f-2402-49d3-98aa-24071ffba7aa"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "6e074c7b-999f-4f71-a395-7c44cf86b11c"
                        }
                    },
                    {
                        "id": "56da0c3f-e4e9-4a77-93c5-a6f95631f0fb",
                        "name": "[no]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 637.6999969482422,
                            "y": 65.5,
                            "width": 85,
                            "height": 145
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 145
                            },
                            {
                                "x": 42.5,
                                "y": 145
                            },
                            {
                                "x": 42.5,
                                "y": 0
                            },
                            {
                                "x": 85,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "5c85bafa-3def-44f4-b9c2-5026bc2c7bcd"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "6e074c7b-999f-4f71-a395-7c44cf86b11c"
                        }
                    },
                    {
                        "id": "dc33daf3-e978-4380-97bf-37dcfacdbbca",
                        "name": "[no]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 641.6999969482422,
                            "y": 65.5,
                            "width": 81,
                            "height": 262
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 262
                            },
                            {
                                "x": 40.5,
                                "y": 262
                            },
                            {
                                "x": 40.5,
                                "y": 0
                            },
                            {
                                "x": 81,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "aa481227-72b2-4c67-841e-c9d276753726"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "6e074c7b-999f-4f71-a395-7c44cf86b11c"
                        }
                    },
                    {
                        "id": "34269c89-c1d0-4eba-b1ec-66111816b9cf",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 742.6999969482422,
                            "y": 67,
                            "width": 71,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 71,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "6e074c7b-999f-4f71-a395-7c44cf86b11c"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "84188d46-8bd3-4724-8564-152b2d16b1ab"
                        }
                    },
                    {
                        "id": "9e0a9034-50fe-4284-9f75-a505d0f99bd9",
                        "name": "[yes]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 350.6999969482422,
                            "y": 0,
                            "width": 563,
                            "height": 41.5
                        },
                        "path": [
                            {
                                "x": 563,
                                "y": 38.5
                            },
                            {
                                "x": 563,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 41.5
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "84188d46-8bd3-4724-8564-152b2d16b1ab"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "670e7b53-f8ee-47d3-a4af-c7cde1ddcc70"
                        }
                    },
                    {
                        "id": "8cdda1ee-c354-4b6d-a2c2-96e992965f56",
                        "name": "[no]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 914.3999938964844,
                            "y": 98.5,
                            "width": 1,
                            "height": 72.80000305175781
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 72.80000305175781
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "84188d46-8bd3-4724-8564-152b2d16b1ab"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "ea3a736f-089e-49af-9bde-0773373f60ab"
                        }
                    },
                    {
                        "id": "a8a124df-dba3-44c9-a576-402732800bdc",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 905.8999786376953,
                            "y": 211.3000030517578,
                            "width": 1,
                            "height": 61.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 61.99998474121094
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "ea3a736f-089e-49af-9bde-0773373f60ab"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "d8eaa3c3-0e7c-4a0a-8672-82ca223c5fde"
                        }
                    },
                    {
                        "id": "35c39738-9272-407f-b534-8ff3c4ee71bc",
                        "name": "[yes]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 746.6999664306641,
                            "y": 191.3000030517578,
                            "width": 68.4000244140625,
                            "height": 111.99998474121094
                        },
                        "path": [
                            {
                                "x": 40,
                                "y": 111.99998474121094
                            },
                            {
                                "x": 0,
                                "y": 111.99998474121094
                            },
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 68.4000244140625,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "d8eaa3c3-0e7c-4a0a-8672-82ca223c5fde"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "ea3a736f-089e-49af-9bde-0773373f60ab"
                        }
                    },
                    {
                        "id": "e83f461b-4dfa-4660-bb21-c9cff6e72ab1",
                        "name": "[no]",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 886.6999664306641,
                            "y": 333.29998779296875,
                            "width": 1,
                            "height": 44.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 44.00001525878906
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "d8eaa3c3-0e7c-4a0a-8672-82ca223c5fde"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "6a7bc8de-6482-4e14-95fc-bfc3834c4be4"
                        }
                    },
                    {
                        "id": "3ca86309-c5f9-49a1-bb52-d5a765fcdfad",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 787.6999664306641,
                            "y": 417.3000030517578,
                            "width": 99,
                            "height": 41
                        },
                        "path": [
                            {
                                "x": 99,
                                "y": 0
                            },
                            {
                                "x": 99,
                                "y": 41
                            },
                            {
                                "x": 0,
                                "y": 41
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "6a7bc8de-6482-4e14-95fc-bfc3834c4be4"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "1659b3dc-275b-43a5-be71-91728819a570"
                        }
                    },
                    {
                        "id": "6f4516c4-fac9-46c3-ab99-98f18893a321",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 485.19996643066406,
                            "y": 451.8000030517578,
                            "width": 102.5,
                            "height": 6.5
                        },
                        "path": [
                            {
                                "x": 102.5,
                                "y": 6.5
                            },
                            {
                                "x": 51.25,
                                "y": 6.5
                            },
                            {
                                "x": 51.25,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "1659b3dc-275b-43a5-be71-91728819a570"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "49a16d6f-f31a-44a9-8be7-b74ccb0aefed"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    static final String ACTIBITY_MODEL_2 = """
            {
                "version": "2.0.0",
                "type": "ActivityDiagram",
                "size": {
                    "width": 1520,
                    "height": 860
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "299ef0dc-cd12-4a79-9888-6dae14ce7a83",
                        "name": "",
                        "type": "ActivityInitialNode",
                        "owner": null,
                        "bounds": {
                            "x": 24.5,
                            "y": 0,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "c7d7573f-2223-4a83-bf2e-0df6a07d82de",
                        "name": "",
                        "type": "ActivityForkNode",
                        "owner": null,
                        "bounds": {
                            "x": 321,
                            "y": 145.00003051757812,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "31a41451-8fc5-4d8b-b7ec-230ed68e33f5",
                        "name": "",
                        "type": "ActivityForkNode",
                        "owner": null,
                        "bounds": {
                            "x": 692,
                            "y": 149,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "1559cbe7-8052-4b7c-9531-84a967f5abc1",
                        "name": "Choose type",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 437,
                            "y": 61.00001525878906,
                            "width": 160,
                            "height": 70
                        }
                    },
                    {
                        "id": "9c091427-6e30-4a40-ae91-889ce65848d1",
                        "name": "Choose Size",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 435,
                            "y": 151.00001525878906,
                            "width": 170,
                            "height": 50
                        }
                    },
                    {
                        "id": "5a2cac0f-8505-44fa-b2e1-0eddf7e18243",
                        "name": "Choose topping",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 436,
                            "y": 221.00001525878906,
                            "width": 170,
                            "height": 60
                        }
                    },
                    {
                        "id": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "name": "Complete Order",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 187.5,
                            "y": 352.8000030517578,
                            "width": 650,
                            "height": 460
                        }
                    },
                    {
                        "id": "5e67247a-5534-4904-8b29-47d1b2d779ae",
                        "name": "",
                        "type": "ActivityInitialNode",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 201,
                            "y": 453.8000030517578,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "e8920604-82e9-4a9d-852b-28789b13d7cd",
                        "name": "",
                        "type": "ActivityForkNode",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 277.5,
                            "y": 455.79998779296875,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "fb62e404-8f8d-4573-a254-113c72bd7d36",
                        "name": "Enter Adress",
                        "type": "Activity",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 335.5,
                            "y": 397.8000030517578,
                            "width": 210,
                            "height": 60
                        }
                    },
                    {
                        "id": "968e25e3-0c0d-42da-a66f-a4500f3e9491",
                        "name": "Enter Payment details",
                        "type": "Activity",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 352.5,
                            "y": 498.8000030517578,
                            "width": 190,
                            "height": 60
                        }
                    },
                    {
                        "id": "29c5b249-3289-4e87-ab25-c8f59c64d057",
                        "name": "",
                        "type": "ActivityForkNode",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 654.5,
                            "y": 454.79998779296875,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "7471c8fe-e49d-4d90-934f-f91166ae9f46",
                        "name": "",
                        "type": "ActivityFinalNode",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 751,
                            "y": 465.8000030517578,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "efd38597-d983-4738-9148-227b4697c821",
                        "name": "Payment Details Valid?",
                        "type": "ActivityMergeNode",
                        "owner": "0223188c-0e36-443e-aa77-2d6013a74339",
                        "bounds": {
                            "x": 504.5,
                            "y": 597.7999725341797,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "b012273d-4a1b-41e7-a3a2-799ff3d1ab98",
                        "name": "Change Topping",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 878.5,
                            "y": 331.79998779296875,
                            "width": 249,
                            "height": 83.00001525878906
                        }
                    },
                    {
                        "id": "f07e7427-f553-43f5-a380-e2680f6e8cdc",
                        "name": "Pinapples left?",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 794.5,
                            "y": 144.79998779296875,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "d206d82d-d770-4f61-b9e7-8f4f751b8b1c",
                        "name": "Want to change Topping?",
                        "type": "ActivityMergeNode",
                        "owner": null,
                        "bounds": {
                            "x": 1017.5,
                            "y": 210.79998779296875,
                            "width": 200,
                            "height": 60
                        }
                    },
                    {
                        "id": "fe400ab3-8df2-4e11-97cd-4897673c0c62",
                        "name": "Cancel Order",
                        "type": "ActivityActionNode",
                        "owner": null,
                        "bounds": {
                            "x": 1207.5,
                            "y": 321.79998779296875,
                            "width": 200,
                            "height": 100
                        }
                    },
                    {
                        "id": "d5d7546d-66b0-47c3-8d09-19a6da4013ba",
                        "name": "",
                        "type": "ActivityFinalNode",
                        "owner": null,
                        "bounds": {
                            "x": 1272,
                            "y": 471.8000030517578,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "510bc0d0-5b85-4306-8100-1d09690d782f",
                        "name": "Pay",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 950.5,
                            "y": 650.8000030517578,
                            "width": 200,
                            "height": 100
                        }
                    },
                    {
                        "id": "567b3aba-956c-4cd6-bdae-640154163757",
                        "name": "Send order",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 1197.5,
                            "y": 631.2000274658203,
                            "width": 200,
                            "height": 100
                        }
                    },
                    {
                        "id": "4e547ee4-5fc7-4fb9-b5a9-60ca91e90c0f",
                        "name": "",
                        "type": "ActivityFinalNode",
                        "owner": null,
                        "bounds": {
                            "x": 1418,
                            "y": 809.2000274658203,
                            "width": 45,
                            "height": 45
                        }
                    },
                    {
                        "id": "72602956-9741-45b4-8ade-bbd5d6d4777c",
                        "name": "Open Pizza Delivery Website",
                        "type": "Activity",
                        "owner": null,
                        "bounds": {
                            "x": 55.5,
                            "y": 112.80000305175781,
                            "width": 240,
                            "height": 100
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "50fbb081-72af-4c57-b988-c25a8940e0f1",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 45,
                            "width": 55.5,
                            "height": 117.80000305175781
                        },
                        "path": [
                            {
                                "x": 47,
                                "y": 0
                            },
                            {
                                "x": 47,
                                "y": 40
                            },
                            {
                                "x": 0,
                                "y": 40
                            },
                            {
                                "x": 0,
                                "y": 117.80000305175781
                            },
                            {
                                "x": 55.5,
                                "y": 117.80000305175781
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "299ef0dc-cd12-4a79-9888-6dae14ce7a83"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "72602956-9741-45b4-8ade-bbd5d6d4777c"
                        }
                    },
                    {
                        "id": "8f515b4a-50fe-487d-a0a1-b82855f3319a",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 341,
                            "y": 96.00001525878906,
                            "width": 96,
                            "height": 79.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 79.00001525878906
                            },
                            {
                                "x": 40,
                                "y": 79.00001525878906
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 96,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "c7d7573f-2223-4a83-bf2e-0df6a07d82de"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "1559cbe7-8052-4b7c-9531-84a967f5abc1"
                        }
                    },
                    {
                        "id": "afb66700-9a40-4a21-accc-6d959a98ac16",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 341,
                            "y": 176.00001525878906,
                            "width": 94,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 94,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "c7d7573f-2223-4a83-bf2e-0df6a07d82de"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "9c091427-6e30-4a40-ae91-889ce65848d1"
                        }
                    },
                    {
                        "id": "8fec5757-1f0e-4dc3-a535-cf5758a1bf0e",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 341,
                            "y": 175.00003051757812,
                            "width": 95,
                            "height": 75.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 75.99998474121094
                            },
                            {
                                "x": 95,
                                "y": 75.99998474121094
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "c7d7573f-2223-4a83-bf2e-0df6a07d82de"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "5a2cac0f-8505-44fa-b2e1-0eddf7e18243"
                        }
                    },
                    {
                        "id": "7aef866d-4214-4582-9e86-77bec474901d",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 597,
                            "y": 96.00001525878906,
                            "width": 95,
                            "height": 82.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 47.5,
                                "y": 0
                            },
                            {
                                "x": 47.5,
                                "y": 82.99998474121094
                            },
                            {
                                "x": 95,
                                "y": 82.99998474121094
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "1559cbe7-8052-4b7c-9531-84a967f5abc1"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "31a41451-8fc5-4d8b-b7ec-230ed68e33f5"
                        }
                    },
                    {
                        "id": "0b3f0c6e-909c-4f04-a741-25a1fd8a8f5a",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 605,
                            "y": 176.00001525878906,
                            "width": 87,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 87,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "9c091427-6e30-4a40-ae91-889ce65848d1"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "31a41451-8fc5-4d8b-b7ec-230ed68e33f5"
                        }
                    },
                    {
                        "id": "c1c3c6df-9061-4da2-abd7-b5787e7dfbf3",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 606,
                            "y": 179,
                            "width": 86,
                            "height": 72.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 72.00001525878906
                            },
                            {
                                "x": 43,
                                "y": 72.00001525878906
                            },
                            {
                                "x": 43,
                                "y": 0
                            },
                            {
                                "x": 86,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "5a2cac0f-8505-44fa-b2e1-0eddf7e18243"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "31a41451-8fc5-4d8b-b7ec-230ed68e33f5"
                        }
                    },
                    {
                        "id": "d46059d3-6fd4-4f1a-8a65-a73bb8649337",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 712,
                            "y": 104.79998779296875,
                            "width": 182.5,
                            "height": 74.20001220703125
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 74.20001220703125
                            },
                            {
                                "x": 40,
                                "y": 74.20001220703125
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 182.5,
                                "y": 0
                            },
                            {
                                "x": 182.5,
                                "y": 40
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "31a41451-8fc5-4d8b-b7ec-230ed68e33f5"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "f07e7427-f553-43f5-a380-e2680f6e8cdc"
                        }
                    },
                    {
                        "id": "0cf5c09c-6afa-456c-aed9-eccb82d25f80",
                        "name": "Yes",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 816,
                            "y": 204.79998779296875,
                            "width": 1,
                            "height": 148.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 148.00001525878906
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "f07e7427-f553-43f5-a380-e2680f6e8cdc"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "0223188c-0e36-443e-aa77-2d6013a74339"
                        }
                    },
                    {
                        "id": "7575a148-cbf7-4878-81d5-0ff7bb426f3c",
                        "name": "No",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 994.5,
                            "y": 137.79998779296875,
                            "width": 123,
                            "height": 73
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 37
                            },
                            {
                                "x": 40,
                                "y": 37
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 123,
                                "y": 0
                            },
                            {
                                "x": 123,
                                "y": 73
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "f07e7427-f553-43f5-a380-e2680f6e8cdc"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "d206d82d-d770-4f61-b9e7-8f4f751b8b1c"
                        }
                    },
                    {
                        "id": "ac3cd726-a216-4e1d-b6f8-cb38a3a58f70",
                        "name": "Yes",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 1072.5,
                            "y": 270.79998779296875,
                            "width": 1,
                            "height": 61
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 61
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "d206d82d-d770-4f61-b9e7-8f4f751b8b1c"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "b012273d-4a1b-41e7-a3a2-799ff3d1ab98"
                        }
                    },
                    {
                        "id": "2a2c68e6-477b-4c16-8825-0d0947f256a6",
                        "name": "No",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 1217.5,
                            "y": 240.79998779296875,
                            "width": 90,
                            "height": 81
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 90,
                                "y": 0
                            },
                            {
                                "x": 90,
                                "y": 81
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "d206d82d-d770-4f61-b9e7-8f4f751b8b1c"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "fe400ab3-8df2-4e11-97cd-4897673c0c62"
                        }
                    },
                    {
                        "id": "4b465dac-c658-45da-9b6c-00b672bf4ed3",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 1294.5,
                            "y": 421.79998779296875,
                            "width": 1,
                            "height": 50.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 50.00001525878906
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "fe400ab3-8df2-4e11-97cd-4897673c0c62"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "d5d7546d-66b0-47c3-8d09-19a6da4013ba"
                        }
                    },
                    {
                        "id": "b7545e2f-c50d-46ce-9c26-8907476cc655",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 837.5,
                            "y": 383.8000030517578,
                            "width": 41,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 41,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "b012273d-4a1b-41e7-a3a2-799ff3d1ab98"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "0223188c-0e36-443e-aa77-2d6013a74339"
                        }
                    },
                    {
                        "id": "dc7707db-61d8-4c46-8338-20b12befe786",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 246,
                            "y": 477.2999954223633,
                            "width": 31.5,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 31.5,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "5e67247a-5534-4904-8b29-47d1b2d779ae"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "e8920604-82e9-4a9d-852b-28789b13d7cd"
                        }
                    },
                    {
                        "id": "f4ad9ca0-762b-480f-8e63-54bde2656a3d",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 295.5,
                            "y": 427.8000030517578,
                            "width": 42,
                            "height": 57.99998474121094
                        },
                        "path": [
                            {
                                "x": 2,
                                "y": 57.99998474121094
                            },
                            {
                                "x": 42,
                                "y": 57.99998474121094
                            },
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "e8920604-82e9-4a9d-852b-28789b13d7cd"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "fb62e404-8f8d-4573-a254-113c72bd7d36"
                        }
                    },
                    {
                        "id": "86173e79-4f50-4a05-a2b0-f5c0cde053c1",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 297.5,
                            "y": 485.79998779296875,
                            "width": 55,
                            "height": 43.00001525878906
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 0
                            },
                            {
                                "x": 15,
                                "y": 43.00001525878906
                            },
                            {
                                "x": 55,
                                "y": 43.00001525878906
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "e8920604-82e9-4a9d-852b-28789b13d7cd"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "968e25e3-0c0d-42da-a66f-a4500f3e9491"
                        }
                    },
                    {
                        "id": "7dd02532-20be-4ed9-bbe5-60a41b4619dd",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 674.5,
                            "y": 488.3000030517578,
                            "width": 76.5,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 76.5,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "29c5b249-3289-4e87-ab25-c8f59c64d057"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "7471c8fe-e49d-4d90-934f-f91166ae9f46"
                        }
                    },
                    {
                        "id": "35054098-9147-4113-9990-d6edc39dcfa0",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 545.5,
                            "y": 427.8000030517578,
                            "width": 109,
                            "height": 56.99998474121094
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 54.5,
                                "y": 0
                            },
                            {
                                "x": 54.5,
                                "y": 56.99998474121094
                            },
                            {
                                "x": 109,
                                "y": 56.99998474121094
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "fb62e404-8f8d-4573-a254-113c72bd7d36"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "29c5b249-3289-4e87-ab25-c8f59c64d057"
                        }
                    },
                    {
                        "id": "c23cdb09-e19f-403a-b8de-24a6b551cc91",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 542.5,
                            "y": 528.8000030517578,
                            "width": 62,
                            "height": 68.99996948242188
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 62,
                                "y": 0
                            },
                            {
                                "x": 62,
                                "y": 68.99996948242188
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "968e25e3-0c0d-42da-a66f-a4500f3e9491"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "efd38597-d983-4738-9148-227b4697c821"
                        }
                    },
                    {
                        "id": "c7719602-1114-4562-a370-6f0ffdacc7d2",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 512.5,
                            "y": 700.8000030517578,
                            "width": 438,
                            "height": 152
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 112
                            },
                            {
                                "x": 0,
                                "y": 152
                            },
                            {
                                "x": 381.5,
                                "y": 152
                            },
                            {
                                "x": 381.5,
                                "y": 0
                            },
                            {
                                "x": 438,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "0223188c-0e36-443e-aa77-2d6013a74339"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "510bc0d0-5b85-4306-8100-1d09690d782f"
                        }
                    },
                    {
                        "id": "e552971c-fa43-4c95-9bb2-c5d23b35aa03",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 1150.5,
                            "y": 691.0000152587891,
                            "width": 47,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 47,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "510bc0d0-5b85-4306-8100-1d09690d782f"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "567b3aba-956c-4cd6-bdae-640154163757"
                        }
                    },
                    {
                        "id": "56fdc401-1c8a-4737-91f6-cedacf60f45d",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 1297.5,
                            "y": 731.2000274658203,
                            "width": 143,
                            "height": 78
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 40
                            },
                            {
                                "x": 143,
                                "y": 38
                            },
                            {
                                "x": 143,
                                "y": 78
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "567b3aba-956c-4cd6-bdae-640154163757"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "4e547ee4-5fc7-4fb9-b5a9-60ca91e90c0f"
                        }
                    },
                    {
                        "id": "2bb8737c-2f32-4d7b-add3-057625583f10",
                        "name": "No",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 447.5,
                            "y": 558.8000030517578,
                            "width": 157,
                            "height": 138.99996948242188
                        },
                        "path": [
                            {
                                "x": 157,
                                "y": 98.99996948242188
                            },
                            {
                                "x": 157,
                                "y": 138.99996948242188
                            },
                            {
                                "x": 0,
                                "y": 138.99996948242188
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "efd38597-d983-4738-9148-227b4697c821"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "968e25e3-0c0d-42da-a66f-a4500f3e9491"
                        }
                    },
                    {
                        "id": "da2715f4-fcab-4fc2-a34f-1cae15f2be30",
                        "name": "Yes",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 614.5,
                            "y": 484.79998779296875,
                            "width": 130,
                            "height": 142.99998474121094
                        },
                        "path": [
                            {
                                "x": 90,
                                "y": 142.99998474121094
                            },
                            {
                                "x": 130,
                                "y": 142.99998474121094
                            },
                            {
                                "x": 130,
                                "y": 71.49999237060547
                            },
                            {
                                "x": 0,
                                "y": 71.49999237060547
                            },
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 40,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "efd38597-d983-4738-9148-227b4697c821"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "29c5b249-3289-4e87-ab25-c8f59c64d057"
                        }
                    },
                    {
                        "id": "1251f86c-9294-4d21-84b4-66c903994588",
                        "name": "",
                        "type": "ActivityControlFlow",
                        "owner": null,
                        "bounds": {
                            "x": 295.5,
                            "y": 175.00003051757812,
                            "width": 25.5,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 25.5,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "72602956-9741-45b4-8ade-bbd5d6d4777c"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "c7d7573f-2223-4a83-bf2e-0df6a07d82de"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    private UMLActivityDiagrams() {
        // do not instantiate
    }
}
