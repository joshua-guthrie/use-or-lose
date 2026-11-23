"""Load import inputs and publish or restore a whole reviewed import under lock."""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path

from .cross_year import AccountingError
from .importing import ImportPlan, canonical_json, valid_cache_key, valid_year_key
from .storage import atomic_write_bytes, atomic_write_json, backup_file, ensure_data_dir, load_json
from .storage_guard import check_write

JOURNAL = "import-recovery.json"


def check_pending_import(base: Path) -> None:
    journal = base / JOURNAL
    if journal.exists() or journal.is_symlink():
        raise AccountingError("RECOVERY_REQUIRED", "Interrupted backup import; run import-data --recover")


def import_path(base: Path, key: str) -> Path:
    """Use validated paths from an archive, store listing, or recovery journal."""
    parts = key.split("/")
    valid = key == "config.json"
    if len(parts) == 2 and parts[1].endswith(".json"):
        parent, filename = parts
        if parent == "leave_years":
            valid = valid_year_key(filename[:-5])
        elif parent == "holiday_cache":
            valid = valid_cache_key(filename[:-5])
    if not valid:
        raise AccountingError("INVALID_PATH", "Invalid canonical import destination")
    path = base.joinpath(*parts)
    if path.is_symlink() or path.parent.is_symlink() or not path.resolve().is_relative_to(base.resolve()):
        raise AccountingError("INVALID_PATH", "Import destination must be inside the selected datastore")
    return path


def read_store(base: Path) -> tuple[dict[str, dict], str]:
    check_pending_import(base)
    if (base / "carryover-reconciliation.json").exists():
        raise AccountingError("RECOVERY_REQUIRED", "Interrupted reconciliation; run carryover-reconcile --recover")
    if (base / "backups").is_symlink():
        raise AccountingError("INVALID_PATH", "Datastore backups cannot be a symbolic link")
    files = {}
    digests = {}
    paths = [base / "config.json"]
    for directory in ("leave_years", "holiday_cache"):
        folder = base / directory
        if folder.is_symlink():
            raise AccountingError("INVALID_PATH", "Import destination must be inside the selected datastore")
        paths.extend(sorted(folder.glob("*.json")))
    for path in paths:
        if not path.exists() and not path.is_symlink():
            continue
        key = path.relative_to(base).as_posix()
        if key.startswith("leave_years/") and not path.stem.isascii():
            continue
        if key.startswith("leave_years/") and not path.stem.isdecimal():
            continue
        raw = import_path(base, key).read_bytes()
        value = json.loads(raw.decode("utf-8"))
        if not isinstance(value, dict):
            raise AccountingError("INVALID_LEDGER", f"{key} must be a JSON object")
        files[key] = value
        digests[key] = hashlib.sha256(raw).hexdigest()
    check_pending_import(base)
    return files, hashlib.sha256(canonical_json(digests)).hexdigest()


def recover_import(base: Path) -> dict:
    """Roll back an interrupted import. Safe to retry until every file is restored."""
    journal = base / JOURNAL
    if journal.is_symlink():
        raise AccountingError("INVALID_PATH", "Import recovery journal cannot be a symbolic link")
    if not journal.exists():
        return {"status": "unchanged", "restored_files": []}
    payload = load_json(journal)
    if not isinstance(payload, dict) or payload.get("version") != 1 or not isinstance(payload.get("originals"), dict):
        raise AccountingError("RECOVERY_REQUIRED", "Invalid import recovery journal. Keep it for inspection.")
    originals = {
        key: (import_path(base, key), None if data is None else base64.b64decode(data, validate=True))
        for key, data in payload["originals"].items()
    }
    check_write(journal)
    for path, _ in originals.values():
        check_write(path)
    for path, original in originals.values():
        if original is None:
            path.unlink(missing_ok=True)
        else:
            atomic_write_bytes(path, original)
    journal.unlink()
    return {"status": "recovered", "restored_files": sorted(originals)}


def publish_import(base: Path, plan: ImportPlan) -> None:
    """Publish a ready plan after locked revalidation and keep recovery bytes."""
    check_pending_import(base)
    if plan.conflicts:
        raise ValueError("Cannot publish a blocked import")
    if not plan.writes:
        return
    paths = {key: import_path(base, key) for key in plan.writes}
    for path in paths.values():
        check_write(path)
        if path.exists() and not path.is_file():
            raise AccountingError("INVALID_PATH", "Import destination is not a regular file")
    originals = {
        key: base64.b64encode(path.read_bytes()).decode("ascii") if path.exists() else None
        for key, path in paths.items()
    }
    ensure_data_dir(base)
    journal = base / JOURNAL
    atomic_write_json(journal, {"version": 1, "originals": originals}, overwrite=False)
    try:
        for key, path in paths.items():
            if originals[key] is not None:
                backup_file(path)
            atomic_write_json(path, plan.writes[key], overwrite=originals[key] is not None)
    except Exception as exc:
        try:
            recover_import(base)
        except Exception as recovery:
            raise AccountingError("RECOVERY_REQUIRED", "Import interrupted. Run import-data --recover.") from recovery
        raise AccountingError("IMPORT_ROLLED_BACK", "Import failed and the prior datastore was restored.") from exc
    journal.unlink()
