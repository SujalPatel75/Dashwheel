"""
Bakes a 3D model of the driver's car into a Dashwheel car pack (.dwcar), the
file Settings > My car > Car picture (or the companion app) takes. EXPERIMENTAL.

Run with Blender (4.2 or newer), in the background:

    blender -b my_car.blend --python tools/mycar/bake_car.py -- my-car.dwcar
    blender -b --python tools/mycar/bake_car.py -- my-car.dwcar --model my_car.glb

Options after "--":
    OUT.dwcar           where the pack goes (required)
    --model FILE        a .glb, .gltf, .obj or .fbx to import into an empty scene
                        (without it, the .blend opened by Blender is used)
    --nose +x           which way the car's nose points: +x, -x, +y or -y (default +x;
                        glTF and most downloads face -y)
    --hide A,B          objects to leave out (stray parts, a ground plane)
    --name "C4 Picasso" the car's name in the pack
    --keep-world        keep the .blend's own world, lights and exposure (a model that
                        brings its own sky for realistic reflections) instead of the
                        plain studio

The pack is a zip: car.json, side.png (nose to the right), top.png (nose up),
hero.png (three-quarter front), back.png and front.png (seen from each end, for
the Car problems map), each on a transparent background, plus where the wheels,
the bumpers and the lamps are on each picture (fractions of its width and
height), so the app can draw its signals on the car. The lamps are found by
material or object name (tail light, headlight...); without them the app looks
for the red of the rear lamps itself. The wheels are found
by name (wheel, tyre, tire, rim) or, failing that, as the low round parts at
the four corners. The app trims each picture to the car itself.
"""

import math
import os
import sys
import tempfile
import zipfile
import json
import re

import bpy
from mathutils import Euler, Matrix, Vector


def args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not argv:
        sys.exit("usage: blender -b model.blend --python bake_car.py -- out.dwcar [--model f] [--nose +x] [--hide a,b] [--name n]")
    opts = {"out": argv[0], "model": None, "nose": "+x", "hide": [], "name": "", "keep_world": False}
    i = 1
    while i < len(argv):
        key = argv[i].lstrip("-")
        if key == "keep-world":
            opts["keep_world"] = True
            i += 1
            continue
        val = argv[i + 1] if i + 1 < len(argv) else ""
        if key == "hide":
            opts["hide"] = [s.strip() for s in val.split(",") if s.strip()]
        elif key in opts:
            opts[key] = val
        i += 2
    return opts


def load_model(path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    ext = os.path.splitext(path)[1].lower()
    if ext in (".glb", ".gltf"):
        bpy.ops.import_scene.gltf(filepath=path)
    elif ext == ".obj":
        bpy.ops.wm.obj_import(filepath=path)
    elif ext == ".fbx":
        bpy.ops.import_scene.fbx(filepath=path)
    else:
        sys.exit("unknown model type: " + ext)


def car_meshes(hide):
    meshes = []
    for o in bpy.data.objects:
        if o.type != "MESH" or o.name in hide or o.hide_render:
            continue
        dims = o.dimensions
        # A ground plane: wide and flat, no thickness.
        if dims.z < 1e-4 and max(dims.x, dims.y) > 5:
            o.hide_render = True
            continue
        meshes.append(o)
    return meshes


def world_box(objs):
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for o in objs:
        for c in o.bound_box:
            p = o.matrix_world @ Vector(c)
            lo = Vector(map(min, lo, p))
            hi = Vector(map(max, hi, p))
    return lo, hi


def nose_turn(nose):
    """The turn about Z that brings the nose to +x."""
    return {"+x": 0.0, "-x": math.pi, "+y": -math.pi / 2, "-y": math.pi / 2}.get(nose, 0.0)


VIEWS = ("side", "top", "hero", "back", "front")

WHEEL_NAME = re.compile(r"(^|[^a-z])(wheel|tyre|tire|rim)s?([^a-z]|$)")


def find_wheels(meshes, lo, hi):
    """The four wheel centres (front left, front right, rear left, rear right), car space with the nose to +x."""
    length = hi.x - lo.x
    width = hi.y - lo.y
    height = hi.z - lo.z
    # Whole words only: "CargoTrim" is no rim.
    named = [o for o in meshes if WHEEL_NAME.search(o.name.lower())]
    pool = named or meshes
    corners = {"fl": [], "fr": [], "rl": [], "rr": []}
    for o in pool:
        a, b = world_box([o])
        c = (a + b) / 2
        d = b - a
        if not named:
            # Low, near a side, about as tall as it is long: a wheel's parts.
            if c.z > lo.z + height * 0.35 or abs(c.y - (lo.y + hi.y) / 2) < width * 0.3:
                continue
            if d.x > length * 0.3 or d.z > height * 0.6:
                continue
        front = c.x > (lo.x + hi.x) / 2
        left = c.y > (lo.y + hi.y) / 2
        corners[("f" if front else "r") + ("l" if left else "r")].append(c)
    wheels = {}
    for k, pts in corners.items():
        if pts:
            wheels[k] = sum(pts, Vector()) / len(pts)
    return wheels if len(wheels) == 4 else None


TAIL_NAME = re.compile(r"tail.?(light|lamp|clear|reflector)|rear.?(lamp|light)|stop.?lamp|brake.?light|feu.?arr|r[uü]ckl")
HEAD_NAME = re.compile(r"head.?(light|lamp|lens)|phare|scheinwerfer")


def lamp_centres(meshes, pattern, facing):
    """
    The car's left and right lamp, as the area-weighted middle of the faces
    whose material (or object) name matches [pattern], weighted by how much
    they face [facing] (+1: the nose, -1: the tail), so the part wrapping round
    the side doesn't pull it off. Car space, nose to +x, the car's left at +y.
    """
    sums = {True: [Vector(), 0.0], False: [Vector(), 0.0]}
    for o in meshes:
        mats = [s.material.name.lower() if s.material else "" for s in o.material_slots]
        whole = bool(pattern.search(o.name.lower()))
        if not whole and not any(pattern.search(m) for m in mats):
            continue
        mw = o.matrix_world
        rot = mw.to_3x3()
        for poly in o.data.polygons:
            if not whole and not (poly.material_index < len(mats) and pattern.search(mats[poly.material_index])):
                continue
            n = (rot @ poly.normal).normalized()
            wgt = poly.area * max(0.0, n.x * facing)
            if wgt <= 0:
                continue
            c = mw @ poly.center
            acc = sums[c.y > 0]
            acc[0] += c * wgt
            acc[1] += wgt
    if sums[True][1] <= 0 or sums[False][1] <= 0:
        return None
    return [sums[True][0] / sums[True][1], sums[False][0] / sums[False][1]]


def main():
    o = args()
    if o["model"]:
        load_model(o["model"])
    sc = bpy.context.scene
    meshes = car_meshes(set(o["hide"]))
    if not meshes:
        sys.exit("no meshes to bake")
    for n in o["hide"]:
        if n in bpy.data.objects:
            bpy.data.objects[n].hide_render = True

    # Turn the whole car so its nose points to +x, centred on the origin, wheels on the ground.
    turn = nose_turn(o["nose"])
    root = bpy.data.objects.new("bake_root", None)
    sc.collection.objects.link(root)
    for m in [ob for ob in bpy.data.objects if ob.parent is None and ob is not root]:
        m.parent = root
        m.matrix_parent_inverse = Matrix.Identity(4)
    root.rotation_euler = Euler((0, 0, turn))
    bpy.context.view_layer.update()
    lo, hi = world_box(meshes)
    root.location = Vector((-(lo.x + hi.x) / 2, -(lo.y + hi.y) / 2, -lo.z))
    bpy.context.view_layer.update()
    lo, hi = world_box(meshes)
    length, width, height = hi.x - lo.x, hi.y - lo.y, hi.z - lo.z

    # Studio light: a soft sky over a dark floor, and a camera.
    keep = o["keep_world"] and sc.world is not None
    world = sc.world if keep else bpy.data.worlds.new("bake_studio")
    if not keep:
        world.use_nodes = True
        nt = world.node_tree
        nt.nodes.clear()
        tc = nt.nodes.new("ShaderNodeTexCoord")
        sep = nt.nodes.new("ShaderNodeSeparateXYZ")
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        bg = nt.nodes.new("ShaderNodeBackground")
        out = nt.nodes.new("ShaderNodeOutputWorld")
        nt.links.new(tc.outputs["Generated"], sep.inputs[0])
        nt.links.new(sep.outputs["Z"], ramp.inputs[0])
        ramp.color_ramp.elements[0].position = 0.45
        ramp.color_ramp.elements[0].color = (0.05, 0.05, 0.06, 1)
        ramp.color_ramp.elements[1].position = 0.75
        ramp.color_ramp.elements[1].color = (0.9, 0.92, 0.95, 1)
        nt.links.new(ramp.outputs[0], bg.inputs["Color"])
        bg.inputs["Strength"].default_value = 1.2
        nt.links.new(bg.outputs[0], out.inputs[0])
        sc.world = world
    if not any(ob.type == "LIGHT" for ob in bpy.data.objects):
        sun = bpy.data.objects.new("bake_sun", bpy.data.lights.new("bake_sun", "SUN"))
        sun.data.energy = 3.0
        sun.rotation_euler = Euler((math.radians(35), 0, math.radians(30)))
        sc.collection.objects.link(sun)

    cam = bpy.data.objects.new("bake_cam", bpy.data.cameras.new("bake_cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.clip_end = 500
    sc.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items] else "BLENDER_EEVEE"
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    if not keep:
        sc.view_settings.exposure = 1.1

    tmp = tempfile.mkdtemp(prefix="dwcar_")
    far = max(length, width, height) * 10

    def ortho(name, loc, rot, span, w, h):
        cam.data.type = "ORTHO"
        cam.data.ortho_scale = span
        cam.location = loc
        cam.rotation_euler = Euler([math.radians(a) for a in rot])
        sc.render.resolution_x, sc.render.resolution_y = w, h
        sc.render.filepath = os.path.join(tmp, name + ".png")
        bpy.ops.render.render(write_still=True)

    def project(p, w, h):
        """Where the world point p lands on the last render, as fractions."""
        from bpy_extras.object_utils import world_to_camera_view
        v = world_to_camera_view(sc, cam, p)
        return [round(v.x, 4), round(1 - v.y, 4)]

    anchors = {}
    wheels = find_wheels(meshes, lo, hi)
    mid_z = (lo.z + hi.z) / 2

    # Side: from the car's right, nose to the right.
    span = max(length, height * 2) * 1.08
    ortho("side", (0, -far, mid_z), (90, 0, 0), span, 1400, 700)
    side = {"nose": project(Vector((hi.x, 0, mid_z)), 1400, 700), "tail": project(Vector((lo.x, 0, mid_z)), 1400, 700)}
    if wheels:
        side["wheels"] = [project(wheels["fr"], 1400, 700), project(wheels["rr"], 1400, 700)]
    anchors["side"] = side

    # Top: from above, nose up.
    span = max(length, width * 1.6) * 1.08
    ortho("top", (0, 0, far), (0, 0, -90), span, 600, 1000)
    top = {"nose": project(Vector((hi.x, 0, hi.z)), 600, 1000), "tail": project(Vector((lo.x, 0, hi.z)), 600, 1000)}
    if wheels:
        top["wheels"] = [project(wheels[k], 600, 1000) for k in ("fl", "fr", "rl", "rr")]
    anchors["top"] = top

    # Each end, the car's lamps seen face on (the Car problems map). From behind
    # the car's left is on the picture's left, from the front on its right.
    span = max(width, height * 1000 / 800) * 1.08
    ortho("back", (-far, 0, mid_z), (90, 0, -90), span, 1000, 800)
    back = {}
    lamps = lamp_centres(meshes, TAIL_NAME, -1)
    if lamps:
        back["lights"] = [project(p, 1000, 800) for p in lamps]
    anchors["back"] = back
    ortho("front", (far, 0, mid_z), (90, 0, 90), span, 1000, 800)
    front = {}
    lamps = lamp_centres(meshes, HEAD_NAME, 1)
    if lamps:
        front["lights"] = [project(p, 1000, 800) for p in lamps]
    anchors["front"] = front

    # Three-quarter front, from the right.
    cam.data.type = "PERSP"
    cam.data.lens = 62
    size = max(length, width, height)
    cam.location = Vector((size * 1.35, -size * 1.2, size * 0.5))
    cam.rotation_euler = (Vector((0, 0, height * 0.5)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    sc.render.resolution_x, sc.render.resolution_y = 1400, 900
    sc.render.filepath = os.path.join(tmp, "hero.png")
    bpy.ops.render.render(write_still=True)

    car = {
        "v": 1,
        "name": o["name"],
        "views": {v: v + ".png" for v in VIEWS},
        "anchors": anchors,
    }
    with zipfile.ZipFile(o["out"], "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("car.json", json.dumps(car, indent=1))
        for v in VIEWS:
            z.write(os.path.join(tmp, v + ".png"), v + ".png")
    print("BAKED", os.path.abspath(o["out"]), "wheels found" if wheels else "no wheels found",
          "tail lamps found" if "lights" in back else "no tail lamps found",
          "headlights found" if "lights" in front else "no headlights found")


main()
