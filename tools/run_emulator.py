#!/usr/bin/env python3
"""Start the development emulator with the current macOS proxy, without saving its address."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


def system_proxy():
    if sys.platform != "darwin":
        return None
    config = subprocess.check_output(["/usr/sbin/scutil", "--proxy"], text=True)
    values = dict(re.findall(r"^\s*(\w+)\s*:\s*([^\n]+)", config, re.MULTILINE))
    for prefix in ("HTTPS", "HTTP"):
        if values.get(prefix + "Enable", "").strip() != "1":
            continue
        host = values.get(prefix + "Proxy", "").strip()
        port = values.get(prefix + "Port", "").strip()
        if host and port.isdigit() and 0 < int(port) < 65536:
            if ":" in host and not host.startswith("["):
                host = "[" + host + "]"
            return "http://" + host + ":" + port
    if values.get("ProxyAutoConfigEnable", "").strip() == "1":
        raise SystemExit("El proxy usa PAC; configura un proxy explícito para el emulador.")
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--avd", default="Pixel_9_Pro")
    parser.add_argument("--direct", action="store_true", help="Omitir el proxy de macOS")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or Path.home() / "Library/Android/sdk")
    executable = sdk / "emulator/emulator"
    if not executable.is_file():
        raise SystemExit("No se encuentra el emulador. Configura ANDROID_SDK_ROOT.")
    command = [str(executable), "-avd", args.avd, "-no-snapshot-load", "-no-snapshot-save"]
    proxy = None if args.direct else system_proxy()
    if proxy:
        command += ["-http-proxy", proxy]
    print("Iniciando emulador " + ("con el proxy actual de macOS." if proxy else "sin proxy explícito."), flush=True)
    os.execv(str(executable), command)


if __name__ == "__main__":
    main()
