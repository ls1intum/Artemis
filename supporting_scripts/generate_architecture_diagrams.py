#!/usr/bin/env python3
"""Generates the architecture diagrams of the system design page as UML 2 SVG files.

The diagrams are drawn from this file and not by hand, so that a change to the architecture is a change to the
text of a diagram and shows up in the review. The SVG files are committed, because the documentation site
includes them, and `--check` fails when a committed file differs from what this file generates, which
catches a diagram that was edited by hand or a generator that was changed without regenerating.

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
GRAY = "#eeeeee"
FONT = "Helvetica, Arial, sans-serif"


class Svg:
    def __init__(self, w, h, title, desc):
        self.w, self.h, self.title, self.desc = w, h, title, desc
        self.p = []

    # -- primitives
    def rect(self, x, y, w, h, fill="#ffffff", dashed=False, sw=1.4):
        d = ' stroke-dasharray="7 4"' if dashed else ""
        self.p.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" stroke="{INK}" stroke-width="{sw}"{d}/>')

    def text(self, x, y, s, size=12, weight="400", anchor="middle", style="normal", fill=INK):
        self.p.append(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" font-style="{style}" text-anchor="{anchor}" fill="{fill}">{escape(s)}</text>')

    def label(self, x, y, s, size=11):
        """Text with a white halo so that it stays readable on top of lines."""
        w = len(s) * size * 0.56 + 6
        self.p.append(f'<rect x="{x - w / 2}" y="{y - size}" width="{w}" height="{size + 4}" fill="#ffffff"/>')
        self.text(x, y, s, size)

    def line(self, pts, dashed=False, arrow=None, sw=1.4):
        d = ' stroke-dasharray="7 4"' if dashed else ""
        m = f' marker-end="url(#{arrow})"' if arrow else ""
        pl = " ".join(f"{x},{y}" for x, y in pts)
        self.p.append(f'<polyline points="{pl}" fill="none" stroke="{INK}" stroke-width="{sw}"{d}{m}/>')

    # -- UML elements
    def frame(self, kind, name):
        self.rect(10, 10, self.w - 20, self.h - 20, sw=1.6)
        label = f"{kind} {name}" if kind else name
        w = len(label) * 7.4 + 30
        self.p.append(f'<polygon points="10,10 {10 + w},10 {10 + w},24 {10 + w - 12},38 10,38" fill="#ffffff" stroke="{INK}" stroke-width="1.6"/>')
        if kind:
            self.text(22, 29, kind, 13, "700", "start")
            self.text(22 + len(kind) * 7.6 + 8, 29, name, 13, "400", "start")
        else:
            self.text(22, 29, name, 13, "700", "start")

    def comp(self, x, y, w, h, name, sub=None, stereo="component", dep=False, container=False, extra=None):
        self.rect(x, y, w, h, GRAY if dep else "#ffffff", dashed=dep)
        # component icon
        ix, iy = x + w - 28, y + 8
        self.p.append(f'<rect x="{ix}" y="{iy}" width="16" height="11" fill="#ffffff" stroke="{INK}" stroke-width="1.1"/>')
        self.p.append(f'<rect x="{ix - 4}" y="{iy + 2}" width="8" height="3" fill="#ffffff" stroke="{INK}" stroke-width="1.1"/>')
        self.p.append(f'<rect x="{ix - 4}" y="{iy + 7}" width="8" height="3" fill="#ffffff" stroke="{INK}" stroke-width="1.1"/>')
        st = f"«{stereo}»"
        if container:
            self.text(x + 12, y + 20, st, 11, anchor="start")
            self.text(x + 12, y + 37, name, 14, "700", "start")
            if sub:
                self.text(x + 12, y + 53, sub, 11, anchor="start")
        else:
            self.text(x + w / 2 - 6, y + 22, st, 11)
            self.text(x + w / 2 - 6, y + 40, name, 13, "700")
            lines = [sub] if isinstance(sub, str) else (sub or [])
            if dep:
                lines = ["«deprecated»"] + lines
            for i, line in enumerate(lines):
                self.text(x + w / 2 - 6, y + 57 + i * 14, line, 11)

    def cont(self, x, y, w, h, name, sub=None):
        """A component that contains other components: the title sits at the top left."""
        self.rect(x, y, w, h)
        ix, iy = x + w - 28, y + 8
        self.p.append(f'<rect x="{ix}" y="{iy}" width="16" height="11" fill="#ffffff" stroke="{INK}" stroke-width="1.1"/>')
        self.p.append(f'<rect x="{ix - 4}" y="{iy + 2}" width="8" height="3" fill="#ffffff" stroke="{INK}" stroke-width="1.1"/>')
        self.p.append(f'<rect x="{ix - 4}" y="{iy + 7}" width="8" height="3" fill="#ffffff" stroke="{INK}" stroke-width="1.1"/>')
        self.text(x + 12, y + 20, "\u00abcomponent\u00bb", 11, anchor="start")
        self.text(x + 12, y + 37, name, 14, "700", "start")
        if sub:
            self.text(x + 12 + len(name) * 8.6 + 10, y + 37, sub, 11, anchor="start")

    def package(self, x, y, w, h, name, stereo=None):
        t = f"«{stereo}» {name}" if stereo else name
        tab = max(90, len(t) * 6.9 + 18)
        self.rect(x, y + 20, w, h - 20)
        self.p.append(f'<rect x="{x}" y="{y}" width="{tab}" height="20" fill="#ffffff" stroke="{INK}" stroke-width="1.4"/>')
        self.text(x + 8, y + 14, t, 11, "700", "start")

    def port(self, x, y):
        self.p.append(f'<rect x="{x - 5}" y="{y - 5}" width="10" height="10" fill="#ffffff" stroke="{INK}" stroke-width="1.4"/>')

    def assembly(self, pts, name, label_at=None, dashed=False):
        """Ball-and-socket assembly. pts[0] is the provider end, pts[-1] the requirer end; the ball sits on the first segment."""
        (x0, y0), (x1, y1) = pts[0], pts[1]
        L = math.hypot(x1 - x0, y1 - y0)
        ux, uy = (x1 - x0) / L, (y1 - y0) / L
        nx, ny = -uy, ux
        r, R, stub = 6, 11, 14
        cx, cy = x0 + ux * (stub + r), y0 + uy * (stub + r)
        ax, ay = cx + ux * R, cy + uy * R
        d = ' stroke-dasharray="7 4"' if dashed else ""
        # provider stub and ball
        self.p.append(f'<line x1="{x0}" y1="{y0}" x2="{cx - ux * r}" y2="{cy - uy * r}" stroke="{INK}" stroke-width="1.4"{d}/>')
        self.p.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#ffffff" stroke="{INK}" stroke-width="1.4"/>')
        # socket arc, opening towards the provider
        sx, sy, ex, ey = cx + R * nx, cy + R * ny, cx - R * nx, cy - R * ny
        self.p.append(f'<path d="M {sx} {sy} A {R} {R} 0 0 0 {ex} {ey}" fill="none" stroke="{INK}" stroke-width="1.4"/>')
        rest = [(ax, ay)] + list(pts[1:])
        self.line(rest, dashed=dashed)
        if label_at:
            self.label(label_at[0], label_at[1], name)

    def dependency(self, pts, stereo="use"):
        self.line(pts, dashed=True, arrow="open")
        if stereo:
            mx, my = (pts[0][0] + pts[-1][0]) / 2, (pts[0][1] + pts[-1][1]) / 2
            self.label(mx + 22, my + 4, f"«{stereo}»", 10)

    def node(self, x, y, w, h, name, stereo="device", dep=False, d=14, mult=None):
        fill = GRAY if dep else "#ffffff"
        dash = ' stroke-dasharray="7 4"' if dep else ""
        top = f"{x},{y} {x + d},{y - d} {x + w + d},{y - d} {x + w},{y}"
        right = f"{x + w},{y} {x + w + d},{y - d} {x + w + d},{y + h - d} {x + w},{y + h}"
        self.p.append(f'<polygon points="{top}" fill="{fill}" stroke="{INK}" stroke-width="1.4"{dash}/>')
        self.p.append(f'<polygon points="{right}" fill="{fill}" stroke="{INK}" stroke-width="1.4"{dash}/>')
        self.rect(x, y, w, h, fill, dashed=dep)
        st = f"«{stereo}»" + (" «deprecated»" if dep else "")
        self.text(x + 10, y + 18, st, 11, anchor="start")
        self.text(x + 10, y + 35, name, 13, "700", "start")
        if mult:
            self.text(x + w - 8, y + 18, f"[{mult}]", 11, anchor="end")

    def nested(self, x, y, w, h, name, stereo="execution environment", dep=False, sub=None):
        self.rect(x, y, w, h, GRAY if dep else "#ffffff", dashed=dep)
        self.text(x + 8, y + 16, f"«{stereo}»", 10, anchor="start")
        self.text(x + 8, y + 31, name, 12, "700", "start")
        rows = (["«deprecated»"] if dep else []) + list(sub or [])
        for i, line in enumerate(rows):
            self.text(x + 8, y + 45 + i * 13, line, 10, anchor="start")

    def artifact(self, x, y, w, h, name, props=None, stereo="artifact"):
        self.p.append(f'<polygon points="{x},{y} {x + w - 12},{y} {x + w},{y + 12} {x + w},{y + h} {x},{y + h}" fill="#ffffff" stroke="{INK}" stroke-width="1.3"/>')
        self.p.append(f'<polyline points="{x + w - 12},{y} {x + w - 12},{y + 12} {x + w},{y + 12}" fill="none" stroke="{INK}" stroke-width="1.1"/>')
        self.text(x + 8, y + 15, f"«{stereo}»", 10, anchor="start")
        self.text(x + 8, y + 29, name, 11, "700", "start")
        for i, s in enumerate(props or []):
            self.text(x + 8, y + 43 + i * 12, s, 10, anchor="start")

    def note(self, x, y, w, h, lines):
        self.p.append(f'<polygon points="{x},{y} {x + w - 12},{y} {x + w},{y + 12} {x + w},{y + h} {x},{y + h}" fill="#ffffff" stroke="{INK}" stroke-width="1.2"/>')
        self.p.append(f'<polyline points="{x + w - 12},{y} {x + w - 12},{y + 12} {x + w},{y + 12}" fill="none" stroke="{INK}" stroke-width="1"/>')
        for i, s in enumerate(lines):
            self.text(x + 8, y + 18 + i * 14, s, 10.5, anchor="start")

    def render(self):
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" font-family="{FONT}" role="img" aria-labelledby="t d">'
                f'<title id="t">{escape(self.title)}</title><desc id="d">{escape(self.desc)}</desc>'
                f'<defs><marker id="open" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto">'
                f'<path d="M1 1 L9 5 L1 9" fill="none" stroke="{INK}" stroke-width="1.4"/></marker></defs>'
                f'<rect width="{self.w}" height="{self.h}" fill="#ffffff"/>')
        return head + "".join(self.p) + "</svg>\n"


# ====================================================================== component diagram: top-level
def top_level():
    s = Svg(1160, 560, "Artemis top-level design (UML component diagram)",
            "The Artemis client uses the REST and WebSocket interface of the Artemis server. The server contains the core modules, LocalVC and LocalCI. "
            "It requires a database, user management and optional services, and a distributed data provider. Build agents require the Git interface of LocalVC and the distributed data.")
    s.frame(None, "Top-Level Design")

    # actors on the left
    s.comp(30, 125, 200, 80, "Artemis Client", "Angular single-page app")
    s.comp(30, 255, 200, 80, "Git Client", "IDE or command line")

    # server
    s.cont(340, 70, 440, 300, "Artemis Server", "Spring Boot, Java 25")
    s.comp(370, 140, 380, 80, "Core Modules", "exercises, exams, assessment, communication, ...")
    s.comp(370, 250, 175, 90, "LocalVC", "Git server (HTTPS, SSH)")
    s.comp(575, 250, 175, 90, "LocalCI", "build queue and results")
    s.dependency([(460, 220), (460, 250)], None)
    s.dependency([(662, 220), (662, 250)], None)

    # provided interfaces of the server
    s.port(340, 165)
    s.assembly([(340, 165), (230, 165)], "REST + WebSocket", (285, 150))
    s.port(340, 295)
    s.assembly([(340, 295), (230, 295)], "Git (HTTPS, SSH)", (285, 280))

    # right column: required interfaces
    right = [
        (150, "PostgreSQL", ["relational database"], "JDBC"),
        (240, "User Management", ["LDAP, SAML2, OIDC"], "Authentication"),
        (330, "Optional Services", ["Iris, Athena, Hyperion, Weaviate"], "Service APIs"),
    ]
    for y, name, sub, iface in right:
        s.comp(900, y - 32, 210, 64, name, sub)
        s.port(780, y)
        s.assembly([(900, y), (780, y)], iface, (840, y - 15))

    # build agent and distributed data below the server
    s.comp(330, 430, 240, 80, "Build Agent", "runs build jobs in Docker")
    s.port(420, 370)
    s.assembly([(420, 370), (420, 430)], "Git (HTTPS, SSH)", (420 + 62, 408))
    s.comp(700, 430, 260, 80, "Distributed Data Provider", "Hazelcast, Redis or Local")
    s.port(770, 370)
    s.assembly([(770, 430), (770, 370)], "Distributed Data", (770 + 68, 408))
    s.assembly([(700, 470), (570, 470)], "Distributed Data", (635, 456))
    return s


# ====================================================================== component diagram: server architecture
def server_arch():
    s = Svg(1000, 780, "Artemis server architecture (UML component diagram)",
            "The server has a web layer, an application layer and a data layer. The application layer consists of the feature modules and the integrated code lifecycle with LocalVC and LocalCI. "
            "The data layer accesses the database. LocalCI shares state with build agents through the distributed data provider.")
    s.frame(None, "Server Architecture")

    s.comp(350, 55, 300, 70, "Artemis Client", None)
    s.cont(60, 175, 880, 440, "Artemis Server", None)
    s.port(500, 175)
    s.assembly([(500, 175), (500, 125)], "REST + WebSocket", (500 + 74, 154))

    s.package(90, 225, 820, 100, "Web Layer", "layer")
    s.comp(120, 262, 260, 50, "REST Resources", None)
    s.comp(420, 262, 260, 50, "WebSocket Topics", None)
    s.package(90, 335, 820, 155, "Application Layer", "layer")
    s.comp(120, 380, 330, 100, "Feature Modules", ["exercise, programming, exam, assessment,", "course, communication, lecture, ..."])
    s.package(520, 370, 360, 115, "Integrated Code Lifecycle", "subsystem")
    s.comp(540, 398, 150, 78, "LocalVC", "Git server")
    s.comp(710, 398, 150, 78, "LocalCI", "build queue")
    s.package(90, 500, 820, 108, "Data Layer", "layer")
    s.comp(120, 530, 330, 64, "Repositories", None)

    s.dependency([(250, 312), (250, 380)], None)
    s.dependency([(550, 312), (400, 380)], None)
    s.dependency([(450, 430), (520, 430)], None)
    s.dependency([(285, 480), (285, 530)], "use")

    s.comp(120, 685, 200, 64, "Database", None)
    s.port(220, 615)
    s.assembly([(220, 685), (220, 615)], "Persistence", (220 + 42, 651))
    s.comp(420, 685, 220, 64, "Distributed Data Provider", None)
    s.port(530, 615)
    s.assembly([(530, 685), (530, 615)], "Distributed Data", (530 + 62, 651))
    s.comp(780, 685, 160, 64, "Build Agent", None)
    s.assembly([(640, 717), (780, 717)], "Distributed Data", (710, 703))
    return s


# ====================================================================== deployment diagram
def deployment():
    s = Svg(1200, 480, "Typical Artemis deployment (UML deployment diagram)",
            "User devices reach the reverse proxy, which forwards to the core nodes running the Artemis server with LocalVC and LocalCI. "
            "Build agent nodes run the build jobs. A database server hosts PostgreSQL, a distributed data server hosts Redis or Valkey, and a broker node relays websocket messages.")
    s.frame("deployment", "Typical Deployment")

    s.node(40, 130, 240, 150, "User Device", mult="0..*")
    s.nested(55, 180, 210, 42, "Web Browser")
    s.nested(55, 228, 210, 40, "Git Client")

    s.node(345, 170, 150, 80, "Reverse Proxy", stereo="execution environment")
    s.line([(294, 210), (345, 210)])
    s.label(320, 199, "\u00abHTTPS\u00bb", 10)

    s.node(600, 150, 290, 120, "Core Node", mult="1..*")
    s.nested(615, 200, 260, 56, "Artemis Server")
    s.line([(509, 210), (600, 210)])
    s.label(554, 199, "\u00abHTTP, WebSocket\u00bb", 10)

    s.node(985, 80, 170, 100, "Database Server")
    s.nested(1000, 128, 140, 42, "PostgreSQL")
    s.line([(890, 165), (925, 165), (925, 130), (985, 130)])
    s.label(955, 120, "\u00abJDBC\u00bb", 10)

    s.node(985, 205, 170, 86, "Distributed Data Server", mult="0..1")
    s.nested(1000, 246, 140, 38, "Redis or Valkey")
    s.line([(890, 215), (940, 215), (940, 262), (985, 262)])
    s.label(955, 252, "\u00abRedis\u00bb", 10)

    s.node(985, 316, 170, 86, "Broker Node", mult="0..1")
    s.nested(1000, 357, 140, 38, "ActiveMQ")
    s.line([(890, 255), (910, 255), (910, 376), (1000, 376)])
    s.label(955, 366, "\u00abSTOMP\u00bb", 10)

    s.node(600, 330, 290, 110, "Build Agent Node", mult="0..*")
    s.nested(615, 378, 260, 50, "Artemis Build Agent")
    s.line([(745, 270), (745, 330)])
    s.label(745, 304, "\u00abGit\u00bb", 10)
    return s


DIAGRAMS = {"TopLevelDesign.svg": top_level, "ServerArchitecture.svg": server_arch, "DeploymentOverview.svg": deployment}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="fail when a committed SVG file differs from the generated one")
    args = parser.parse_args()
    outdated = []
    for name, diagram in DIAGRAMS.items():
        text = diagram().render()
        path = OUT / name
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
