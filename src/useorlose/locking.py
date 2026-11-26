"""OS-backed transaction locks shared by CLI, GUI backends, and MCP callers."""

from __future__ import annotations

import os
from contextlib import contextmanager
from pathlib import Path

import portalocker
import typer
from typer.core import TyperCommand


@contextmanager
def datastore_lock(data_dir: Path | str, timeout: float | None = None):
    """Hold an advisory OS lock for a complete read/modify/write operation.

    Never unlink the lock file: replacing its inode could let two writers lock
    different files. The OS releases ownership when a process exits or crashes.
    """
    # Typer may defer Path conversion until its callback wrapper, after invoke.
    data_dir = Path(data_dir).resolve()
    data_dir.mkdir(parents=True, exist_ok=True)
    if (data_dir / ".useorlose.lock").is_symlink():
        raise RuntimeError("Datastore lock must not be a symbolic link")
    timeout = float(os.environ.get("FEDLEAVE_LOCK_TIMEOUT", "30")) if timeout is None else timeout
    with portalocker.Lock(str(data_dir / ".useorlose.lock"), timeout=timeout):
        yield


@contextmanager
def datastore_read_lock(data_dir: Path | str):
    """Share an existing lock without creating a datastore during a preview."""
    lock_path = Path(data_dir) / ".useorlose.lock"
    if lock_path.is_symlink():
        raise ValueError("Datastore lock must not be a symbolic link")
    if lock_path.exists():
        with portalocker.Lock(
            str(lock_path),
            mode="r",
            flags=portalocker.LOCK_SH | portalocker.LOCK_NB,
            timeout=float(os.environ.get("FEDLEAVE_LOCK_TIMEOUT", "30")),
        ):
            yield
    else:
        yield


class LockedCommand(TyperCommand):
    """Serialize every CLI leaf, including reports that migrate/backfill data."""

    def invoke(self, ctx):
        from .config import get_default_data_dir
        from .cross_year import AccountingError, preflight_creation
        from .import_storage import check_pending_import

        readonly = (
            ctx.info_name
            in {
                "snapshot",
                "balance",
                "use-or-lose",
                "use-or-loose",
                "expirations",
                "years",
                "list",
                "month",
                "pay-period",
                "pay-periods",
                "daily-activity",
                "activity",
                "compare-leave-balances",
                "types",
                "help",
                "export-data",
            }
            or ctx.params.get("preview")
            or (ctx.info_name == "validate" and not ctx.params.get("apply"))
        )
        try:
            if ctx.info_name == "import-data":
                # The command preflights under a read lock and validates again
                # under a writer lock. Preview and blocked preflight write nothing.
                return super().invoke(ctx)
            base = Path(get_default_data_dir(ctx.params.get("data_dir")))
            if ctx.info_name in {"init", "rollover"}:
                check_pending_import(base)
                preflight_creation(base, ctx.info_name, ctx.params)
            if readonly:
                with datastore_read_lock(base):
                    check_pending_import(base)
                    return super().invoke(ctx)
            check_pending_import(base)
            with datastore_lock(base):
                check_pending_import(base)
                return super().invoke(ctx)
        except portalocker.exceptions.LockException:
            typer.echo("Datastore busy: lock wait timed out; retry the command.", err=True)
            raise typer.Exit(75) from None
        except (AccountingError, ValueError, OSError, TypeError, KeyError) as exc:
            from .commands.rollover import report_error

            report_error(exc, bool(ctx.params.get("json_output")))


def coordinate_commands(application) -> None:
    """Apply the same lock to all registered and nested CLI commands."""
    for command in application.registered_commands:
        # Newer Typer versions store their default class explicitly rather
        # than leaving it unset. Both forms must receive the transaction lock.
        if command.cls is None or command.cls is TyperCommand:
            command.cls = LockedCommand
    for group in application.registered_groups:
        coordinate_commands(group.typer_instance)
