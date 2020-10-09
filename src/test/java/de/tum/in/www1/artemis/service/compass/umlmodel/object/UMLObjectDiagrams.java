package de.tum.in.www1.artemis.service.compass.umlmodel.object;

final class UMLObjectDiagrams {

    static final String OBJECT_MODEL_1 = """
            {
                "version": "2.0.0",
                "type": "ObjectDiagram",
                "size": {
                    "width": 680,
                    "height": 640
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "cf26446e-06ea-4e25-99c4-ded25948e856",
                        "name": "MyHouse",
                        "type": "ObjectName",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 50,
                            "width": 200,
                            "height": 130
                        },
                        "attributes": [
                            "31180cd7-9813-415b-9b1c-b2b00e1a9807",
                            "71d6a25e-df58-44d8-8f37-cb6663a8bf27"
                        ],
                        "methods": [
                            "8b99b905-fd77-4911-9292-a2a663bce464"
                        ]
                    },
                    {
                        "id": "31180cd7-9813-415b-9b1c-b2b00e1a9807",
                        "name": "room = room1",
                        "type": "ObjectAttribute",
                        "owner": "cf26446e-06ea-4e25-99c4-ded25948e856",
                        "bounds": {
                            "x": 0,
                            "y": 90,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "71d6a25e-df58-44d8-8f37-cb6663a8bf27",
                        "name": "abc = def",
                        "type": "ObjectAttribute",
                        "owner": "cf26446e-06ea-4e25-99c4-ded25948e856",
                        "bounds": {
                            "x": 0,
                            "y": 120,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "8b99b905-fd77-4911-9292-a2a663bce464",
                        "name": "destroy()",
                        "type": "ObjectMethod",
                        "owner": "cf26446e-06ea-4e25-99c4-ded25948e856",
                        "bounds": {
                            "x": 0,
                            "y": 150,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "f26c93d7-fa5f-46a9-9b90-131b552a9f5d",
                        "name": "MyCat",
                        "type": "ObjectName",
                        "owner": null,
                        "bounds": {
                            "x": 370,
                            "y": 40,
                            "width": 200,
                            "height": 130
                        },
                        "attributes": [
                            "367852d8-8f33-4f73-b0b9-c6a242e89169",
                            "53cbced5-14b8-4027-8b85-b657bd999d35"
                        ],
                        "methods": [
                            "1ba74c00-c9b5-40dc-a7be-a7f2310f3c95"
                        ]
                    },
                    {
                        "id": "367852d8-8f33-4f73-b0b9-c6a242e89169",
                        "name": "sleep = true",
                        "type": "ObjectAttribute",
                        "owner": "f26c93d7-fa5f-46a9-9b90-131b552a9f5d",
                        "bounds": {
                            "x": 370,
                            "y": 80,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "53cbced5-14b8-4027-8b85-b657bd999d35",
                        "name": "blah = false",
                        "type": "ObjectAttribute",
                        "owner": "f26c93d7-fa5f-46a9-9b90-131b552a9f5d",
                        "bounds": {
                            "x": 370,
                            "y": 110,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "1ba74c00-c9b5-40dc-a7be-a7f2310f3c95",
                        "name": "miau()",
                        "type": "ObjectMethod",
                        "owner": "f26c93d7-fa5f-46a9-9b90-131b552a9f5d",
                        "bounds": {
                            "x": 370,
                            "y": 140,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "name": "YourView",
                        "type": "ObjectName",
                        "owner": null,
                        "bounds": {
                            "x": 210,
                            "y": 300,
                            "width": 200,
                            "height": 190
                        },
                        "attributes": [
                            "07f577f2-03b5-4e7a-8828-12da70af314a",
                            "c0075bc0-c476-491c-a922-a78b060f2142",
                            "d3ca760c-89b9-4645-89e8-0c0a86860ece"
                        ],
                        "methods": [
                            "f04e70be-a7d5-47a6-b906-1253ff5a9567",
                            "bc2c12ea-c68b-477c-aa51-e4ae2b3f22d9"
                        ]
                    },
                    {
                        "id": "07f577f2-03b5-4e7a-8828-12da70af314a",
                        "name": "model",
                        "type": "ObjectAttribute",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 340,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "c0075bc0-c476-491c-a922-a78b060f2142",
                        "name": "view",
                        "type": "ObjectAttribute",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 370,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "d3ca760c-89b9-4645-89e8-0c0a86860ece",
                        "name": "controller",
                        "type": "ObjectAttribute",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 400,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "f04e70be-a7d5-47a6-b906-1253ff5a9567",
                        "name": "model()",
                        "type": "ObjectMethod",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 430,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "bc2c12ea-c68b-477c-aa51-e4ae2b3f22d9",
                        "name": "start()",
                        "type": "ObjectMethod",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 460,
                            "width": 200,
                            "height": 30
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "01cf00b7-77cf-4c4a-8e62-ee0114c011d3",
                        "name": "",
                        "type": "ObjectLink",
                        "owner": null,
                        "bounds": {
                            "x": 410,
                            "y": 170,
                            "width": 60,
                            "height": 225
                        },
                        "path": [
                            {
                                "x": 0,
                                "y": 225
                            },
                            {
                                "x": 60,
                                "y": 225
                            },
                            {
                                "x": 60,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Right",
                            "element": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "f26c93d7-fa5f-46a9-9b90-131b552a9f5d"
                        }
                    },
                    {
                        "id": "c895ce8e-4216-46d1-baeb-6ec6eba35759",
                        "name": "",
                        "type": "ObjectLink",
                        "owner": null,
                        "bounds": {
                            "x": 200,
                            "y": 115,
                            "width": 110,
                            "height": 185
                        },
                        "path": [
                            {
                                "x": 110,
                                "y": 185
                            },
                            {
                                "x": 110,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28"
                        },
                        "target": {
                            "direction": "Right",
                            "element": "cf26446e-06ea-4e25-99c4-ded25948e856"
                        }
                    },
                    {
                        "id": "ea1a2901-eefd-4ffe-a64c-b8e84f977c48",
                        "name": "",
                        "type": "ObjectLink",
                        "owner": null,
                        "bounds": {
                            "x": 100,
                            "y": 180,
                            "width": 110,
                            "height": 215
                        },
                        "path": [
                            {
                                "x": 110,
                                "y": 215
                            },
                            {
                                "x": 0,
                                "y": 215
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "cf26446e-06ea-4e25-99c4-ded25948e856"
                        }
                    },
                    {
                        "id": "7a5d9537-6ad6-4ace-bb0b-d2be946133c4",
                        "name": "",
                        "type": "ObjectLink",
                        "owner": null,
                        "bounds": {
                            "x": 100,
                            "y": 0,
                            "width": 370,
                            "height": 50
                        },
                        "path": [
                            {
                                "x": 370,
                                "y": 40
                            },
                            {
                                "x": 370,
                                "y": 0
                            },
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
                            "direction": "Up",
                            "element": "f26c93d7-fa5f-46a9-9b90-131b552a9f5d"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "cf26446e-06ea-4e25-99c4-ded25948e856"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    static final String OBJECT_MODEL_2 = """
            {
                "version": "2.0.0",
                "type": "ObjectDiagram",
                "size": {
                    "width": 680,
                    "height": 520
                },
                "interactive": {
                    "elements": [],
                    "relationships": []
                },
                "elements": [
                    {
                        "id": "cf26446e-06ea-4e25-99c4-ded25948e856",
                        "name": "Vulcano",
                        "type": "ObjectName",
                        "owner": null,
                        "bounds": {
                            "x": 0,
                            "y": 40,
                            "width": 200,
                            "height": 70
                        },
                        "attributes": [],
                        "methods": [
                            "8b99b905-fd77-4911-9292-a2a663bce464"
                        ]
                    },
                    {
                        "id": "8b99b905-fd77-4911-9292-a2a663bce464",
                        "name": "blub()",
                        "type": "ObjectMethod",
                        "owner": "cf26446e-06ea-4e25-99c4-ded25948e856",
                        "bounds": {
                            "x": 0,
                            "y": 80,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "name": "TheCar",
                        "type": "ObjectName",
                        "owner": null,
                        "bounds": {
                            "x": 210,
                            "y": 290,
                            "width": 200,
                            "height": 130
                        },
                        "attributes": [
                            "07f577f2-03b5-4e7a-8828-12da70af314a",
                            "c0075bc0-c476-491c-a922-a78b060f2142"
                        ],
                        "methods": [
                            "f04e70be-a7d5-47a6-b906-1253ff5a9567"
                        ]
                    },
                    {
                        "id": "07f577f2-03b5-4e7a-8828-12da70af314a",
                        "name": "fast",
                        "type": "ObjectAttribute",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 330,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "c0075bc0-c476-491c-a922-a78b060f2142",
                        "name": "speedy",
                        "type": "ObjectAttribute",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 360,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "f04e70be-a7d5-47a6-b906-1253ff5a9567",
                        "name": "drive()",
                        "type": "ObjectMethod",
                        "owner": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28",
                        "bounds": {
                            "x": 210,
                            "y": 390,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "55684e12-73c3-4c26-b326-4eba0fa65e16",
                        "name": "TheCar2",
                        "type": "ObjectName",
                        "owner": null,
                        "bounds": {
                            "x": 480,
                            "y": 290,
                            "width": 200,
                            "height": 100
                        },
                        "attributes": [
                            "b3bf773d-d451-486f-ab02-725bbc443617"
                        ],
                        "methods": [
                            "19374141-6f88-4122-9212-ca1b9f9078bf"
                        ]
                    },
                    {
                        "id": "b3bf773d-d451-486f-ab02-725bbc443617",
                        "name": "nice4",
                        "type": "ObjectAttribute",
                        "owner": "55684e12-73c3-4c26-b326-4eba0fa65e16",
                        "bounds": {
                            "x": 480,
                            "y": 330,
                            "width": 200,
                            "height": 30
                        }
                    },
                    {
                        "id": "19374141-6f88-4122-9212-ca1b9f9078bf",
                        "name": "stop()12312",
                        "type": "ObjectMethod",
                        "owner": "55684e12-73c3-4c26-b326-4eba0fa65e16",
                        "bounds": {
                            "x": 480,
                            "y": 360,
                            "width": 200,
                            "height": 30
                        }
                    }
                ],
                "relationships": [
                    {
                        "id": "ea1a2901-eefd-4ffe-a64c-b8e84f977c48",
                        "name": "",
                        "type": "ObjectLink",
                        "owner": null,
                        "bounds": {
                            "x": 100,
                            "y": 110,
                            "width": 110,
                            "height": 245
                        },
                        "path": [
                            {
                                "x": 110,
                                "y": 245
                            },
                            {
                                "x": 0,
                                "y": 245
                            },
                            {
                                "x": 0,
                                "y": 0
                            }
                        ],
                        "source": {
                            "direction": "Left",
                            "element": "ad1eb8af-adb7-4ea4-a377-86ebc49b2f28"
                        },
                        "target": {
                            "direction": "Down",
                            "element": "cf26446e-06ea-4e25-99c4-ded25948e856"
                        }
                    },
                    {
                        "id": "7a5d9537-6ad6-4ace-bb0b-d2be946133c4",
                        "name": "",
                        "type": "ObjectLink",
                        "owner": null,
                        "bounds": {
                            "x": 100,
                            "y": 0,
                            "width": 480,
                            "height": 290
                        },
                        "path": [
                            {
                                "x": 480,
                                "y": 290
                            },
                            {
                                "x": 480,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 0
                            },
                            {
                                "x": 0,
                                "y": 40
                            }
                        ],
                        "source": {
                            "direction": "Up",
                            "element": "55684e12-73c3-4c26-b326-4eba0fa65e16"
                        },
                        "target": {
                            "direction": "Up",
                            "element": "cf26446e-06ea-4e25-99c4-ded25948e856"
                        }
                    }
                ],
                "assessments": []
            }
            """;

    private UMLObjectDiagrams() {
        // do not instantiate
    }
}
