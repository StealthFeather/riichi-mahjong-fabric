"""Install a pinned, offline-at-playtime Mortal worker on Linux x86_64."""
import argparse
import hashlib
import platform
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = "https://raw.githubusercontent.com/lietxia/my_mortal/f2dc4603d3fed49727be0ba053d7ffa93de67891"
ASSETS = {
    "libriichi.so": (
        "sample/bot_20240110_mortal/libriichi/libriichi-3.12-x86_64-unknown-linux-gnu.so",
        "3aeb09112198f6babe9bba4dd392ea009669ee7ba5d33cd3cd4242c737bb004b",
    ),
    "mortal.onnx": (
        "step4/public/models/mortal_best.onnx",
        "e96071b343d02c8870c79b67dd6882e08c2ed8ffbc55d7a4e8f1ace2ea4c7764",
    ),
}


def checksum(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def install_asset(dest, url_path, digest):
    if dest.is_file() and checksum(dest) == digest:
        return
    with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as staging:
        temporary = Path(staging.name)
    try:
        with urllib.request.urlopen(f"{SOURCE}/{url_path}", timeout=120) as stream, temporary.open("wb") as out:
            shutil.copyfileobj(stream, out)
        if checksum(temporary) != digest:
            raise ValueError(f"wrong SHA-256 for {dest.name}")
        temporary.replace(dest)
    finally:
        temporary.unlink(missing_ok=True)


def publish(source, dest, mode):
    if dest.is_file() and source.read_bytes() == dest.read_bytes() and dest.stat().st_mode & 0o777 == mode:
        return
    with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as stream:
        stream.write(source.read_bytes())
        temporary = Path(stream.name)
    try:
        temporary.chmod(mode)
        temporary.replace(dest)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path, nargs="?", default=Path("config/riichi-mahjong-ai/mortal"))
    dest = parser.parse_args().destination.resolve()
    if (platform.system(), platform.machine(), sys.version_info[:2]) != ("Linux", "x86_64", (3, 12)):
        parser.error("this pinned binary distribution requires Linux x86_64 and Python 3.12")
    dest.mkdir(parents=True, exist_ok=True)
    for name, (url_path, digest) in ASSETS.items():
        install_asset(dest / name, url_path, digest)
    venv = dest / "venv"
    subprocess.run([sys.executable, "-m", "venv", str(venv)], check=True)
    subprocess.run([str(venv / "bin/python"), "-m", "pip", "install", "--disable-pip-version-check",
                    "--require-hashes", "--only-binary=:all:", "-r", str(HERE / "requirements.txt")], check=True)
    publish(HERE / "worker.py", dest / "worker.py", 0o644)
    publish(HERE / "worker", dest / "worker", 0o755)
    subprocess.run([sys.executable, str(HERE / "smoke_test.py"), str(dest / "worker")], check=True)
    print(f"Mortal worker ready at {dest / 'worker'}")


if __name__ == "__main__":
    main()
