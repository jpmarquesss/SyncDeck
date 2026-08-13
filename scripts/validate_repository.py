#!/usr/bin/env python3
"""Validação sem dependências externas para o repositório SyncDeck."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import plistlib
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


def decode_base64_url(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def encode_base64_url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def require_files() -> None:
    required = [
        "README.md", "VERSION", "LICENSE", "CHANGELOG.md",
        "ATUALIZAR-PARA-1.0.0.txt", "ATUALIZAR-PARA-1.0.1.txt",
        "PRIVACY-POLICY.md", "INSTALAR-NO-IPHONE.txt",
        "CONTRIBUTING.md", "CODE_OF_CONDUCT.md", "SECURITY.md", "SUPPORT.md",
        "ROADMAP.md", "REPOSITORY-SETUP.md", ".editorconfig", ".gitattributes",
        ".gitignore", ".github/workflows/build.yml", ".github/dependabot.yml",
        "docs/ARCHITECTURE.md", "docs/ACTIONS.md", "docs/PROTOCOL.md",
        "docs/DEVELOPMENT.md", "docs/TESTING.md", "docs/RELEASING.md",
        "docs/TROUBLESHOOTING.md", "docs/WAKE-ON-LAN.md", "docs/PLAY-STORE.md",
        "docs/DATA-SAFETY.md", "docs/STORE-LISTING.md", "store-assets/play-icon-512.png",
        "android-app/Gerar-APK.bat", "android-app/Gerar-AAB.bat",
        "android-app/keystore.properties.example", "android-app/gradlew",
        "android-app/gradlew.bat", "android-app/gradle/wrapper/gradle-wrapper.jar",
        "android-app/app/src/main/AndroidManifest.xml",
        "android-app/app/src/main/kotlin/com/syncdeck/app/MainActivity.kt",
        "android-app/app/src/main/kotlin/com/syncdeck/app/ActionWizard.kt",
        "android-app/app/src/main/kotlin/com/syncdeck/app/ApiClient.kt",
        "android-app/app/src/main/kotlin/com/syncdeck/app/DeckController.kt",
        "android-app/app/src/main/kotlin/com/syncdeck/app/Models.kt",
        "android-app/app/src/main/kotlin/com/syncdeck/app/Security.kt",
        "ios-app/SyncDeck.xcodeproj/project.pbxproj", "ios-app/README.md",
        "ios-app/SyncDeck.xcodeproj/xcshareddata/xcschemes/SyncDeck.xcscheme",
        "ios-app/SyncDeck/Info.plist", "ios-app/SyncDeck/SyncDeckApp.swift",
        "ios-app/SyncDeck/Models.swift", "ios-app/SyncDeck/Security.swift",
        "ios-app/SyncDeck/APIClient.swift", "ios-app/SyncDeck/DeckViewModel.swift",
        "ios-app/SyncDeck/ContentView.swift", "ios-app/SyncDeck/ConnectionView.swift",
        "ios-app/SyncDeck/ActionEditorView.swift",
        "ios-app/SyncDeck/Assets.xcassets/AppIcon.appiconset/Contents.json",
        "ios-app/SyncDeck/Assets.xcassets/AppIcon.appiconset/icon-1024.png",
        "windows-agent/build-agent.bat", "windows-agent/Instalar-no-Windows.bat",
        "windows-agent/Configurar-Firewall.bat", "windows-agent/src/AssemblyInfo.cs",
        "windows-agent/src/DeckServer.cs", "windows-agent/src/DesktopSecurity.cs",
        "windows-agent/src/AppCatalog.cs", "windows-agent/src/CryptoPairing.cs",
        "windows-agent/src/NetworkInfo.cs", "windows-agent/src/WindowInspector.cs", "tests/protocol-vector.json",
        "tests/ProtocolVectorTest.java", "tests/ProtocolVectorTest.swift",
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
        content = path.read_text(encoding="utf-8")
        if "\t" in content:
            fail(f"YAML contém tabulação: {path.relative_to(ROOT)}")
        if not content.strip():
            fail(f"YAML vazio: {path.relative_to(ROOT)}")


def validate_versions() -> None:
    gradle = read("android-app/app/build.gradle")
    server = read("windows-agent/src/DeckServer.cs")
    context = read("windows-agent/src/AgentContext.cs")
    manifest = read("windows-agent/app.manifest")
    assembly = read("windows-agent/src/AssemblyInfo.cs")
    xcode = read("ios-app/SyncDeck.xcodeproj/project.pbxproj")
    android_match = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", gradle)
    code_match = re.search(r"versionCode\s+(\d+)", gradle)
    agent_match = re.search(r'version\s*=\s*"([^"]+)"', server)
    manifest_match = re.search(r'assemblyIdentity\s+version="([^"]+)"', manifest)
    file_version_match = re.search(r'AssemblyFileVersion\("([^"]+)"\)', assembly)
    ios_versions = set(re.findall(r"MARKETING_VERSION\s*=\s*([^;]+);", xcode))
    if not all([android_match, code_match, agent_match, manifest_match, file_version_match]) or len(ios_versions) != 1:
        fail("Não foi possível localizar todas as versões.")
        return
    android_version = android_match.group(1)
    agent_version = agent_match.group(1)
    repository_version = read("VERSION").strip()
    expected_assembly = f"{android_version}.0"
    if android_version != agent_version or repository_version != android_version:
        fail(f"Versões públicas divergentes: Android {android_version}, agente {agent_version}, repositório {repository_version}.")
    if manifest_match.group(1) != expected_assembly or file_version_match.group(1) != expected_assembly:
        fail(f"Versões do executável devem ser {expected_assembly}.")
    if f"versão {android_version}" not in context:
        fail("A janela de status do agente exibe uma versão diferente.")
    if int(code_match.group(1)) < 10:
        fail("versionCode Android deve ser pelo menos 10 na linha pública 1.0.")
    if android_version not in read("README.md") or android_version not in read("CHANGELOG.md"):
        fail("Versão pública atual ausente no README ou CHANGELOG.")
    ios_version = next(iter(ios_versions)).strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", ios_version):
        fail(f"Versão iOS inválida: {ios_version}.")


def validate_android() -> None:
    manifest_path = ROOT / "android-app/app/src/main/AndroidManifest.xml"
    try:
        root = ET.parse(manifest_path).getroot()
    except Exception:
        return
    namespace = "{http://schemas.android.com/apk/res/android}"
    permissions = [item.attrib.get(namespace + "name", "") for item in root.findall("uses-permission")]
    if permissions != ["android.permission.INTERNET"]:
        fail(f"Permissões Android inesperadas: {permissions}")
    application = root.find("application")
    if application is None:
        fail("Elemento application ausente no manifesto Android.")
    else:
        if application.attrib.get(namespace + "allowBackup") != "false":
            fail("Backup Android precisa permanecer desativado para proteger o pareamento.")
        if application.attrib.get(namespace + "fullBackupContent") != "false":
            fail("fullBackupContent precisa permanecer desativado.")
        if application.attrib.get(namespace + "networkSecurityConfig") != "@xml/network_security_config":
            fail("Configuração de segurança de rede Android ausente.")
    app_gradle = read("android-app/app/build.gradle")
    root_gradle = read("android-app/build.gradle")
    for value in [
        "compileSdk 36", "targetSdk 36", "minSdk 26",
        "id 'org.jetbrains.kotlin.plugin.compose'", "compose true",
        "minifyEnabled true", "shrinkResources true", "keystore.properties",
    ]:
        if value not in app_gradle and value not in root_gradle:
            fail(f"Configuração Android ausente: {value}")
    if "version '9.0.1'" not in root_gradle or "version '2.2.10'" not in root_gradle:
        fail("Versões aprovadas do AGP/Kotlin não foram encontradas.")
    icon = ROOT / "store-assets/play-icon-512.png"
    try:
        data = icon.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data) < 24:
            raise ValueError("assinatura PNG inválida")
        width = int.from_bytes(data[16:20], "big")
        height = int.from_bytes(data[20:24], "big")
        if (width, height) != (512, 512):
            fail(f"Ícone da Play Store mede {width}x{height}; esperado 512x512.")
    except Exception as error:
        fail(f"Ícone da Play Store inválido: {error}")


def validate_ios_project() -> None:
    plist_path = ROOT / "ios-app/SyncDeck/Info.plist"
    try:
        with plist_path.open("rb") as handle:
            info = plistlib.load(handle)
    except Exception as error:
        fail(f"Info.plist do iOS inválido: {error}")
        return
    description = info.get("NSLocalNetworkUsageDescription", "")
    if not isinstance(description, str) or "rede local" not in description.lower():
        fail("Descrição da permissão Rede Local do iOS ausente.")
    if info.get("NSAppTransportSecurity", {}).get("NSAllowsLocalNetworking") is not True:
        fail("iOS precisa permitir somente o transporte HTTP local do agente.")
    if info.get("UIRequiredDeviceCapabilities") != ["arm64"]:
        fail("Capacidade iOS inesperada; o projeto deve exigir somente arm64.")
    orientations = set(info.get("UISupportedInterfaceOrientations", []))
    expected_orientations = {
        "UIInterfaceOrientationPortrait", "UIInterfaceOrientationLandscapeLeft",
        "UIInterfaceOrientationLandscapeRight",
    }
    if orientations != expected_orientations:
        fail(f"Orientações iOS inesperadas: {sorted(orientations)}")
    project = read("ios-app/SyncDeck.xcodeproj/project.pbxproj")
    for value in [
        "IPHONEOS_DEPLOYMENT_TARGET = 15.0;", "TARGETED_DEVICE_FAMILY = 1;",
        "SWIFT_VERSION = 5.0;", "PRODUCT_BUNDLE_IDENTIFIER = com.eudollyn.syncdeck;",
    ]:
        if value not in project:
            fail(f"Configuração iOS ausente: {value}")
    icon_manifest_path = ROOT / "ios-app/SyncDeck/Assets.xcassets/AppIcon.appiconset/Contents.json"
    try:
        icon_manifest = json.loads(icon_manifest_path.read_text(encoding="utf-8"))
        filenames = {item.get("filename") for item in icon_manifest.get("images", []) if item.get("filename")}
        required_icons = {
            "icon-40.png", "icon-58.png", "icon-60.png", "icon-80.png",
            "icon-87.png", "icon-120.png", "icon-180.png", "icon-1024.png",
        }
        if not required_icons.issubset(filenames):
            fail("Catálogo AppIcon do iOS não contém todos os tamanhos exigidos.")
        for filename in filenames:
            path = icon_manifest_path.parent / filename
            if not path.is_file() or path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
                fail(f"Ícone iOS ausente ou inválido: {filename}")
    except Exception as error:
        fail(f"Falha ao validar AppIcon do iOS: {error}")


def validate_protocol_vector() -> None:
    try:
        vector = json.loads(read("tests/protocol-vector.json"))
        secret = bytes.fromhex(vector["secretHex"])
        body = vector["body"].encode("utf-8")
        response = vector["responseBody"].encode("utf-8")

        def sign_request(wire_body: bytes) -> tuple[str, str]:
            body_hash = hashlib.sha256(wire_body).hexdigest()
            canonical = "\n".join([
                vector["method"], vector["path"], str(vector["timestamp"]),
                vector["nonce"], body_hash,
            ]).encode("utf-8")
            return body_hash, encode_base64_url(hmac.new(secret, canonical, hashlib.sha256).digest())

        def sign_response(wire_body: bytes) -> tuple[str, str]:
            body_hash = hashlib.sha256(wire_body).hexdigest()
            canonical = "\n".join([
                "RESPONSE", str(vector["responseStatus"]), vector["nonce"], body_hash,
            ]).encode("utf-8")
            return body_hash, encode_base64_url(hmac.new(secret, canonical, hashlib.sha256).digest())

        body_hash, signature = sign_request(body)
        response_hash, response_signature = sign_response(response)
        if body_hash != vector["bodySha256"] or signature != vector["signatureBase64Url"]:
            fail("Vetor HMAC da requisição legada diverge.")
        if response_hash != vector["responseBodySha256"] or response_signature != vector["responseSignatureBase64Url"]:
            fail("Vetor HMAC da resposta legada diverge.")
        request_wire = decode_base64_url(vector["encryptedBodyBase64Url"])
        response_wire = decode_base64_url(vector["encryptedResponseBase64Url"])
        for label, wire, iv_hex in [
            ("requisição", request_wire, vector["requestIvHex"]),
            ("resposta", response_wire, vector["responseIvHex"]),
        ]:
            if len(wire) < 32 or len(wire) % 16 != 0:
                fail(f"Payload cifrado de {label} possui tamanho inválido.")
            if wire[:16].hex() != iv_hex:
                fail(f"IV cifrado de {label} diverge do vetor.")
        encrypted_hash, encrypted_signature = sign_request(request_wire)
        encrypted_response_hash, encrypted_response_signature = sign_response(response_wire)
        if encrypted_hash != vector["encryptedBodySha256"] or encrypted_signature != vector["encryptedSignatureBase64Url"]:
            fail("Vetor autenticado da requisição cifrada diverge.")
        if encrypted_response_hash != vector["encryptedResponseSha256"] or encrypted_response_signature != vector["encryptedResponseSignatureBase64Url"]:
            fail("Vetor autenticado da resposta cifrada diverge.")
        if vector.get("encryptionContext") != "SyncDeck.Encryption.v1":
            fail("Contexto de derivação da chave cifrada diverge.")
    except Exception as error:
        fail(f"Falha ao validar vetor criptográfico: {error}")


def validate_source_invariants() -> None:
    main = read("android-app/app/src/main/kotlin/com/syncdeck/app/MainActivity.kt")
    wizard = read("android-app/app/src/main/kotlin/com/syncdeck/app/ActionWizard.kt")
    api = read("android-app/app/src/main/kotlin/com/syncdeck/app/ApiClient.kt")
    controller = read("android-app/app/src/main/kotlin/com/syncdeck/app/DeckController.kt")
    security = read("android-app/app/src/main/kotlin/com/syncdeck/app/Security.kt")
    server = read("windows-agent/src/DeckServer.cs")
    desktop = read("windows-agent/src/DesktopSecurity.cs")
    catalog = read("windows-agent/src/AppCatalog.cs")
    crypto = read("windows-agent/src/CryptoPairing.cs")
    executor = read("windows-agent/src/ActionExecutor.cs")
    chrome = read("windows-agent/src/ChromeProfileResolver.cs")
    stores = read("windows-agent/src/Stores.cs")
    program = read("windows-agent/src/Program.cs")
    installer = read("windows-agent/Instalar-no-Windows.bat")
    ios_api = read("ios-app/SyncDeck/APIClient.swift")
    ios_crypto = read("ios-app/SyncDeck/Security.swift")
    ios_model = read("ios-app/SyncDeck/DeckViewModel.swift")
    ios_view = read("ios-app/SyncDeck/ContentView.swift")
    workflow = read(".github/workflows/build.yml")
    if list((ROOT / "android-app/app/src/main").rglob("*.java")):
        fail("A migração Android para Kotlin está incompleta: ainda existem fontes Java.")
    android_requirements = {
        "Compose em MainActivity": "setContent" in main and "SyncDeckApp" in main,
        "grade configurável por orientação": all(value in main for value in [
            "GridCells.Fixed(columns)", "GridPreferences", "portraitColumns", "landscapeColumns",
        ]),
        "menu por toque longo": "onLongClick" in main,
        "contorno de estado aberto": "action.isOpen" in main and "BorderStroke" in main,
        "assistente de quatro tipos": all(value in wizard for value in ['"app"', '"url"', '"path"', '"command"']),
        "catálogo automático do Windows": "loadApplications" in wizard and "pickPath" in wizard,
        "polling de janelas": "getActionStates" in api and "startStatePolling" in controller,
        "fechamento múltiplo": "close-all" in controller and "CloseConfirmation" in main,
        "recuperação por fingerprint": "discoverPairedComputer" in api and "server_fingerprint" in api,
        "Wake-on-LAN": "wakeComputer" in api and "currentBroadcastAddresses" in api and "WAKE_ACTION_ID" in controller,
        "segredo no Keystore": "AndroidKeyStore" in security and "AES/GCM/NoPadding" in security,
        "cifragem do protocolo 2": "AES/CBC/PKCS5Padding" in security and "X-SyncDeck-Encryption" in api,
        "autenticação de resposta": "X-SyncDeck-Response-Signature" in api and "constantTimeEquals" in security,
        "limite de resposta": "MAX_RESPONSE = 524_288" in api,
    }
    for label, valid in android_requirements.items():
        if not valid:
            fail(f"Implementação Android incompleta — {label}.")
    auth_index = server.find("if (!Authenticate")
    state_index = server.find('/api/actions/state')
    if auth_index < 0 or state_index < auth_index:
        fail("Rota de estado precisa estar depois da autenticação.")
    windows_requirements = {
        "conteúdo cifrado": "PayloadCipher.Decrypt" in server and "PayloadCipher.Encrypt" in server and "EncryptionProtocol" in server,
        "cifra AES e derivação": "CipherMode.CBC" in crypto and "SyncDeck.Encryption.v1" in crypto and "HMACSHA256" in crypto,
        "HMAC em tempo constante": "FixedTimeEquals" in crypto,
        "proteção contra replay": "_nonces.TryAdd" in server and "Math.Abs(now - timestamp) > 90" in server,
        "limite de requisições": "window.Count <= 120" in server,
        "limite de conexões": "_activeConnections) > 16" in server,
        "rede privada": "NetworkInfo.IsPrivateOrLoopback" in server,
        "aprovação de execução no PC": "ApproveExecution" in server and "DesktopDecision" in desktop,
        "aprovação para salvar no PC": "ApproveSave" in server and "executionChanged" in server,
        "mudança do diretório protegida": "existing.WorkingDirectory" in server,
        "mudança do fallback protegida": "existing.FallbackUrl" in server,
        "negação padrão": "AcceptButton = deny" in desktop and "_remaining = 45" in desktop,
        "tokens de seleção": "SelectionToken" in catalog and "Consume" in catalog,
        "persistência DPAPI": "ProtectedData.Protect" in stores and "DataProtectionScope.CurrentUser" in stores,
        "backup de clientes": "ClientsBackup" in stores and "File.Replace(temp, DataPaths.Clients" in stores,
        "instância única": "Local\\SyncDeck.Agent.v1" in program,
        "instalação permanente": "%LOCALAPPDATA%\\SyncDeck\\Agent" in installer and "CurrentVersion\\Run" in installer,
    }
    for label, valid in windows_requirements.items():
        if not valid:
            fail(f"Implementação Windows incompleta — {label}.")
    if "close-all" not in server or "close-all" not in executor:
        fail("Operação close-all não está implementada no agente.")
    if "--profile-directory" not in chrome or "last_active_profiles" not in chrome:
        fail("Resolução do perfil Chrome está incompleta.")
    for action_id in ("chatgpt-web", "shutdown-pc"):
        if action_id not in stores:
            fail(f"Ação padrão obrigatória ausente: {action_id}.")
    if 'Arguments = "/s /t 5"' not in stores or "action.Type == \"command\" || action.Type == \"hotkey\"" not in stores:
        fail("Confirmação obrigatória para comandos está incompleta.")
    ios_requirements = {
        "autenticação de requisição": "X-SyncDeck-Signature" in ios_api,
        "autenticação de resposta": "X-SyncDeck-Response-Signature" in ios_api,
        "recuperação por impressão digital": "discoverPairedComputer" in ios_api and "server-fingerprint" in ios_api,
        "Wake-on-LAN": "sendMagicPacket" in ios_api and "SO_BROADCAST" in ios_api,
        "segredo no Keychain": "kSecAttrAccessibleWhenUnlockedThisDeviceOnly" in ios_crypto,
        "RSA compatível": "rsaEncryptionOAEPSHA1" in ios_crypto,
        "HMAC SHA-256": "HMAC<SHA256>" in ios_crypto,
        "fechamento múltiplo": "close-all" in ios_model and "multipleCloseAction" in ios_view,
        "layout 2/3 colunas": "landscape ? 3 : 2" in ios_view,
    }
    for label, valid in ios_requirements.items():
        if not valid:
            fail(f"Implementação iOS experimental incompleta — {label}.")
    for value in [
        "testDebugUnitTest lintRelease assembleDebug bundleRelease", "ProtocolVectorTest.java",
        "ProtocolVectorTest.swift", "SyncDeck-iOS-unsigned", "CODE_SIGNING_ALLOWED=NO",
        "windows-agent/Instalar-no-Windows.bat",
    ]:
        if value not in workflow:
            fail(f"Etapa obrigatória ausente no CI: {value}")


def validate_wrapper() -> None:
    path = ROOT / "android-app/gradle/wrapper/gradle-wrapper.jar"
    if not path.is_file():
        return
    expected = "76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3"
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        fail("Checksum do gradle-wrapper.jar não corresponde ao wrapper aprovado.")
    properties = read("android-app/gradle/wrapper/gradle-wrapper.properties")
    if "gradle-9.1.0-bin.zip" not in properties or "distributionSha256Sum=" not in properties:
        fail("Distribuição Gradle ou seu checksum não estão fixados.")


def validate_markdown_links() -> None:
    pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
    for path in ROOT.rglob("*.md"):
        if ".git" in path.parts:
            continue
        for target in pattern.findall(path.read_text(encoding="utf-8")):
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
    forbidden_names = {
        "local.properties", "keystore.properties", "clients.json", "clients.backup.json",
        "settings.json", "actions.json",
    }
    forbidden_suffixes = {
        ".apk", ".aab", ".exe", ".dll", ".pdb", ".jks", ".keystore", ".pfx",
        ".p12", ".ipa", ".mobileprovision", ".pem", ".key",
    }
    ignored_directories = {".git", "build", ".gradle", "dist", "__pycache__"}
    private_markers = tuple("-----BEGIN " + prefix + "PRIVATE KEY-----" for prefix in ("RSA ", "OPENSSH ", "EC ", ""))
    for path in ROOT.rglob("*"):
        relative = path.relative_to(ROOT)
        if any(part in ignored_directories for part in relative.parts):
            continue
        if path.is_dir() and path.suffix.lower() == ".xcarchive":
            fail(f"Arquivo não publicável encontrado: {relative}")
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
    validate_android()
    validate_ios_project()
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
    print("- Estrutura, versões e documentação: OK")
    print("- XML, JSON, manifesto e arte da loja: OK")
    print("- Protocolo autenticado e cifrado: OK")
    print("- Aprovações locais e armazenamento seguro: OK")
    print("- Arquivos publicáveis: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
