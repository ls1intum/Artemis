package de.tum.in.www1.artemis.service.compass.umlmodel.petrinet;

class PetriNets {

    static final String PETRI_NET_MODEL_1A = """
            {
                "version": "2.0.0",
                "type": "PetriNet",
                "size": {
                    "width": 580,
                    "height": 480
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "af3e9a50-aa85-4668-b46d-11b0dc407f20",
                        "name": "t1",
                        "type": "PetriNetTransition",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 90,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "9aa98e8d-d864-4587-b9c8-866dc347cfee",
                        "name": "t2",
                        "type": "PetriNetTransition",
                        "owner": null,
                        "bounds": {
                            "x": 290,
                            "y": 90,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "48af145a-8d0a-4a6c-87d2-18cee2f6ad0a",
                        "name": "a",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 120,
                            "y": 0,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 1,
                        "capacity": 1
                    },
                    {
                        "id": "5b32ce17-a779-45b7-bfbe-5563bbed1064",
                        "name": "b",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 120,
                            "y": 180,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 0,
                        "capacity": "Infinity"
                    }
                ],
                "relationships": [
                    {
                        "id": "22889cf7-b39e-4e7b-a21c-aa39c23daaf8",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 20,
                            "y": 30,
                            "width": 100,
                            "height": 90
                        },
                        "path": [
                            {
                                "x": 100,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 90
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "48af145a-8d0a-4a6c-87d2-18cee2f6ad0a"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "af3e9a50-aa85-4668-b46d-11b0dc407f20"
                        }
                    },
                    {
                        "id": "6e15f9a5-2ab1-4f8f-829b-38dcdbd6f5a8",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 20,
                            "y": 120,
                            "width": 100,
                            "height": 90
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 100,
                                "y": 90
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "af3e9a50-aa85-4668-b46d-11b0dc407f20"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "5b32ce17-a779-45b7-bfbe-5563bbed1064"
                        }
                    },
                    {
                        "id": "5fd53f65-8c9e-47a8-b3d5-2d9962ff4542",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 180,
                            "y": 120,
                            "width": 110,
                            "height": 90
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 90
                            },
                            {
                                "x": 110,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "5b32ce17-a779-45b7-bfbe-5563bbed1064"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "9aa98e8d-d864-4587-b9c8-866dc347cfee"
                        }
                    },
                    {
                        "id": "aa634acd-5245-4bfd-ba17-20815647be05",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 180,
                            "y": 30,
                            "width": 110,
                            "height": 90
                        },
                        "path": [
                            {
                                "x": 110,
                                "y": 90
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "9aa98e8d-d864-4587-b9c8-866dc347cfee"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "48af145a-8d0a-4a6c-87d2-18cee2f6ad0a"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    static final String PETRI_NET_MODEL_1B = """
            {
                "version": "2.0.0",
                "type": "PetriNet",
                "size": {
                    "width": 500,
                    "height": 620
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "f406d5df-9404-4914-8631-8588cd289566",
                        "name": "t1",
                        "type": "PetriNetTransition",
                        "owner": null,
                        "bounds": {
                            "x": 340,
                            "y": 110,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "9336eb67-ddfb-4622-8593-d640402a04fc",
                        "name": "t2",
                        "type": "PetriNetTransition",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 130,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "a12c5ed0-56aa-4174-b64b-7aead67429fd",
                        "name": "a",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 150,
                            "y": 0,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 1,
                        "capacity": 1
                    },
                    {
                        "id": "c67e50de-22a0-42df-8056-1cc65fc0f950",
                        "name": "b",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 150,
                            "y": 230,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 0,
                        "capacity": "Infinity"
                    }
                ],
                "relationships": [
                    {
                        "id": "818fd351-bf3e-48b8-b41b-d775b23c13e5",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 20,
                            "y": 30,
                            "width": 130,
                            "height": 130
                        },
                        "path": [
                            {
                                "x": 130,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 130
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "a12c5ed0-56aa-4174-b64b-7aead67429fd"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "9336eb67-ddfb-4622-8593-d640402a04fc"
                        }
                    },
                    {
                        "id": "60e82121-c0a2-4140-b723-c192783df7af",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 20,
                            "y": 160,
                            "width": 130,
                            "height": 100
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 130,
                                "y": 100
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "9336eb67-ddfb-4622-8593-d640402a04fc"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "c67e50de-22a0-42df-8056-1cc65fc0f950"
                        }
                    },
                    {
                        "id": "b161c092-db0b-41cd-9b77-08e1e663ca5a",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 210,
                            "y": 140,
                            "width": 130,
                            "height": 120
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 120
                            },
                            {
                                "x": 130,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "c67e50de-22a0-42df-8056-1cc65fc0f950"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "f406d5df-9404-4914-8631-8588cd289566"
                        }
                    },
                    {
                        "id": "80519154-e81d-465b-b692-fa6343345ece",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 210,
                            "y": 30,
                            "width": 130,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 130,
                                "y": 110
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "f406d5df-9404-4914-8631-8588cd289566"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "a12c5ed0-56aa-4174-b64b-7aead67429fd"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    static final String PETRI_NET_MODEL_2 = """
            {
                "version": "2.0.0",
                "type": "PetriNet",
                "size": {
                    "width": 660,
                    "height": 640
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "af3e9a50-aa85-4668-b46d-11b0dc407f20",
                        "name": "t0",
                        "type": "PetriNetTransition",
                        "owner": null,
                        "bounds": {
                            "x": 150,
                            "y": 110,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "f5b2ca9a-9fb0-41e4-85b6-17723d7e09f7",
                        "name": "t1",
                        "type": "PetriNetTransition",
                        "owner": null,
                        "bounds": {
                            "x": 450,
                            "y": 110,
                            "width": 20,
                            "height": 60
                        }
                    },
                    {
                        "id": "d905518c-f1e1-41b2-a4ca-7a8c6950033e",
                        "name": "start",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 110,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 50,
                        "capacity": "Infinity"
                    },
                    {
                        "id": "3f5fc415-6a39-41fb-a10e-6ea38987e453",
                        "name": "place 1",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 290,
                            "y": 0,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 0,
                        "capacity": "Infinity"
                    },
                    {
                        "id": "3951f435-d212-40f5-af60-e6f22afdb1bf",
                        "name": "place 2",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 290,
                            "y": 230,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 0,
                        "capacity": "Infinity"
                    },
                    {
                        "id": "69091741-1d13-40ea-8d2c-c9c02e65ae46",
                        "name": "end",
                        "type": "PetriNetPlace",
                        "owner": null,
                        "bounds": {
                            "x": 540,
                            "y": 110,
                            "width": 60,
                            "height": 60
                        },
                        "amountOfTokens": 0,
                        "capacity": "Infinity"
                    }
                ],
                "relationships": [
                    {
                        "id": "429a53ae-6050-47ae-ac1e-e909eceaf9df",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 170,
                            "y": 30,
                            "width": 120,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 110
                            },
                            {
                                "x": 120,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "af3e9a50-aa85-4668-b46d-11b0dc407f20"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "3f5fc415-6a39-41fb-a10e-6ea38987e453"
                        }
                    },
                    {
                        "id": "121fe55e-498a-4f24-bc44-b64b0cf4c28a",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 350,
                            "y": 30,
                            "width": 100,
                            "height": 110
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 100,
                                "y": 110
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "3f5fc415-6a39-41fb-a10e-6ea38987e453"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "f5b2ca9a-9fb0-41e4-85b6-17723d7e09f7"
                        }
                    },
                    {
                        "id": "c1e53264-090d-42cd-b469-1863e5839934",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 170,
                            "y": 140,
                            "width": 120,
                            "height": 120
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 120,
                                "y": 120
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "af3e9a50-aa85-4668-b46d-11b0dc407f20"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "3951f435-d212-40f5-af60-e6f22afdb1bf"
                        }
                    },
                    {
                        "id": "bbe3ba41-917e-4ce6-89da-77f1d21992ce",
                        "name": "2",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 350,
                            "y": 140,
                            "width": 100,
                            "height": 120
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 120
                            },
                            {
                                "x": 100,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "3951f435-d212-40f5-af60-e6f22afdb1bf"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "f5b2ca9a-9fb0-41e4-85b6-17723d7e09f7"
                        }
                    },
                    {
                        "id": "4b716db3-2c29-4860-9984-c5be03a21b49",
                        "name": "1",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 470,
                            "y": 140,
                            "width": 70,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 70,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "f5b2ca9a-9fb0-41e4-85b6-17723d7e09f7"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "69091741-1d13-40ea-8d2c-c9c02e65ae46"
                        }
                    },
                    {
                        "id": "28794e6a-5d7f-47d8-8b00-fcaa6a96ca31",
                        "name": "2",
                        "type": "PetriNetArc",
                        "owner": null,
                        "bounds": {
                            "x": 60,
                            "y": 140,
                            "width": 90,
                            "height": 1
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 90,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "d905518c-f1e1-41b2-a4ca-7a8c6950033e"
                        },
                        "target": {
                            "direction": "Left",
                            "element": "af3e9a50-aa85-4668-b46d-11b0dc407f20"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    private PetriNets() {
        // do not instantiate
    }
}
