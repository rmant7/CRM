#!/usr/bin/env python3
"""Pick Firebase Test Lab virtual phones for the CRoMan matrix."""

import json
import os
import sys

loaded = json.load(open(sys.argv[1], encoding="utf-8"))
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
if not phones:
    raise SystemExit("Firebase Test Lab returned no virtual phones")

want = [
    (23, ["en", "ru", "iw"]),
    (26, ["en"]),
    (28, ["en"]),
    (30, ["en", "ru", "iw"]),
    (33, ["en"]),
    (35, ["en"]),
    (36, ["en", "ru", "iw"]),
]
chosen = []
for api, locales in want:
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
        name, version = best[1], best[2]
        note = f"nearest to {api}"
    for locale in locales:
        chosen.append((name, version, locale, note, api))

out = os.environ["RUNNER_TEMP"]
device_lines = []
for name, version, locale, note, api in chosen:
    print(f"device {name} version={version} locale={locale} ({note})")
    device_lines.append(f"model={name},version={version},locale={locale},orientation=portrait")
open(os.path.join(out, "ftl-devices.txt"), "w", encoding="utf-8").write("\n".join(device_lines) + "\n")
instrumented = []
for target in (23, 30, 36):
    match = next(item for item in chosen if item[4] == target and item[2] == "en")
    instrumented.append(f"model={match[0]},version={match[1]},locale=en,orientation=portrait")
open(os.path.join(out, "ftl-instrumentation.txt"), "w", encoding="utf-8").write("\n".join(instrumented) + "\n")
