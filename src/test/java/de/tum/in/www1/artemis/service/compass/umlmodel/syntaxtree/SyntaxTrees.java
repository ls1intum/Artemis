package de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree;

class SyntaxTrees {

    static final String SYNTAX_TREE_MODEL_1A = """
            {
                "version": "2.0.0",
                "type": "SyntaxTree",
                "size": {
                    "width": 540,
                    "height": 580
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "8e21a66e-9fc4-4f82-b2d1-3d26a51b309a",
                        "name": "stmt",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 160,
                            "y": 0,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "2ea3a753-b2d9-4a57-881e-1f10837ef5a2",
                        "name": "a",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 90,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "3a54da7e-ad8c-410d-83d3-541ec58ea038",
                        "name": "=",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 110,
                            "y": 90,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "76c37862-7cb7-4044-8250-764ca9b1a216",
                        "name": "4",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 220,
                            "y": 90,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "d2025889-3fe5-4ad5-9f42-d1ea57256dcc",
                        "name": ";",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 320,
                            "y": 90,
                            "width": 80,
                            "height": 30
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "200cbd5f-4ce1-4d96-8bbd-34ef89aae533",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 40,
                            "y": 30,
                            "width": 160,
                            "height": 60
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 60
                            },
                            {
                                "x": 160,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "2ea3a753-b2d9-4a57-881e-1f10837ef5a2"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "8e21a66e-9fc4-4f82-b2d1-3d26a51b309a"
                        }
                    },
                    {
                        "id": "7afce32d-7faa-489d-ac8f-10964961edc5",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 150,
                            "y": 30,
                            "width": 50,
                            "height": 60
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 60
                            },
                            {
                                "x": 50,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "3a54da7e-ad8c-410d-83d3-541ec58ea038"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "8e21a66e-9fc4-4f82-b2d1-3d26a51b309a"
                        }
                    },
                    {
                        "id": "0a40b242-028b-4013-b239-016345c16965",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 200,
                            "y": 30,
                            "width": 60,
                            "height": 60
                        },
                        "path": [
                            {
                                "x": 60,
                                "y": 60
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "76c37862-7cb7-4044-8250-764ca9b1a216"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "8e21a66e-9fc4-4f82-b2d1-3d26a51b309a"
                        }
                    },
                    {
                        "id": "c147a351-0b47-4f3d-92a4-8d8b35fc25e7",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 200,
                            "y": 30,
                            "width": 160,
                            "height": 60
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 160,
                                "y": 60
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "8e21a66e-9fc4-4f82-b2d1-3d26a51b309a"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "d2025889-3fe5-4ad5-9f42-d1ea57256dcc"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    static final String SYNTAX_TREE_MODEL_1B = """
            {
                "version": "2.0.0",
                "type": "SyntaxTree",
                "size": {
                    "width": 460,
                    "height": 560
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "50606dce-29cd-4440-9c79-69ec40a27ac2",
                        "name": "stmt",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 160,
                            "y": 0,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "0c6bf193-f254-46df-a33b-f49cc4a06a31",
                        "name": "a",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 80,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "d9633903-d805-4429-80de-c2326082c294",
                        "name": "=",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 30,
                            "y": 220,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "419474df-a68f-4b34-9bc0-640647416994",
                        "name": "4",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 220,
                            "y": 120,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "74c56bf9-d34c-4a93-afeb-4c960d6a91f6",
                        "name": ";",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 330,
                            "y": 180,
                            "width": 80,
                            "height": 30
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "aebf41a6-c55d-4d0e-a645-6dc2b9515208",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 40,
                            "y": 15,
                            "width": 120,
                            "height": 65
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 65
                            },
                            {
                                "x": 120,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "0c6bf193-f254-46df-a33b-f49cc4a06a31"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "50606dce-29cd-4440-9c79-69ec40a27ac2"
                        }
                    },
                    {
                        "id": "8c6ca60f-3509-4df9-b177-ffb281178b85",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 70,
                            "y": 30,
                            "width": 130,
                            "height": 190
                        },
                        "path": [
                            {
                                "x": 130,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 190
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "50606dce-29cd-4440-9c79-69ec40a27ac2"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "d9633903-d805-4429-80de-c2326082c294"
                        }
                    },
                    {
                        "id": "52c91f51-9509-457a-8dd3-4daf8a71dd6a",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 240,
                            "y": 15,
                            "width": 90,
                            "height": 180
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 90,
                                "y": 180
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "50606dce-29cd-4440-9c79-69ec40a27ac2"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "74c56bf9-d34c-4a93-afeb-4c960d6a91f6"
                        }
                    },
                    {
                        "id": "6eb899fd-c31e-4d52-b168-8a82e28be637",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 200,
                            "y": 30,
                            "width": 60,
                            "height": 90
                        },
                        "path": [
                            {
                                "x": 60,
                                "y": 90
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "419474df-a68f-4b34-9bc0-640647416994"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "50606dce-29cd-4440-9c79-69ec40a27ac2"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    static final String SYNTAX_TREE_MODEL_2 = """
            {
                "version": "2.0.0",
                "type": "SyntaxTree",
                "size": {
                    "width": 520,
                    "height": 660
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "5d2498d6-350c-4acf-ba38-23d19d5c6126",
                        "name": "stmt",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 180,
                            "y": 0,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "8ad7b06e-b6cd-49a4-977d-03bbb0cf1cb4",
                        "name": "a",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 220,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "3f583f3e-de62-47d2-b706-4483cd677725",
                        "name": "=",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 120,
                            "y": 140,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "02e457c9-725e-4792-a437-111c58688026",
                        "name": ";",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 340,
                            "y": 140,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "8e448c20-8a41-4627-806e-a2f5b4a4f40c",
                        "name": "expr",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 230,
                            "y": 140,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "253165b3-19df-4865-84f9-9a2645bfcbf2",
                        "name": "name",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 140,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "e68a9bbd-85f4-4711-83df-1d99c882632c",
                        "name": "4",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 230,
                            "y": 380,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "640aa598-dc36-45b6-a664-dcf67698d455",
                        "name": "expr",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 230,
                            "y": 220,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "ce72461c-b9dc-4c8c-bf01-ff90f6dda126",
                        "name": "number",
                        "type": "SyntaxTreeNonterminal",
                        "owner": null,
                        "bounds": {
                            "x": 230,
                            "y": 300,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "55d08470-dd1f-4cf2-9c2a-973e03731606",
                        "name": "(",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 120,
                            "y": 220,
                            "width": 80,
                            "height": 30
                        }
                    },
                    {
                        "id": "8fe2d8dd-98df-4609-a11e-fd17b3fb0ab0",
                        "name": ")",
                        "type": "SyntaxTreeTerminal",
                        "owner": null,
                        "bounds": {
                            "x": 330,
                            "y": 220,
                            "width": 80,
                            "height": 30
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "f40bd20e-3c44-4bfd-afcc-292be5af6652",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 40,
                            "y": 30,
                            "width": 180,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 110
                            },
                            {
                                "x": 180,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "253165b3-19df-4865-84f9-9a2645bfcbf2"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "5d2498d6-350c-4acf-ba38-23d19d5c6126"
                        }
                    },
                    {
                        "id": "87e801ba-fbb1-4486-9fa9-d59307d4ada2",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 220,
                            "y": 30,
                            "width": 160,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 160,
                                "y": 110
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "5d2498d6-350c-4acf-ba38-23d19d5c6126"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "02e457c9-725e-4792-a437-111c58688026"
                        }
                    },
                    {
                        "id": "04f1a212-9091-437d-82b9-d98c520fd510",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 220,
                            "y": 30,
                            "width": 50,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 50,
                                "y": 110
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "5d2498d6-350c-4acf-ba38-23d19d5c6126"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "8e448c20-8a41-4627-806e-a2f5b4a4f40c"
                        }
                    },
                    {
                        "id": "66c0a6b3-14c2-4330-90ae-0ce643894512",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 160,
                            "y": 30,
                            "width": 60,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 60,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 110
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "5d2498d6-350c-4acf-ba38-23d19d5c6126"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "3f583f3e-de62-47d2-b706-4483cd677725"
                        }
                    },
                    {
                        "id": "f77f4de4-0412-4383-b848-21427a50d19d",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 40,
                            "y": 170,
                            "width": 1,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 50
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "253165b3-19df-4865-84f9-9a2645bfcbf2"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "8ad7b06e-b6cd-49a4-977d-03bbb0cf1cb4"
                        }
                    },
                    {
                        "id": "3a2266cc-fc26-44e0-b68a-a6048aee2880",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 270,
                            "y": 170,
                            "width": 1,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 50
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "8e448c20-8a41-4627-806e-a2f5b4a4f40c"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "640aa598-dc36-45b6-a664-dcf67698d455"
                        }
                    },
                    {
                        "id": "ef58b0a0-9788-4d8b-b656-89967e79d2b2",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 160,
                            "y": 170,
                            "width": 110,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 50
                            },
                            {
                                "x": 110,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "55d08470-dd1f-4cf2-9c2a-973e03731606"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "8e448c20-8a41-4627-806e-a2f5b4a4f40c"
                        }
                    },
                    {
                        "id": "1bb7e0f8-5c35-4413-99a4-fce5f253a0b7",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 270,
                            "y": 170,
                            "width": 100,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 100,
                                "y": 50
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "8fe2d8dd-98df-4609-a11e-fd17b3fb0ab0"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "8e448c20-8a41-4627-806e-a2f5b4a4f40c"
                        }
                    },
                    {
                        "id": "040395f2-19a1-448b-b75a-2a5068406615",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 270,
                            "y": 330,
                            "width": 1,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 50
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "ce72461c-b9dc-4c8c-bf01-ff90f6dda126"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "e68a9bbd-85f4-4711-83df-1d99c882632c"
                        }
                    },
                    {
                        "id": "65603e2f-75b1-41bc-857b-533eb26d1c15",
                        "name": "",
                        "type": "SyntaxTreeLink",
                        "owner": null,
                        "bounds": {
                            "x": 270,
                            "y": 250,
                            "width": 1,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 50
                            }
                        ],
                        "source": {
                            "direction": "Down",
                            "element": "640aa598-dc36-45b6-a664-dcf67698d455"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "ce72461c-b9dc-4c8c-bf01-ff90f6dda126"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    private SyntaxTrees() {
        // do not instantiate
    }
}
