#!/usr/bin/env python3
"""Validação sem dependências externas para o repositório SyncDeck."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def fail(message: str) -> None:
    ERRORS.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    try:
        return path.read_text(encoding="utf-8")
    except Exception as error:
        fail(f"Não foi possível ler {relative}: {error}")
        return ""


def require_files() -> None:
    required = [
        "README.md",
        "VERSION",
        "LICENSE",
        "CHANGELOG.md",
        "CONTRIBUTING.md",
        "CODE_OF_CONDUCT.md",
        "SECURITY.md",
        "SUPPORT.md",
        "ROADMAP.md",
        "REPOSITORY-SETUP.md",
        ".editorconfig",
        ".gitattributes",
        ".gitignore",
        ".github/workflows/build.yml",
        ".github/dependabot.yml",
        "docs/ARCHITECTURE.md",
        "docs/ACTIONS.md",
        "docs/PROTOCOL.md",
        "docs/DEVELOPMENT.md",
        "docs/TESTING.md",
        "docs/RELEASING.md",
        "docs/TROUBLESHOOTING.md",
        "android-app/gradlew",
        "android-app/gradlew.bat",
        "android-app/gradle/wrapper/gradle-wrapper.jar",
        "windows-agent/build-agent.bat",
        "windows-agent/src/DeckServer.cs",
        "windows-agent/src/WindowInspector.cs",
        "tests/protocol-vector.json",
    ]
    for relative in required:
        if not (ROOT / relative).is_file():
            fail(f"Arquivo obrigatório ausente: {relative}")


def validate_structured_files() -> None:
    for path in ROOT.rglob("*.xml"):
        if ".git" in path.parts:
            continue
        try:
            ET.parse(path)
        except Exception as error:
            fail(f"XML inválido em {path.relative_to(ROOT)}: {error}")

    for path in ROOT.rglob("*.json"):
        if ".git" in path.parts:
            continue
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as error:
            fail(f"JSON inválido em {path.relative_to(ROOT)}: {error}")

    for path in list(ROOT.rglob("*.yml")) + list(ROOT.rglob("*.yaml")):
        if ".git" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        if "\t" in text:
            fail(f"YAML contém tabulação: {path.relative_to(ROOT)}")
        if not text.strip():
            fail(f"YAML vazio: {path.relative_to(ROOT)}")


def validate_versions() -> None:
    gradle = read("android-app/app/build.gradle")
    server = read("windows-agent/src/DeckServer.cs")
    manifest = read("windows-agent/app.manifest")

    android_match = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", gradle)
    code_match = re.search(r"versionCode\s+(\d+)", gradle)
    agent_match = re.search(r'version\s*=\s*"([^"]+)"', server)
    assembly_match = re.search(r'assemblyIdentity\s+version="([^"]+)"', manifest)
    if not all([android_match, code_match, agent_match, assembly_match]):
        fail("Não foi possível localizar todas as versões.")
        return

    android_version = android_match.group(1)
    agent_version = agent_match.group(1)
    assembly_version = assembly_match.group(1)
    if android_version != agent_version:
        fail(f"Versões divergentes: Android {android_version}, agente {agent_version}.")
    repository_version = read("VERSION").strip()
    if repository_version != android_version:
        fail(f"VERSION contém {repository_version}, esperado {android_version}.")
    if assembly_version != f"{android_version}.0":
        fail(f"Assembly {assembly_version} não corresponde a {android_version}.0.")
    if int(code_match.group(1)) < 1:
        fail("versionCode Android deve ser positivo.")
    if android_version not in read("README.md") or android_version not in read("CHANGELOG.md"):
        fail("Versão atual ausente no README ou CHANGELOG.")


def validate_android_manifest() -> None:
    path = ROOT / "android-app/app/src/main/AndroidManifest.xml"
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return
    namespace = "{http://schemas.android.com/apk/res/android}"
    permissions = [item.attrib.get(namespace + "name", "") for item in root.findall("uses-permission")]
    expected = [
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.VIBRATE",
    ]
    if permissions != expected:
        fail(f"Permissões Android inesperadas: {permissions}")


def validate_protocol_vector() -> None:
    try:
        vector = json.loads(read("tests/protocol-vector.json"))
        secret = bytes.fromhex(vector["secretHex"])
        body = vector["body"].encode("utf-8")
        body_hash = hashlib.sha256(body).hexdigest()
        canonical = "\n".join(
            [
                vector["method"],
                vector["path"],
                str(vector["timestamp"]),
                vector["nonce"],
                body_hash,
            ]
        ).encode("utf-8")
        signature = base64.urlsafe_b64encode(
            hmac.new(secret, canonical, hashlib.sha256).digest()
        ).decode("ascii").rstrip("=")

        response = vector["responseBody"].encode("utf-8")
        response_hash = hashlib.sha256(response).hexdigest()
        response_canonical = "\n".join(
            [
                "RESPONSE",
                str(vector["responseStatus"]),
                vector["nonce"],
                response_hash,
            ]
        ).encode("utf-8")
        response_signature = base64.urlsafe_b64encode(
            hmac.new(secret, response_canonical, hashlib.sha256).digest()
        ).decode("ascii").rstrip("=")

        if body_hash != vector["bodySha256"]:
            fail("SHA-256 do corpo diverge do vetor.")
        if signature != vector["signatureBase64Url"]:
            fail("HMAC da requisição diverge do vetor.")
        if response_hash != vector["responseBodySha256"]:
            fail("SHA-256 da resposta diverge do vetor.")
        if response_signature != vector["responseSignatureBase64Url"]:
            fail("HMAC da resposta diverge do vetor.")
    except Exception as error:
        fail(f"Falha ao validar vetor criptográfico: {error}")


def validate_source_invariants() -> None:
    main = read("android-app/app/src/main/java/com/syncdeck/app/MainActivity.java")
    api = read("android-app/app/src/main/java/com/syncdeck/app/ApiClient.java")
    server = read("windows-agent/src/DeckServer.cs")
    executor = read("windows-agent/src/ActionExecutor.cs")
    chrome = read("windows-agent/src/ChromeProfileResolver.cs")

    if "buildInterface();" not in main or "configureWindow();" not in main:
        fail("Inicialização Android incompleta.")
    elif main.index("buildInterface();") > main.index("configureWindow();"):
        fail("configureWindow não pode ocorrer antes de buildInterface.")

    auth_index = server.find("if (!Authenticate")
    state_index = server.find('/api/actions/state')
    if auth_index < 0 or state_index < auth_index:
        fail("Rota de estado precisa estar depois da autenticação.")
    if "close-all" not in server or "close-all" not in executor or "close-all" not in main:
        fail("Operação close-all não está implementada nas duas partes.")
    if "getActionStates" not in api or "startStatePolling" not in main:
        fail("Atualização automática de estado está incompleta.")
    if "--profile-directory" not in chrome or "last_active_profiles" not in chrome:
        fail("Resolução do perfil Chrome está incompleta.")
    if "SignResponse" not in server or "X-SyncDeck-Response-Signature" not in server:
        fail("Assinatura de resposta ausente no agente.")


def validate_wrapper() -> None:
    path = ROOT / "android-app/gradle/wrapper/gradle-wrapper.jar"
    if not path.is_file():
        return
    expected = "76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3"
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        fail("Checksum do gradle-wrapper.jar não corresponde ao wrapper aprovado.")


def validate_markdown_links() -> None:
    pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
    for path in ROOT.rglob("*.md"):
        if ".git" in path.parts:
            continue
        content = path.read_text(encoding="utf-8")
        for target in pattern.findall(content):
            cleaned = target.strip().split("#", 1)[0]
            if not cleaned or cleaned.startswith(("http://", "https://", "mailto:")):
                continue
            destination = (path.parent / cleaned).resolve()
            try:
                destination.relative_to(ROOT.resolve())
            except ValueError:
                fail(f"Link sai do repositório em {path.relative_to(ROOT)}: {target}")
                continue
            if not destination.exists():
                fail(f"Link quebrado em {path.relative_to(ROOT)}: {target}")


def validate_forbidden_files() -> None:
    forbidden_names = {"local.properties", "clients.json", "settings.json", "actions.json"}
    forbidden_suffixes = {
        ".apk",
        ".aab",
        ".exe",
        ".dll",
        ".pdb",
        ".jks",
        ".keystore",
        ".pfx",
        ".p12",
        ".pem",
        ".key",
    }
    ignored_directories = {".git", "build", ".gradle", "dist", "__pycache__"}
    private_markers = tuple(
        "-----BEGIN " + prefix + "PRIVATE KEY-----"
        for prefix in ("RSA ", "OPENSSH ", "EC ", "")
    )

    for path in ROOT.rglob("*"):
        relative = path.relative_to(ROOT)
        if any(part in ignored_directories for part in relative.parts):
            continue
        if not path.is_file():
            continue
        lower_name = path.name.lower()
        if lower_name in forbidden_names or path.suffix.lower() in forbidden_suffixes:
            fail(f"Arquivo não publicável encontrado: {relative}")
            continue
        if lower_name == ".env" or (lower_name.startswith(".env.") and lower_name != ".env.example"):
            fail(f"Arquivo de ambiente não publicável: {relative}")
            continue
        if path.stat().st_size > 2_000_000 or path.suffix.lower() in {".jar", ".png", ".jpg", ".jpeg", ".zip"}:
            continue
        try:
            content = path.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if any(marker in content for marker in private_markers):
            fail(f"Possível chave privada em {relative}")


def main() -> int:
    require_files()
    validate_structured_files()
    validate_versions()
    validate_android_manifest()
    validate_protocol_vector()
    validate_source_invariants()
    validate_wrapper()
    validate_markdown_links()
    validate_forbidden_files()

    if ERRORS:
        print("VALIDAÇÃO FALHOU")
        for error in ERRORS:
            print(f"- {error}")
        return 1

    print("SyncDeck repository validation: OK")
    print("- Estrutura e documentação: OK")
    print("- XML e JSON: OK")
    print("- Versões e permissões: OK")
    print("- Vetores criptográficos: OK")
    print("- Arquivos publicáveis: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
