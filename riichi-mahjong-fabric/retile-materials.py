"""Apply the requested block materials without resampling existing tile lettering.

Requires Pillow. Outputs only changed resource-pack files and a verification report.
The table model is displayed at 1.5 horizontal scale by the server port.
"""
import argparse
import copy
import io
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-pack', required=True)
    parser.add_argument('--vanilla-jar', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--cloth-density', type=int, choices=(16, 32, 64), default=64,
                        help='Cloth texels per world block.')
    parser.add_argument('--base-density', type=int, choices=(16, 32, 64), default=32,
                        help='Base texels per world block; wood and quartz stay at 16.')
    args = parser.parse_args()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    prefix = 'assets/riichi_mahjong/'
    changed = []

    with ZipFile(args.base_pack) as base, ZipFile(args.vanilla_jar) as vanilla:
        def save_json(name, obj):
            file = output / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            changed.append(name)

        def save_image(name, image):
            file = output / name
            file.parent.mkdir(parents=True, exist_ok=True)
            image.save(file)
            changed.append(name)

        def block(name):
            file = f'assets/minecraft/textures/block/{name}.png'
            archive = base if file in base.namelist() else vanilla
            image = Image.open(io.BytesIO(archive.read(file))).convert('RGBA')
            if image.size != (16, 16):
                raise ValueError(f'{name}: expected native 16 x 16 block texture, got {image.size}')
            return image

        materials = {
            'cloth': block('green_concrete_powder'),
            'back': block('lime_concrete'),
            # minecraft:block/smooth_quartz uses quartz_block_bottom, not quartz_block_top.
            'quartz': block('quartz_block_bottom'),
            'wood': block('stripped_mangrove_log'),
            'base': block('black_terracotta'),
        }

        def repeat(image, size):
            result = Image.new('RGBA', size)
            for y in range(0, size[1], image.height):
                for x in range(0, size[0], image.width):
                    result.paste(image, (x, y))
            return result

        model_name = prefix + 'models/block/mahjong_table.json'
        table = json.loads(base.read(model_name))
        original_table = copy.deepcopy(table)
        table['texture_size'] = [64, 64]
        for key in ['cloth', 'quartz', 'wood', 'base']:
            texture = f'block/materials/table_{key}'
            table['textures'][key] = f'riichi_mahjong:{texture}'
            density = args.cloth_density if key == 'cloth' else args.base_density if key == 'base' else 16
            size = 64 * density // 16
            save_image(prefix + 'textures/' + texture + '.png', repeat(materials[key], (size, size)))
        table['textures']['particle'] = table['textures']['cloth']

        # UVs keep the same world-space extent. Larger repeated material sheets
        # increase detail density without interpolating any source pixels.
        # Account for horizontal display scaling; vertical dimensions stay unscaled.
        density_checks = 0
        for index, element in enumerate(table['elements']):
            if index == 6:  # Preserve the original centre wind plate and its markings.
                continue
            a, b = element['from'], element['to']
            x0, z0 = (a[0] + 8) * 1.5, (a[2] + 8) * 1.5
            x1, z1 = (b[0] + 8) * 1.5, (b[2] + 8) * 1.5
            for direction, face in element['faces'].items():
                material = 'base' if index == 0 else (
                    'cloth' if index == 1 and direction == 'up' else
                    'quartz' if index in (2, 3, 4, 5) and direction == 'up' else 'wood')
                face['texture'] = '#' + material
                if direction in ('up', 'down'):
                    uv = [x0, z0, x1, z1]
                    expected = [(b[0] - a[0]) * 1.5, (b[2] - a[2]) * 1.5]
                elif direction in ('north', 'south'):
                    uv = [x0, 16 - b[1], x1, 16 - a[1]]
                    expected = [(b[0] - a[0]) * 1.5, b[1] - a[1]]
                else:
                    uv = [z0, 16 - b[1], z1, 16 - a[1]]
                    expected = [(b[2] - a[2]) * 1.5, b[1] - a[1]]
                face['uv'] = [v / 4 for v in uv]
                measured = [abs(uv[2] - uv[0]), abs(uv[3] - uv[1])]
                assert all(abs(u - v) < 1e-8 for u, v in zip(measured, expected))
                density_checks += 1
            assert element['from'] == original_table['elements'][index]['from']
            assert element['to'] == original_table['elements'][index]['to']
        save_json(model_name, table)

        # Shell UVs use a 32px unwrap. Preserve its original white/green boundary.
        shell_name = prefix + 'textures/block/tile_shell.png'
        shell = Image.open(io.BytesIO(base.read(shell_name))).convert('RGBA')
        assert shell.size == (32, 32)
        new_shell = shell.copy()
        for y in range(32):
            for x in range(32):
                r, g, b, alpha = shell.getpixel((x, y))
                texture = materials['back'] if g > r + 30 and g > b else materials['quartz']
                pixel = texture.getpixel((x % 16, y % 16))
                new_shell.putpixel((x, y), (*pixel[:3], alpha))
        save_image(shell_name, new_shell)

        faces = []
        for name in base.namelist():
            if not (name.startswith(prefix + 'textures/block/tile/') and name.endswith('.png')):
                if name != prefix + 'textures/block/tile_face.png':
                    continue
            original = Image.open(io.BytesIO(base.read(name))).convert('RGBA')
            quartz = materials['quartz'].resize(original.size, Image.Resampling.NEAREST)
            updated = original.copy()
            preserved = 0
            for y in range(original.height):
                for x in range(original.width):
                    pixel = original.getpixel((x, y))
                    rgb = pixel[:3]
                    # Replace the neutral light background, never rescale the lettering.
                    background = min(rgb) >= 180 and max(rgb) - min(rgb) <= 14
                    if name.endswith('/tile_dragon_white.png') or name.endswith('/tile_face.png'):
                        background = True
                    if background:
                        updated.putpixel((x, y), (*quartz.getpixel((x, y))[:3], pixel[3]))
                    else:
                        assert updated.getpixel((x, y)) == pixel
                        preserved += 1
            assert updated.size == original.size
            save_image(name, updated)
            faces.append({'file': name, 'size': list(updated.size), 'unchangedLetterPixels': preserved})

        report = {
            'basePack': str(Path(args.base_pack).resolve()),
            'materials': {'cloth': 'green_concrete_powder', 'back': 'lime_concrete',
                          'faceAndRim': 'smooth_quartz (quartz_block_bottom)',
                          'side': 'stripped_mangrove_log', 'base': 'black_terracotta'},
            'changedFiles': changed, 'tileFaces': faces,
            'tableDensityFacesChecked': density_checks,
            'tableTexelsPerWorldBlock': {'cloth': args.cloth_density, 'base': args.base_density,
                                        'otherMaterials': 16},
            'tableGeometryUnchanged': True, 'resampling': 'nearest, background only',
            'clientVisualTest': False,
        }
        (output / 'material-verification.json').write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'changedFiles': len(changed), 'tileFaces': len(faces),
                          'densityChecks': density_checks, 'output': str(output.resolve())}))


if __name__ == '__main__':
    main()
