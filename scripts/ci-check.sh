#!/usr/bin/env bash
set -euo pipefail

required=(
  "settings.gradle"
  "build.gradle"
  "gradlew"
  "gradle/wrapper/gradle-wrapper.jar"
  "gradle/wrapper/gradle-wrapper.properties"
  "app/build.gradle"
  "app/src/main/AndroidManifest.xml"
)

for f in "${required[@]}"; do
  test -f "$f" || { echo "Missing required file: $f"; exit 1; }
done

test -x gradlew || chmod +x gradlew

# Catch source corruption signatures seen in previous broken archives before Gradle runs.
corrupt_tokens=(
  'ByteArra!ll'
  'PendingIntent.getBing'
  'FileOutputStreaEa'
  'whilahr'
  'Extenstrsn'
  'inc MText'
  'packet[2].toIocket'
  'copyOfRange(0,1ingkan'
  'HTML"oavith'
  'Batal""eTool'
  '.prion;'
  '0.ly {'
  'di"⚠'
  'content.TE'
)
for token in "${corrupt_tokens[@]}"; do
  if grep -RInF --include='*.kt' -- "$token" app/src/main/java >/tmp/corrupt-hit 2>/dev/null; then
    cat /tmp/corrupt-hit
    rm -f /tmp/corrupt-hit
    echo "Corrupted Kotlin source signature detected: $token"
    exit 1
  fi
done
rm -f /tmp/corrupt-hit

# Catch accidental duplicate package declarations, which break Kotlin compilation.
bad=0
while IFS= read -r -d '' f; do
  count=$(grep -cE '^package ' "$f" || true)
  if [ "$count" -ne 1 ]; then
    echo "Expected exactly one package declaration: $f (found $count)"
    bad=1
  fi
done < <(find app/src -name '*.kt' -print0)

if [ "$bad" -ne 0 ]; then
  exit 1
fi

# Keep the Android version in one place and verify the expected release metadata.
for wf in .github/workflows/*.yml .github/workflows/*.yaml; do
  [ -f "$wf" ] || continue
  if grep -Fq 'yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses' "$wf" && ! grep -Fq 'set +o pipefail' "$wf"; then
    echo "Unsafe sdkmanager license pipeline in $wf"
    exit 1
  fi
done

version_name=$(grep -oE 'versionName[[:space:]]+"[^"]+"' app/build.gradle | head -1 | sed -E 's/.*"([^"]+)"/\1/')
version_code=$(grep -oE 'versionCode[[:space:]]+[0-9]+' app/build.gradle | head -1 | awk '{print $2}')

test -n "$version_name" || { echo "Could not read versionName"; exit 1; }
test -n "$version_code" || { echo "Could not read versionCode"; exit 1; }

echo "GITLS versionName: $version_name"
echo "GITLS versionCode: $version_code"

# Structural validation that does not require the Android SDK.
python3 - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path('.')
src = root / 'app/src/main/java/com/example/aidetest'
res = root / 'app/src/main/res'

# Parse Android XML.
for path in [root / 'app/src/main/AndroidManifest.xml', *res.rglob('*.xml')]:
    ET.parse(path)

# R.id references must have an XML declaration in the source tree.
refs = set()
for path in src.rglob('*.kt'):
    refs.update(re.findall(r'R\.id\.([A-Za-z0-9_]+)', path.read_text(errors='ignore')))
ids = set()
for path in res.rglob('*.xml'):
    ids.update(re.findall(r'@\+?id/([A-Za-z0-9_]+)', path.read_text(errors='ignore')))
missing = sorted(refs - ids)
if missing:
    raise SystemExit('Missing R.id declarations: ' + ', '.join(missing))

# Manifest app components must exist as Kotlin classes.
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
android_ns = '{http://schemas.android.com/apk/res/android}'
classes = set()
for path in src.rglob('*.kt'):
    classes.update(re.findall(r'\b(?:class|object)\s+([A-Za-z_][A-Za-z0-9_]*)', path.read_text(errors='ignore')))
for node in manifest.iter():
    if node.tag.split('}')[-1] not in {'activity','service','receiver','provider'}:
        continue
    name = node.attrib.get(android_ns + 'name')
    if not name or name.startswith('android.') or name.startswith('androidx.'):
        continue
    simple = name.rsplit('.', 1)[-1].lstrip('.')
    if simple not in classes:
        raise SystemExit(f'Manifest component missing from Kotlin source: {name}')

print('Source/resource integrity check: OK')
PY

# No generated documentation/log artifacts in the distributable tree.
if find . -type f \( -name '*.md' -o -name '*.txt' -o -name '*.log' \) \
    ! -path './.git/*' ! -path './.gradle/*' | grep -q .; then
  echo "Unexpected documentation/text/log artifact found in project tree."
  find . -type f \( -name '*.md' -o -name '*.txt' -o -name '*.log' \) \
    ! -path './.git/*' ! -path './.gradle/*'
  exit 1
fi

grep -q 'compileSdkVersion 36' app/build.gradle || { echo "compileSdkVersion 36 missing"; exit 1; }
grep -q 'targetSdkVersion 36' app/build.gradle || { echo "targetSdkVersion 36 missing"; exit 1; }

echo "Project structure and source integrity check: OK"
