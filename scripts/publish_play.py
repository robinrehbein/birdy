"""Upload one AAB and publish it to the existing internal and closed test tracks."""

import json
import os
import sys
from pathlib import Path
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account


PACKAGE = "de.robinrehbein.birdy"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits"


def request(session, method, url, **kwargs):
    response = session.request(method, url, timeout=120, **kwargs)
    if not response.ok:
        raise SystemExit(f"Google Play API {method} failed ({response.status_code}): {response.text}")
    return response.json() if response.content else {}


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: publish_play.py path/to/app-release.aab")
    bundle = Path(sys.argv[1])
    if not bundle.is_file():
        raise SystemExit(f"Bundle not found: {bundle}")

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]), scopes=[SCOPE]
    )
    session = AuthorizedSession(credentials)
    edit_id = request(session, "POST", BASE, json={})["id"]
    url = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}/edits/{edit_id}/bundles?uploadType=media"
    with bundle.open("rb") as source:
        uploaded = request(session, "POST", url, data=source, headers={"Content-Type": "application/octet-stream"})
    version_code = str(uploaded["versionCode"])

    tracks_url = f"{BASE}/{edit_id}/tracks"
    available = {item["track"] for item in request(session, "GET", tracks_url).get("tracks", [])}
    closed_track = os.environ.get("PLAY_CLOSED_TRACK", "alpha")
    if closed_track in {"qa", "beta", "production"} or ":" in closed_track:
        raise SystemExit("PLAY_CLOSED_TRACK must name a closed phone/tablet test track")
    targets = ("qa", closed_track)
    for track in targets:
        if track not in available:
            raise SystemExit(f"Track {track!r} not found. Available tracks: {sorted(available)}")

    release = {
        "name": f"Birdy {version_code}",
        "versionCodes": [version_code],
        "status": "completed",
        "releaseNotes": [
            {"language": "de-DE", "text": "Neues Update mit Verbesserungen."},
            {"language": "en-US", "text": "New update with improvements."},
        ],
    }
    for track in targets:
        request(session, "PUT", f"{tracks_url}/{quote(track, safe='')}", json={"track": track, "releases": [release]})
        print(f"Prepared versionCode {version_code} for {track}")

    request(session, "POST", f"{BASE}/{edit_id}:commit", json={})
    print(f"Committed {PACKAGE} versionCode {version_code} to {', '.join(targets)}")


if __name__ == "__main__":
    main()
