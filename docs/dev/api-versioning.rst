API Versioning
===========================

The API paths are prefixed with an optional version number. If the version number is not specified, the latest version is assumed. For example, if the latest version is 4, ``/api/v4/...`` is the same as ``/api/...``.

To create a new API version, extend the list of versions in the `VersioningConfiguration`. All endpoints with a lower limit will automatically support the new version.

There a three ways to specify what version an endpoint supports:
    - No versioning annotation: The endpoint supports all versions Artemis supports.
    - ``@VersionRange``: The endpoint supports all versions in the specified range or limit.
    - ``@VersionRanges``: The endpoint supports all versions in the specified ranges or limits. This annotation contains a list of ``@VersionRange`` annotations.

If a range contains two elements, an actual range is specified. ``@VersionRange({1,3})`` specifies that the endpoint accepts versions 1, 2, and 3, assuming that Artemis supports at least until version 3. If a range contains only one element, a lower limit is specified. ``@VersionRange({1})`` specifies that the endpoint accepts all versions starting from 1. ``@VersionRanges`` with no supplied parameters gets interpreted as no annotation. Hence, qualifies for all versions. Other configurations are illegal.

If you need to deviate from the default versioning scheme, you can use the ``@IgnoreGlobalMapping`` annotation. See the JavaDoc for more information. Use with caution!

The ``VersioningTest`` tests all endpoints for correct versioning configuration and conflicts.

Currently, there is no documentation of the API available. Please refer to the code base for any available endpoints.
