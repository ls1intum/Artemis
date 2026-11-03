#!/usr/bin/env python3
"""Generates the architecture diagrams of the system design page as UML 2 SVG files.

The diagrams are drawn from this file and not by hand, so that a change to the architecture is a change to the
text of a diagram and shows up in the review. The SVG files are committed, because the documentation site
includes them, and `--check` fails when a committed file differs from what this file generates, which
catches a diagram that was edited by hand or a generator that was changed without regenerating.

Layout rules, so that the diagrams stay readable when somebody changes them:
  * Boxes are as small as their text. A helper measures the text, and positions are derived from the sizes.
  * Connectors are straight where the layout allows it: connected boxes are aligned, so that a connector is a
    horizontal or a vertical line. A bend is the exception.
  * Text never sits on a filled background. A label is placed beside its connector, clear of every box.

Usage:
    python3 supporting_scripts/generate_architecture_diagrams.py            # write the SVG files
    python3 supporting_scripts/generate_architecture_diagrams.py --check    # fail when they are out of date
"""
import argparse
import math
import sys
from pathlib import Path
from xml.sax.saxutils import escape

OUT = Path(__file__).resolve().parent.parent / "documentation" / "docs" / "developer" / "assets" / "system-design"
INK = "#000000"
FONT = "Helvetica, Arial, sans-serif"
TOP_FACE = "#f4f4f4"
SIDE_FACE = "#dedede"
PROVIDER = "Distributed\nData Provider"


def tw(text, size=12, bold=False):
    """The approximate width of a text in pixels."""
    return len(text) * size * (0.62 if bold else 0.55)


class Svg:
    def __init__(self, w, h, title, desc):
        self.w, self.h, self.title, self.desc = w, h, title, desc
        self.p = []

    # -- primitives
    def rect(self, x, y, w, h, fill="#ffffff", sw=1.4):
        self.p.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" stroke="{INK}" stroke-width="{sw}"/>')

    def text(self, x, y, s, size=12, weight="400", anchor="middle"):
        self.p.append(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="{INK}">{escape(s)}</text>')

    def line(self, pts, dashed=False, arrow=None, sw=1.4):
        d = ' stroke-dasharray="7 4"' if dashed else ""
        m = f' marker-end="url(#{arrow})"' if arrow else ""
        pl = " ".join(f"{x},{y}" for x, y in pts)
        self.p.append(f'<polyline points="{pl}" fill="none" stroke="{INK}" stroke-width="{sw}"{d}{m}/>')

    # -- UML elements
    def frame(self, name, w, h):
        """The diagram frame. Called last, when the size is known, and drawn first."""
        self.w, self.h = round(w), round(h)
        tab = tw(name, 13, True) + 34
        drawn = self.p
        self.p = []
        self.rect(10, 10, self.w - 20, self.h - 20, sw=1.6)
        self.p.append(f'<polygon points="10,10 {10 + tab},10 {10 + tab},24 {10 + tab - 12},38 10,38" fill="#ffffff" stroke="{INK}" stroke-width="1.6"/>')
        self.text(22, 29, name, 13, "700", "start")
        self.p += drawn

    def icon(self, x, y, w):
        """The component icon in the top right corner of a box: a body with two tabs on its left edge."""
        ix, iy = x + w - 32, y + 9
        self.p.append(f'<rect x="{ix}" y="{iy}" width="22" height="14" fill="#ffffff" stroke="{INK}" stroke-width="1.2"/>')
        for offset in (2.5, 8.5):
            self.p.append(f'<rect x="{ix - 6}" y="{iy + offset}" width="12" height="3" fill="#ffffff" stroke="{INK}" stroke-width="1.2"/>')

    @staticmethod
    def size_comp(name, sub=None):
        """The size of a component. A name may span lines, separated by a newline."""
        names = name.split("\n")
        lines = [sub] if isinstance(sub, str) else (sub or [])
        w = max([tw(names[0], 13, True) + 12 + 44] + [tw(n, 13, True) + 24 for n in names[1:]] + [tw(line, 11) + 24 for line in lines])
        h = 46 + 16 * (len(names) - 1) + (6 + 14 * len(lines) if lines else 0)
        return round(w), h

    def comp(self, x, y, name, sub=None, w=None, h=None):
        """A component, as small as its text unless a size is given. Returns (x, y, w, h)."""
        sw_, sh_ = self.size_comp(name, sub)
        w, h = w or sw_, h or sh_
        names = name.split("\n")
        self.rect(x, y, w, h)
        self.icon(x, y, w)
        for i, n in enumerate(names):
            self.text(x + 12, y + 26 + 16 * i, n, 13, "700", "start")
        for i, line in enumerate([sub] if isinstance(sub, str) else (sub or [])):
            self.text(x + 12, y + 45 + 16 * (len(names) - 1) + i * 14, line, 11, anchor="start")
        return x, y, w, h

    def cont(self, x, y, w, h, name, sub=None):
        """A component that contains other components: the title sits at the top left."""
        self.rect(x, y, w, h)
        self.icon(x, y, w)
        self.text(x + 12, y + 26, name, 13, "700", "start")
        if sub:
            self.text(x + 12 + tw(name, 13, True) + 10, y + 26, sub, 11, anchor="start")

    def package(self, x, y, w, h, name, stereo=None, tab_right=False):
        """A package. The tab sits at the right when connectors enter the package from above at the left."""
        t = f"«{stereo}» {name}" if stereo else name
        tab = max(90, tw(t, 11, True) + 20)
        tx = x + w - tab if tab_right else x
        self.rect(x, y + 20, w, h - 20)
        self.p.append(f'<rect x="{tx}" y="{y}" width="{tab}" height="20" fill="#ffffff" stroke="{INK}" stroke-width="1.4"/>')
        self.text(tx + 8, y + 14, t, 11, "700", "start")

    def assembly(self, pts, name, label_at, stub=14, anchor="middle"):
        """Ball-and-socket assembly. pts[0] is the provider end on the edge of the providing box, pts[-1] the
        requirer end. The ball sits on the first segment, clear of the box by `stub`."""
        (x0, y0), (x1, y1) = pts[0], pts[1]
        length = math.hypot(x1 - x0, y1 - y0)
        ux, uy = (x1 - x0) / length, (y1 - y0) / length
        nx, ny = -uy, ux
        r, big = 6, 11
        cx, cy = x0 + ux * (stub + r), y0 + uy * (stub + r)
        self.p.append(f'<line x1="{x0}" y1="{y0}" x2="{cx - ux * r}" y2="{cy - uy * r}" stroke="{INK}" stroke-width="1.4"/>')
        self.p.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#ffffff" stroke="{INK}" stroke-width="1.4"/>')
        sx, sy, ex, ey = cx + big * nx, cy + big * ny, cx - big * nx, cy - big * ny
        self.p.append(f'<path d="M {sx} {sy} A {big} {big} 0 0 0 {ex} {ey}" fill="none" stroke="{INK}" stroke-width="1.4"/>')
        self.line([(cx + ux * big, cy + uy * big)] + list(pts[1:]))
        self.text(label_at[0], label_at[1], name, 11, anchor=anchor)

    def dependency(self, pts, label=None, label_at=None, anchor="start"):
        self.line(pts, dashed=True, arrow="open")
        if label:
            self.text(label_at[0], label_at[1], f"«{label}»", 10, anchor=anchor)

    # -- deployment diagram elements
    DEPTH = 12

    def node(self, x, y, w, h, name):
        """A node, drawn as a box with a lit top face and a shaded side face."""
        d = self.DEPTH
        self.p.append(f'<polygon points="{x},{y} {x + d},{y - d} {x + w + d},{y - d} {x + w},{y}" fill="{TOP_FACE}" stroke="{INK}" stroke-width="1.4" stroke-linejoin="round"/>')
        self.p.append(f'<polygon points="{x + w},{y} {x + w + d},{y - d} {x + w + d},{y + h - d} {x + w},{y + h}" fill="{SIDE_FACE}" stroke="{INK}" stroke-width="1.4" stroke-linejoin="round"/>')
        self.rect(x, y, w, h)
        self.text(x + 10, y + 20, name, 13, "700", "start")

    def nested(self, x, y, w, name):
        """Software that runs on a node."""
        self.rect(x, y, w, 34, sw=1.2)
        self.text(x + 10, y + 22, name, 12, "700", "start")

    def render(self):
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" font-family="{FONT}" role="img" aria-labelledby="t d">'
                f'<title id="t">{escape(self.title)}</title><desc id="d">{escape(self.desc)}</desc>'
                f'<defs><marker id="open" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto">'
                f'<path d="M1 1 L9 5 L1 9" fill="none" stroke="{INK}" stroke-width="1.4"/></marker></defs>'
                f'<rect width="{self.w}" height="{self.h}" fill="#ffffff"/>')
        return head + "".join(self.p) + "</svg>\n"


def node_size(name, nested, count=1):
    """The width of a node that holds the given software, and its height for `count` boxes of software."""
    w = round(max(tw(name, 13, True) + 20, *(tw(n, 12, True) + 24 + 20 for n in nested)))
    return w, 32 + 34 * count + 8 * (count - 1) + 12


def node_with(s, x, y, name, nested, w=None):
    """A node and the boxes of software on it. Returns (x, y, w, h)."""
    nw, h = node_size(name, nested, len(nested))
    w = w or nw
    s.node(x, y, w, h, name)
    for i, n in enumerate(nested):
        s.nested(x + 10, y + 32 + i * 42, w - 20, n)
    return x, y, w, h


# ====================================================================== component diagram: top-level
def top_level():
    s = Svg(0, 0, "Artemis top-level design (UML component diagram)",
            "The Artemis client and the Android and iOS apps use the REST and WebSocket interface of the core modules in the Artemis server. Git clients and build agents use the Git interface of LocalVC. "
            "The server requires a database, user management and optional AI services. LocalCI requires the distributed data provider, which the build agents require as well.")

    pad = 40  # between the inner components and the border of the server, clear of the interface balls
    core_w, core_h = s.size_comp("Core Modules", ["exercise, exam, assessment, ..."])
    vc_w, vc_h = s.size_comp("LocalVC", ["Git server"])
    ci_w, ci_h = s.size_comp("LocalCI", ["build queue"])
    inner_w = max(core_w, vc_w + 30 + ci_w)
    client_w, client_h = s.size_comp("Artemis Client", ["Angular single-page app"])
    app_h = s.size_comp("Android App")[1]

    # the mobile apps sit above the client, so the container starts lower
    core_y = 54 + 2 * (app_h + 16) + client_h // 2 - core_h // 2
    cont_y = core_y - 52
    cont_x = 30 + client_w + 160
    cx = cont_x + pad
    row2_y = core_y + core_h + 38
    cont_h = row2_y + vc_h + 26 - cont_y
    cont_w = inner_w + 2 * pad
    s.cont(cont_x, cont_y, cont_w, cont_h, "Artemis Server", "Spring Boot, Java 25")
    s.comp(cx, core_y, "Core Modules", ["exercise, exam, assessment, ..."], w=inner_w)
    s.comp(cx, row2_y, "LocalVC", ["Git server"], w=vc_w)
    ci_x = cx + inner_w - ci_w
    s.comp(ci_x, row2_y, "LocalCI", ["build queue"], w=ci_w)
    s.dependency([(cx + vc_w // 2, core_y + core_h), (cx + vc_w // 2, row2_y)])
    s.dependency([(ci_x + ci_w // 2, core_y + core_h), (ci_x + ci_w // 2, row2_y)])

    # the clients that use the REST and WebSocket interface; the client is at the height of the component that offers it
    core_mid, vc_mid = core_y + core_h // 2, row2_y + vc_h // 2
    client_y = core_mid - client_h // 2
    s.comp(30, client_y, "Artemis Client", ["Angular single-page app"])
    ios_y = client_y - 16 - app_h
    android_y = ios_y - 16 - app_h
    s.comp(30, ios_y, "iOS App", w=client_w)
    s.comp(30, android_y, "Android App", w=client_w)
    bus_x = cont_x - 50
    s.line([(30 + client_w, android_y + app_h // 2), (bus_x, android_y + app_h // 2), (bus_x, core_mid)])
    s.line([(30 + client_w, ios_y + app_h // 2), (bus_x, ios_y + app_h // 2)])
    s.p.append(f'<circle cx="{bus_x}" cy="{core_mid}" r="3" fill="{INK}"/>')
    s.assembly([(cx, core_mid), (30 + client_w, core_mid)], "REST + WebSocket", (30 + client_w + 67, core_mid - 14), stub=pad + 10)
    gc_h = s.size_comp("Git Client", ["IDE or command line"])[1]
    s.comp(30, vc_mid - gc_h // 2, "Git Client", ["IDE or command line"], w=client_w)
    s.assembly([(cx, vc_mid), (30 + client_w, vc_mid)], "Git (HTTPS, SSH)", ((30 + client_w + cont_x) / 2, vc_mid - 14), stub=pad + 10)

    # what the server requires, one box per interface at the height of its connector
    right_x = cont_x + cont_w + 125
    right = [("PostgreSQL", ["relational database"], "Hibernate (JDBC)"),
             ("User Management", ["LDAP, SAML2, OIDC"], "Authentication"),
             ("Optional AI Services", ["Iris, Athena, Hyperion, Weaviate"], "Service APIs")]
    right_w = max(s.size_comp(n, sub)[0] for n, sub, _ in right)
    for i, (name, sub, iface) in enumerate(right):
        y = cont_y + 52 + i * 76
        s.comp(right_x, y - 31, name, sub, w=right_w)
        s.assembly([(right_x, y), (cont_x + cont_w, y)], iface, ((right_x + cont_x + cont_w) / 2, y - 14))

    # build agent and distributed data provider below the server. The provider lies under LocalCI, the agent under LocalVC
    below_y = cont_y + cont_h + 58
    ba_w, ba_h = s.size_comp("Build Agent", ["runs build jobs in Docker"])
    dp_w, dp_h = s.size_comp(PROVIDER, ["Hazelcast, Redis or Local"])
    px = ci_x + ci_w // 2
    provider_x = px - 24
    agent_x = provider_x - 106 - ba_w
    s.comp(agent_x, below_y, "Build Agent", ["runs build jobs in Docker"])
    s.comp(provider_x, below_y, PROVIDER, ["Hazelcast, Redis or Local"])
    vx = cx + vc_w // 2 - 10
    s.assembly([(vx, cont_y + cont_h - 26), (vx, below_y)], "Git (HTTPS, SSH)", (vx + 22, below_y - 22), stub=40, anchor="start")
    s.assembly([(px, below_y), (px, cont_y + cont_h - 26)], "Distributed Data", (px + 22, below_y - 22), anchor="start")
    mid = below_y + ba_h // 2
    s.assembly([(provider_x, mid), (agent_x + ba_w, mid)], "Distributed Data", ((agent_x + ba_w + provider_x) / 2 - 10, mid - 14))
    s.frame("Top-Level Design", max(right_x + right_w, provider_x + dp_w) + 24, below_y + max(ba_h, dp_h) + 24)
    return s


# ====================================================================== component diagram: server architecture
def server_arch():
    s = Svg(1100, 760, "Artemis server architecture (UML component diagram)",
            "The server has a web layer, an application layer and a data layer. The application layer consists of the feature modules and the integrated code lifecycle with LocalVC and LocalCI. "
            "The data layer accesses the database. LocalCI shares state with build agents through the distributed data provider.")
    gap, margin, pad = 24, 18, 22
    rest_w, rest_h = s.size_comp("REST Resources")
    ws_w, _ = s.size_comp("WebSocket Topics")
    fm_sub = ["exercise, programming, exam, assessment,", "course, communication, lecture, ..."]
    fm_w = rest_w + gap + ws_w
    fm_h = s.size_comp("Feature Modules", fm_sub)[1]
    vc_w, vc_h = s.size_comp("LocalVC", ["Git server"])
    ci_w, _ = s.size_comp("LocalCI", ["build queue"])
    icl_w = margin + vc_w + gap + ci_w + margin
    icl_h = 20 + 20 + vc_h + 16
    layer_w = margin + fm_w + 52 + icl_w + margin

    cont_x, cont_y = 30, 136
    lx = cont_x + pad
    web_y = cont_y + 50
    web_h = 20 + 16 + rest_h + 16
    app_y = web_y + web_h + 10
    app_h = 20 + 16 + max(fm_h, icl_h) + 16
    data_y = app_y + app_h + 10
    data_h = 20 + 16 + s.size_comp("Repositories")[1] + 16
    cont_h = data_y + data_h + pad - cont_y
    cont_w = layer_w + 2 * pad
    s.cont(cont_x, cont_y, cont_w, cont_h, "Artemis Server")

    # the tabs of the layers sit at the right, clear of the connectors that enter a layer from above at the left
    s.package(lx, web_y, layer_w, web_h, "Web Layer", "layer", tab_right=True)
    fx = lx + margin
    s.comp(fx, web_y + 36, "REST Resources")
    s.comp(fx + rest_w + gap, web_y + 36, "WebSocket Topics")
    s.package(lx, app_y, layer_w, app_h, "Application Layer", "layer", tab_right=True)
    fm_y = app_y + 36 + (max(fm_h, icl_h) - fm_h) // 2
    s.comp(fx, fm_y, "Feature Modules", fm_sub, w=fm_w)
    icl_x = fx + fm_w + 52
    icl_y = app_y + 36 + (max(fm_h, icl_h) - icl_h) // 2
    s.package(icl_x, icl_y, icl_w, icl_h, "Integrated Code Lifecycle", "subsystem")
    s.comp(icl_x + margin, icl_y + 40, "LocalVC", ["Git server"], w=vc_w)
    ci_x = icl_x + margin + vc_w + gap
    s.comp(ci_x, icl_y + 40, "LocalCI", ["build queue"], w=ci_w)
    # the data layer is as wide as the box it holds, which leaves the way below the code lifecycle free
    s.package(lx, data_y, margin + fm_w + margin, data_h, "Data Layer", "layer", tab_right=True)
    repo = s.comp(fx, data_y + 36, "Repositories", w=fm_w)

    # the web layer calls the feature modules, straight down from each box
    s.dependency([(fx + rest_w // 2, web_y + 36 + rest_h), (fx + rest_w // 2, fm_y)])
    s.dependency([(fx + rest_w + gap + ws_w // 2, web_y + 36 + rest_h), (fx + rest_w + gap + ws_w // 2, fm_y)])
    mid = fm_y + fm_h // 2
    s.dependency([(fx + fm_w, mid), (icl_x, mid)])
    s.dependency([(fx + fm_w // 2, fm_y + fm_h), (fx + fm_w // 2, data_y + 36)], "use", (fx + fm_w // 2 + 10, (fm_y + fm_h + data_y + 36) / 2 + 4))

    # the client uses the web layer
    cl_w, cl_h = s.size_comp("Artemis Client")
    mid_x = lx + layer_w // 2
    s.comp(mid_x - cl_w // 2, 50, "Artemis Client")
    s.assembly([(mid_x, web_y + 20), (mid_x, 50 + cl_h)], "REST + WebSocket", (mid_x + 22, 50 + cl_h + 34), stub=web_y - cont_y + 32, anchor="start")

    # below the server
    below_y = cont_y + cont_h + 56
    db_w, db_h = s.size_comp("Database")
    s.comp(fx + fm_w // 2 - db_w // 2, below_y, "Database")
    dx = fx + fm_w // 2
    s.assembly([(dx, below_y), (dx, data_y + 36 + repo[3])], "Hibernate", (dx + 22, below_y - 22), anchor="start")
    dp_w, _ = s.size_comp(PROVIDER)
    px = ci_x + ci_w // 2
    provider_x = px - dp_w // 2
    s.comp(provider_x, below_y, PROVIDER)
    s.assembly([(px, below_y), (px, icl_y + 40 + vc_h)], "Distributed Data", (px + 22, below_y - 22), anchor="start")
    agent_x = provider_x + dp_w + 116
    ba_w, _ = s.size_comp("Build Agent")
    s.comp(agent_x, below_y, "Build Agent")
    mid = below_y + db_h // 2
    s.assembly([(provider_x + dp_w, mid), (agent_x, mid)], "Distributed Data", ((provider_x + dp_w + agent_x) / 2 + 10, mid - 14))
    s.frame("Server Architecture", max(cont_x + cont_w, agent_x + ba_w) + 24, below_y + 62 + 24)
    return s


# ====================================================================== deployment diagram
def deployment():
    s = Svg(1030, 640, "Artemis deployment overview (UML deployment diagram)",
            "User devices reach the reverse proxy, which forwards to the core nodes running the Artemis server with LocalVC and LocalCI. "
            "A broker node relays websocket messages, a distributed data server hosts Redis or Valkey, and a database server hosts PostgreSQL. "
            "Build agent nodes run the build jobs. They clone repositories from the Artemis server and share job state through the distributed data server.")
    d = Svg.DEPTH

    # every box of the middle column has the width of the widest one, so that vertical connectors are straight
    column_w = max(node_size("Distributed Data Server", ["Redis or Valkey"])[0], node_size("Core Node", ["Artemis Server"])[0], node_size("Build Agent Node", ["Artemis Build Agent"])[0])
    row = 44  # between two rows of boxes, the length of a vertical connector
    broker_y = 56
    core_y = broker_y + 78 + row + d
    redis_y = core_y + 78 + row + d
    agent_y = redis_y + 78 + row + d
    chain = core_y + 34  # the height of the horizontal connectors

    ud = node_with(s, 30, chain - 90, "User Device", ["Web Browser", "Git Client"])
    proxy_w = node_size("Reverse Proxy", ["nginx"])[0]
    proxy_x = ud[0] + ud[2] + d + 66
    col_x = proxy_x + proxy_w + d + 96
    node_with(s, proxy_x, core_y, "Reverse Proxy", ["nginx"], w=proxy_w)
    s.line([(ud[0] + ud[2] + d, chain), (proxy_x, chain)])
    s.text((ud[0] + ud[2] + d + proxy_x) / 2, chain - 8, "«HTTPS»", 10)
    s.line([(proxy_x + proxy_w + d, chain), (col_x, chain)])
    s.text((proxy_x + proxy_w + d + col_x) / 2, chain - 8, "«HTTP, WebSocket»", 10)

    node_with(s, col_x, core_y, "Core Node", ["Artemis Server"], w=column_w)
    node_with(s, col_x, broker_y, "Broker Node", ["ActiveMQ"], w=column_w)
    node_with(s, col_x, redis_y, "Distributed Data Server", ["Redis or Valkey"], w=column_w)
    node_with(s, col_x, agent_y, "Build Agent Node", ["Artemis Build Agent"], w=column_w)

    vx = col_x + column_w // 2 + d // 2  # inside the width of the front and of the top face of every box
    s.line([(vx, broker_y + 78), (vx, core_y - d)])
    s.text(vx + 10, (broker_y + 78 + core_y - d) / 2 + 4, "«STOMP»", 10, anchor="start")
    s.line([(vx, core_y + 78), (vx, redis_y - d)])
    s.text(vx + 10, (core_y + 78 + redis_y - d) / 2 + 4, "«Redis»", 10, anchor="start")
    s.line([(vx, redis_y + 78), (vx, agent_y - d)])
    s.text(vx + 10, (redis_y + 78 + agent_y - d) / 2 + 4, "«Redis»", 10, anchor="start")

    right_x = col_x + column_w + d + 84
    db_w = node_size("Database Server", ["PostgreSQL"])[0]
    node_with(s, right_x, core_y, "Database Server", ["PostgreSQL"], w=db_w)
    s.line([(col_x + column_w + d, chain), (right_x, chain)])
    s.text((col_x + column_w + d + right_x) / 2, chain - 8, "«Hibernate»", 10)

    # the build agents clone from the server: a connector that leaves at the side and returns at the side
    bx = right_x - 42
    s.line([(col_x + column_w + d, core_y + 62), (bx, core_y + 62), (bx, agent_y + 62), (col_x + column_w + d, agent_y + 62)])
    s.text(bx + 8, (core_y + agent_y) / 2 + 80, "«Git (HTTPS, SSH)»", 10, anchor="start")
    s.frame("Deployment Overview", max(right_x + db_w + d, bx + 8 + tw("«Git (HTTPS, SSH)»", 10)) + 24, agent_y + 78 + 24)
    return s


DIAGRAMS = {"TopLevelDesign.svg": top_level, "ServerArchitecture.svg": server_arch, "DeploymentOverview.svg": deployment}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="fail when a committed SVG file differs from the generated one")
    parser.add_argument("--out", type=Path, default=OUT, help="the directory of the SVG files")
    args = parser.parse_args()
    outdated = []
    for name, diagram in DIAGRAMS.items():
        text = diagram().render()
        path = args.out / name
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != text:
                outdated.append(name)
        else:
            path.write_text(text, encoding="utf-8")
    if outdated:
        print("These diagrams differ from what generate_architecture_diagrams.py draws: " + ", ".join(outdated))
        print("Change the generator and run it without --check. Do not edit the SVG files by hand.")
        return 1
    print("Architecture diagrams are up to date." if args.check else "Architecture diagrams written.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
