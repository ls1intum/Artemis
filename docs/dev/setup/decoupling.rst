.. _setup_decoupling:

Decoupled Subsystems
--------------------

Artemis bundles all subsystems (such as different exercise types, lectures, user management, etc.) into one application binary.
In some setups, it might be desirable to start only particular subsystems, independent of other subsystems.

This is for example the case when only a highly-used subsystem should be scaled to offer better
performance or when a certain functionality should not be provided by Artemis at all.

A common approach for these design goals is a microservice architecture, where logic is split into different services,
which can then operate independent of each other.

This approach however has certain drawbacks, including:

1. the onboarding is more complex for new developers
2. the setup of Artemis installations is more complicated, even for small Artemis instances
3. the migration of the code-base is difficult as a code-freeze is difficult to implement

Artemis therefor supports a different mechanism to reach the desired goals:
Subsystems of Artemis are decoupled and can be disabled, so that the corresponding code is not executed.

This allows administrators to either disable undesired functionality entirely or start multiple instances of Artemis,
where each instance only provides a subset of the functionality.
Administrators can then scale these instances independent of each other.

Server Setup
^^^^^^^^^^^^
Artemis uses Spring's ``@Profile``-annotations to indicate which Spring components (resources, services, repositories)
should be instantiated.
By adding a ``@Profile``-annotation to all components of a subsystems, administrators can then decide whether a certain
subsystem should be started.


``@Profile``-Annotation
"""""""""""""""""""""""

.. code-block:: java

    @Profile("!decoupling || lecture")
    public class LectureResource {

The ``LectureResource`` is part of the ``lecture`` subsystem and should thus only be activated if the lecture subsystem
is enabled.

Note that it is instantiated if either the ``lecture``-profile is set or if the ``decoupling`` system is not set.

If neither the ``lecture`` nor the ``decoupling``-profiles are specified, the components are instantiated, which is
useful for simple installations where all subsystems should be executed on the same Artemis instance
(such as a local development environment).

It may not always be possible to add a ``@Profile``-annotation to every component belonging to a subsystem, because
especially services and repositories may be used by other subsystems.
If this is the case, then either the code has to be refactored to remove this dependency or the annotation can not (yet)
be added.


REST-Endpoints
""""""""""""""

   .. figure:: setup/decoupling/deployment_profiles_docu.drawio.png
      :align: center


To allow the load balancer to forward request to the correct instance, a prefix has to be used that indicates which
subsystem is able to handle a request.
The default prefix is ``/api``, which every endpoint that has not (yet) been migrated uses.
Note that the `Course`-subsystem is used as example and not yet decoupled.

Every decoupled subsystem uses the prefix ``/api-SUBSYSTEM``, such as ``/api-lecture``.
The load balancer then proxies the requests accordingly.

The ``@RequestMapping``-annotation now contains the updated API-prefix:

.. code-block:: java

    @RestController
    @RequestMapping("/api-lecture")
    @Profile("!decoupling || lecture")
    public class LectureResource {


Note that the client has to be adjusted so that it uses the correct API-prefix.


WebSocket Messages
""""""""""""""""""

Each Artemis client is connected to exactly one Artemis server instance.
A WebSocket broker forwards messages sent from a server instance to a client that is **not** connected to that
particular instance (for more details, see the :ref:`corresponding section of the documentation <setup_distributed>`).

The same broker also forwards messages that the client sends to the connected server instance, but which the server can
not process because the requested functionality is not available on that particular instance.


   .. figure:: setup/decoupling/communication_quiz_websocket_save.drawio.png
      :align: center

The clients sends the message to an instance running the `Lecture`-subsystem, which authenticates the request but does
not perform other logic.
It instead relays the message to the message broker which relays it to an instance of the application server which runs
the `Quiz`-subsystem.

Note that the broker uses a queue-semantic to forward the message to **exactly one** instance and also distributes the
load to multiple instances.

The ``QuizJMSListenerService`` provides an example for this setup.


Client Setup
^^^^^^^^^^^^
The Artemis client contains all subsystems but has to dynamically hide and show components based on the subsystems
the Artemis server instances support combined.

It is sufficient if a single instance supports a given subsystem, because

1. the load balancer forwards REST-requests to the correct server instance(s), and
2. the message broker forwards Websocket message to the correct server instance.



The ``ProfileToggleService`` retrieves information about supported subsystems from the server and also automatically
updates it if new instances start or stop and subsystems become (un)available.

``Directives``
""""""""""""""
The following directives disable functionality depending on the available subsystems:

- ``jhiProfileToggle``: Components with this directive are disabled using CSS if the corresponding subsystem is not available
- ``jhiProfileToggleLink``: Links with this directive are disabled if the corresponding subsystem is not available
- ``jhiProfileToggleHide``: Components with this directive are hidden if the corresponding subsystem is not available

The directives prevent users from navigating to pages that belong to unavailable subsystems.

Example:

.. code-block:: html+ng2

    <a
        *ngIf="course.isAtLeastEditor"
        [jhiProfileToggleHide]="ProfileToggle.LECTURE"
        [routerLink]="['/course-management', course.id, 'lectures']"
        class="btn btn-primary me-1 mb-1"
        [ngbTooltip]="'entity.action.lecture' | artemisTranslate"
        id="course-card-open-lectures"
    >
        <fa-icon [icon]="faFilePdf"></fa-icon>
        <span class="d-none d-xl-inline">{{ 'entity.action.lecture' | artemisTranslate }}</span>
    </a>

This link is hidden if the `Lecture`-subsystem is not available.

``ProfileToggleGuard``
""""""""""""""""""""""

The ``ProfileToggleGuard`` ensures that users do not access pages by accident that they should not be able to access
(e.g. because they have bookmarked a page that belongs to a subsystem that is currently not available).

If a user tries to navigate to an unavailable page, they are redirected to the dashboard and are shown an alert that
the requested page is currently unavailable.

If the user is currently on a page that becomes unavailable, the user is not routed away from the page, because they
might have entered data that could be lost if the dashboard is loaded.
Instead, an alert is shown, informing them about the unavailability of the currently used subsystem, so that they can
save any data they entered e.g. to a text editor.

Once the subsystem is available again, they are automatically informed by an alert.


The ``ProfileToggleGuard`` has to be added as entry in the corresponding route at ``canActivate``.
The corresponding profile has to be added at ``data.profile``.

Example:

.. code-block:: typescript

      {
        path: ':courseId/lectures',
        component: CourseManagementTabBarComponent,
        canActivate: [ProfileToggleGuard],
        data: {
            profile: ProfileToggle.LECTURE,
        },
      }
