"""Run the desktop backup review with the real selected CLI."""

import os
import sysconfig
from pathlib import Path

import pytest
from PySide6.QtCore import QTimer
from PySide6.QtWidgets import QFileDialog, QMessageBox

from leave_year_support import store, tree
from useorlose_gui.backend import BackendOptions, UseOrLoseBackend
from useorlose_gui.main_window import MainWindow
from useorlose_gui.settings import GuiSettings


@pytest.mark.parametrize("decision", ["keep", "cancel replace", "apply replace"])
def test_conflicting_gui_merge_requires_a_separate_replace_confirmation(qtbot, monkeypatch, tmp_path, decision):
    current = tmp_path / "current"
    incoming = tmp_path / "incoming"
    archive = tmp_path / "backup.json"
    executable = Path(sysconfig.get_path("scripts")) / ("use-or-lose.exe" if os.name == "nt" else "use-or-lose")
    for base, target in ((current, 5), (incoming, 8)):
        store(base, balances={"annual": 20})
        backend = UseOrLoseBackend(BackendOptions(str(executable), str(base)))
        backend.force_balance(date="2026-02-01", category="annual", hours=target, comment="Synthetic target")
    backend.run_text(["export-data", "--output", str(archive)])
    before, source_bytes = tree(current), archive.read_bytes()

    window = MainWindow(GuiSettings(data_dir=str(current), useorlose_path=str(executable)))
    qtbot.addWidget(window)
    destinations = []
    refreshed = []
    monkeypatch.setattr(window, "refresh", lambda: refreshed.append(True))
    monkeypatch.setattr(window, "_select_import_mode", lambda: "--merge")
    monkeypatch.setattr(QFileDialog, "getOpenFileName", lambda *_args: (str(archive), "JSON files (*.json)"))
    original_exec = QMessageBox.exec

    def execute(dialog):
        assert tree(current) == before
        destinations.append(dialog.text())
        if "blocked" in dialog.text():
            assert "5 h in your current data and 8 h in the backup" in dialog.text()
            choice = "Keep current data" if decision == "keep" else "Review Replace"
            button = next(button for button in dialog.buttons() if button.text() == choice)
        else:
            assert dialog.text() == "Replace included files?"
            button = dialog.button(QMessageBox.Ok if decision == "apply replace" else QMessageBox.Cancel)
        QTimer.singleShot(0, button.click)
        return original_exec(dialog)

    monkeypatch.setattr(QMessageBox, "exec", execute)
    window.import_data()
    balance = window.backend.balance(2026, as_of="2026-02-01")["balances"]["annual"]
    assert archive.read_bytes() == source_bytes
    assert len(destinations) == (1 if decision == "keep" else 2)
    if decision == "apply replace":
        assert balance == 8 and refreshed == [True]
        assert list((current / "backups").glob("2026.json.*.bak"))
    else:
        assert balance == 5 and not refreshed and tree(current) == before
