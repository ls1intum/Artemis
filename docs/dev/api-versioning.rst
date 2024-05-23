API Versioning
===========================

**Disclaimer**: As we only want to activate API versioning for REST conform endpoints and ensuring such conformity takes time, versioning only applies to endpoints marked by ``@UseVersioning``. Only use this annotation for verified REST conform endpoints. Consult with maintainers if you are unsure. Once all endpoints are verified, we can remove this annotation and activate versioning on default.

The API paths are prefixed with an optional version number. If the version number is not specified, the latest version is assumed. For example, if the latest version is 4, ``/api/v4/...`` is the same as ``/api/...``.

To create a new API version, extend the list of versions in the `VersioningConfiguration`. All endpoints with a lower limit will automatically support the new version.

There a three ways to specify what version an endpoint supports:
    - No versioning annotation: The endpoint supports all versions Artemis supports.
    - ``@VersionRange``: The endpoint supports all versions in the specified range or lower limit.
    - ``@VersionRanges``: The endpoint supports all versions in the specified ranges or lower limits. This annotation contains a list of ``@VersionRange`` annotations.

If a range specifies the starting and end version, an actual range is specified. ``@VersionRange(start = 1, end = 3)`` specifies that the endpoint accepts versions 1, 2, and 3, assuming that Artemis supports at least until version 3. If a range specifies only the starting version, a lower limit is specified. ``@VersionRange(1)`` specifies that the endpoint accepts all versions starting from 1. ``@VersionRanges`` with no supplied parameters gets interpreted as no annotation and hence supporting all versions.

If you need to deviate from the default versioning scheme, you can use the ``@IgnoreGlobalMapping`` annotation. See the JavaDoc for more information. Use with caution!

The ``VersioningTest`` tests all endpoints for correct versioning configuration and conflicts.

Currently, there is no documentation of the API available. Please refer to the code base for any available endpoints.
