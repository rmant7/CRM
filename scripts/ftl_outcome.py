#!/usr/bin/env python3
"""Classify a Firebase Test Lab gcloud run.

Exit 10 means tests ran and failed: the release must stop.
Auth, quota, billing, and infrastructure errors are reported as "not run"
so the APK can still be published.
"""

import re
import sys

INFRA_CODES = {1, 2, 15, 18, 19, 20}

INFRA_MARKERS = (
    "permission_denied",
    "storage.objects.create",
    "storage.objects.get",
    "could not copy",
    "responseerror 403",
    "http 403",
    " 403",
    "401",
    "unauthenticated",
    "quota",
    "resource_exhausted",
    "billing",
    "has not been used",
    "not been enabled",
    "infrastructure",
    "exhausted attempts",
    "serviceusage.services.use",
)


def reason(text):
    errors = []
    for line in text.splitlines():
        stripped = line.strip()
        if "ERROR:" in stripped or any(marker in stripped.lower() for marker in ("403", "quota", "permission")):
            errors.append(stripped)
    source = errors[-1] if errors else ""
    if not source:
        for line in reversed(text.splitlines()):
            if line.strip():
                source = line.strip()
                break
    source = re.sub(r"^ERROR:\s*(\([^)]*\)\s*)?", "", source).strip()
    source = re.sub(r"\s+", " ", source)
    if not source:
        source = "gcloud exited without an error line"
    return source[:220]


def classify(code, text):
    if code == 0:
        return "pass", "passed"
    lowered = text.lower()
    infra = code in INFRA_CODES or any(marker in lowered for marker in INFRA_MARKERS)
    if code == 10 and not infra:
        return "fail", "tests ran and failed"
    if code == 10 and infra and "storage.objects" not in lowered and "could not copy" not in lowered:
        # A finished matrix can mention an infra retry and still exit 10 because a test failed.
        if "test" in lowered and "fail" in lowered:
            return "fail", "tests ran and failed"
    if infra or code != 10:
        return "skip", reason(text)
    return "fail", "tests ran and failed"


def pick_shots(urls, limit=4):
    chosen = []
    seen = set()
    for url in urls:
        if not url.lower().endswith(".png"):
            continue
        folder = url.rsplit("/", 1)[0]
        if folder in seen:
            continue
        seen.add(folder)
        chosen.append(url)
        if len(chosen) >= limit:
            break
    return chosen


def self_check():
    denied = (
        "ERROR: (gcloud.firebase.test.android.run) Could not copy [app.apk] to [gs://test-lab-x/]. "
        "ResponseError 403: Permission 'storage.objects.create' denied on resource."
    )
    kind, detail = classify(1, denied)
    assert kind == "skip", (kind, detail)
    assert "storage.objects.create" in detail
    kind, _detail = classify(10, "Instrumentation test failed: expected true")
    assert kind == "fail"
    kind, _detail = classify(0, "All tests passed")
    assert kind == "pass"
    kind, detail = classify(20, "ERROR: Firebase Test Lab infrastructure failure: Exhausted attempts")
    assert kind == "skip", (kind, detail)
    kind, detail = classify(1, "ERROR: Quota exceeded for quota metric.")
    assert kind == "skip" and "Quota" in detail
    shots = pick_shots(
        [
            "gs://b/run/robo/phone-26-en/artifacts/a.png",
            "gs://b/run/robo/phone-26-en/artifacts/b.png",
            "gs://b/run/robo/phone-26-ru/artifacts/a.png",
            "gs://b/run/robo/phone-36-iw/video.mp4",
            "gs://b/run/robo/phone-36-iw/artifacts/a.png",
        ]
    )
    assert shots == [
        "gs://b/run/robo/phone-26-en/artifacts/a.png",
        "gs://b/run/robo/phone-26-ru/artifacts/a.png",
        "gs://b/run/robo/phone-36-iw/artifacts/a.png",
    ], shots
    print("ftl-outcome self-check ok")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--self-check":
        self_check()
        return
    if len(sys.argv) > 1 and sys.argv[1] == "pick-shots":
        text = open(sys.argv[2], encoding="utf-8").read().splitlines()
        for url in pick_shots(text):
            print(url)
        return
    code = int(sys.argv[1])
    text = open(sys.argv[2], encoding="utf-8").read()
    kind, detail = classify(code, text)
    print(f"{kind}\t{detail}")


if __name__ == "__main__":
    main()
