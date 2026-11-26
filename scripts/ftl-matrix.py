#!/usr/bin/env python3
"""Pick Firebase Test Lab virtual phones for the CRoMan matrix.

Spark's free quota is 10 virtual-device runs per day. This list is 9 Robo
runs: API 26 (en/ru/iw), 30 (en), 33 (en), 35 (en), 36 (en/ru/iw). API 23 is
not offered on current virtual devices; 26 is the oldest stand-in, and
Robolectric still runs the API 23 checks.
"""

import json
import os
import sys
import tempfile

WANT = [
    (26, ["en", "ru", "iw"]),
    (30, ["en"]),
    (33, ["en"]),
    (35, ["en"]),
    (36, ["en", "ru", "iw"]),
]
SPARK_LIMIT = 9


def phones_from(loaded):
    models = loaded if isinstance(loaded, list) else loaded.get("models", loaded.get("androidModels", []))
    phones = []
    for model in models:
        form = str(model.get("form", "")).upper()
        factor = str(model.get("formFactor", "")).upper()
        if form != "VIRTUAL":
            continue
        if factor and factor != "PHONE":
            continue
        versions = []
        for raw in model.get("supportedVersionIds", []):
            try:
                versions.append(int(raw))
            except ValueError:
                continue
        name = model.get("id") or model.get("codename")
        if name and versions:
            phones.append((name, versions))
    return phones


def choose(phones):
    chosen = []
    seen = set()
    for api, locales in WANT:
        exact = [(name, api) for name, versions in phones if api in versions]
        if exact:
            name, version = exact[0]
            note = "exact"
        else:
            best = None
            for name, versions in phones:
                for version in versions:
                    distance = abs(version - api)
                    if best is None or distance < best[0]:
                        best = (distance, name, version)
            if best is None:
                raise SystemExit("Firebase Test Lab returned no virtual phones")
            name, version = best[1], best[2]
            note = f"nearest to {api}"
        for locale in locales:
            key = (name, version, locale)
            if key in seen:
                continue
            seen.add(key)
            chosen.append((name, version, locale, note, api))
    if len(chosen) > SPARK_LIMIT:
        raise SystemExit(f"matrix has {len(chosen)} devices, over the Spark limit of {SPARK_LIMIT}")
    return chosen


def write_choice(chosen, out_dir):
    lines = []
    for name, version, locale, note, _api in chosen:
        print(f"device {name} version={version} locale={locale} ({note})")
        lines.append(f"model={name},version={version},locale={locale},orientation=portrait")
    path = os.path.join(out_dir, "ftl-devices.txt")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + ("\n" if lines else ""))
    return path


def self_check():
    loaded = {
        "models": [
            {
                "id": "MediumPhone.arm",
                "form": "VIRTUAL",
                "formFactor": "PHONE",
                "supportedVersionIds": ["26", "28", "30", "33", "35", "36"],
            },
            {
                "id": "Nexus5",
                "form": "VIRTUAL",
                "formFactor": "PHONE",
                "supportedVersionIds": ["23"],
            },
        ]
    }
    chosen = choose(phones_from(loaded))
    assert len(chosen) == 9, chosen
    versions = sorted({item[1] for item in chosen})
    assert versions == [26, 30, 33, 35, 36], versions
    locales_26 = sorted(item[2] for item in chosen if item[1] == 26)
    assert locales_26 == ["en", "iw", "ru"], locales_26
    assert all(item[0] == "MediumPhone.arm" for item in chosen)
    assert all(item[1] != 23 for item in chosen)
    print("ftl-matrix self-check ok")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--self-check":
        self_check()
        return
    loaded = json.load(open(sys.argv[1], encoding="utf-8"))
    phones = phones_from(loaded)
    if not phones:
        raise SystemExit("Firebase Test Lab returned no virtual phones")
    out = os.environ.get("RUNNER_TEMP") or tempfile.gettempdir()
    write_choice(choose(phones), out)


if __name__ == "__main__":
    main()
