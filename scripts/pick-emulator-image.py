#!/usr/bin/env python3
"""Choose an x86_64 google_apis system image, or the nearest API if the requested one is missing."""

import sys


def parse_packages(text):
    found = []
    for line in text.splitlines():
        token = line.strip().split()[0] if line.strip() else ""
        if not token.startswith("system-images;"):
            continue
        parts = token.split(";")
        if len(parts) < 4 or not parts[1].startswith("android-"):
            continue
        try:
            api = int(parts[1].split("-", 1)[1])
        except ValueError:
            continue
        found.append((api, parts[2], parts[3]))
    return found


def pick(wanted, packages, preferred="google_apis"):
    def of(target):
        return [item for item in packages if item[1] == target and item[2] == "x86_64"]

    preferred_images = of(preferred)
    if any(api == wanted for api, _target, _abi in preferred_images):
        note = "" if preferred == "google_apis" else f"API {wanted} uses the lighter {preferred} x86_64 image"
        return wanted, preferred, note
    google = of("google_apis")
    if any(api == wanted for api, _target, _abi in google):
        return wanted, "google_apis", f"API {wanted} {preferred} x86_64 is not offered; using google_apis"
    pool = preferred_images or google or of("default")
    if not pool:
        raise SystemExit("sdkmanager lists no x86_64 system images")
    nearest = min(pool, key=lambda item: (abs(item[0] - wanted), item[0]))
    note = f"API {wanted} x86_64 {preferred} is not offered; using API {nearest[0]} {nearest[1]}"
    return nearest[0], nearest[1], note


def self_check():
    sample = """
    system-images;android-23;google_apis;x86_64
    system-images;android-30;google_apis;x86_64
    system-images;android-35;google_apis;x86_64
    system-images;android-36;google_apis_playstore;x86_64
    system-images;android-23;google_apis;x86
    """
    packages = parse_packages(sample)
    api, target, note = pick(30, packages)
    assert (api, target, note) == (30, "google_apis", "")
    api, target, note = pick(36, packages)
    assert api == 35 and target == "google_apis" and "using API 35" in note
    api, target, note = pick(30, packages, "default")
    assert (api, target) == (30, "google_apis") and "not offered" in note
    api, _target, note = pick(23, packages)
    assert api == 23 and note == ""
    print("pick-emulator-image self-check ok")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--self-check":
        self_check()
        return
    wanted = int(sys.argv[1])
    text = open(sys.argv[2], encoding="utf-8", errors="replace").read()
    preferred = sys.argv[3] if len(sys.argv) > 3 else "google_apis"
    api, target, note = pick(wanted, parse_packages(text), preferred)
    print(f"{api}\t{target}\t{note}")


if __name__ == "__main__":
    main()
