"""Prepare ignored signing files for the Google Play CI build."""

import base64
import os
from pathlib import Path


required = (
    "UPLOAD_KEYSTORE_BASE64",
    "UPLOAD_STORE_PASSWORD",
    "UPLOAD_KEY_ALIAS",
    "UPLOAD_KEY_PASSWORD",
    "PLAY_SERVICE_ACCOUNT_JSON",
)
missing = [name for name in required if not os.environ.get(name)]
if missing:
    raise SystemExit("Missing GitHub Actions secrets: " + ", ".join(missing))

android = Path(__file__).resolve().parents[1] / "android"
keystore = android / "birdy-upload.jks"
keystore.write_bytes(base64.b64decode(os.environ["UPLOAD_KEYSTORE_BASE64"], validate=True))
keystore.chmod(0o600)


def property_value(name: str) -> str:
    value = os.environ[name]
    if "\n" in value or "\r" in value or "\\" in value:
        raise SystemExit(f"Invalid character in {name}")
    return value


properties = android / "keystore.properties"
properties.write_text(
    "storeFile=birdy-upload.jks\n"
    f"storePassword={property_value('UPLOAD_STORE_PASSWORD')}\n"
    f"keyAlias={property_value('UPLOAD_KEY_ALIAS')}\n"
    f"keyPassword={property_value('UPLOAD_KEY_PASSWORD')}\n",
    encoding="utf-8",
)
properties.chmod(0o600)
