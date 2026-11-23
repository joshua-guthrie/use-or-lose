"""Qt control flow and real selected-CLI preview/apply integration."""

import os
import sysconfig
import threading
from pathlib import Path

import pytest
from PySide6.QtWidgets import QDialog, QDialogButtonBox, QMessageBox

from fedleave_gui.backend import BackendError, BackendOptions, FedleaveBackend
from fedleave_gui.main_window import MANUAL_YEAR_WARNING, MainWindow, StartYearDialog
from leave_year_support import store, tree


class Backend:
    def __init__(self):
        self.calls = []

    def leave_years(self):
        return {
            "years": [
                {"leave_year": 2026, "valid": True, "start_date": "2026-01-11", "end_date": "2027-01-09"},
                {"leave_year": 2025, "valid": False, "start_date": None, "end_date": None},
            ]
        }

    def preview_year(self, **options):
        self.calls.append(("preview", options))
        return {
            "leave_year_start": "2027-01-10",
            "leave_year_end": "2028-01-08",
            "pay_period_count": 26,
            "opening_balances": {"annual": 240, "sick": 600},
            "lots": [],
            "adjustments": [],
            "warnings": [],
            "preview_fingerprint": "reviewed",
        }

    def init_year(self, **options):
        self.calls.append(("manual", options))

    def create_carryover_year(self, **options):
        self.calls.append(("carryover", options))


def ready_carryover(qtbot, backend):
    dialog = StartYearDialog(backend=backend)
    qtbot.addWidget(dialog)
    dialog.year.setValue(2027)
    dialog.mode.setCurrentIndex(1)
    qtbot.waitUntil(lambda: dialog._pending is None)
    dialog.previous.setCurrentIndex(1)
    dialog.preview_creation()
    qtbot.waitUntil(lambda: dialog._pending is None)
    return dialog


def test_GUI_001_002_003_004_008_009_010_011_012_013(qtbot):
    backend = Backend()
    dialog = StartYearDialog(backend=backend)
    qtbot.addWidget(dialog)
    assert [dialog.mode.itemText(i) for i in range(2)] == [
        "Manual Starting Balances",
        "Carry Over From Previous Leave Year",
    ]
    assert dialog.previous.currentText() == "No Previous Leave Year"
    assert not dialog.previous.isEnabled() and dialog.annual.isEnabled()
    assert dialog.explanation.text() == MANUAL_YEAR_WARNING
    dialog.year.setValue(2027)
    dialog.mode.setCurrentIndex(1)
    qtbot.waitUntil(lambda: dialog._pending is None)
    assert dialog.previous.isEnabled() and not dialog.annual.isEnabled()
    assert dialog.previous.currentData() is None
    assert not dialog.previous.model().item(2).isEnabled()
    dialog.preview_creation()
    assert not backend.calls
    dialog.previous.setCurrentIndex(1)
    dialog.preview_creation()
    qtbot.waitUntil(lambda: dialog._pending is None)
    assert "annual: 240.00 hours" in dialog.preview_text.toPlainText()
    assert backend.calls == [("preview", {"year": 2027, "from_year": 2026, "pay_periods": None})]
    assert dialog.buttons.button(QDialogButtonBox.Ok).isEnabled()
    dialog.reject()


@pytest.mark.parametrize("confirm", [False, True], ids=["GUI-005", "GUI-006"])
def test_manual_confirmation_warning_before_cli_write(qtbot, monkeypatch, tmp_path, confirm):
    monkeypatch.setattr(MainWindow, "refresh", lambda self: None)
    window = MainWindow()
    qtbot.addWidget(window)
    backend = Backend()
    window.backend = backend
    dialog = StartYearDialog(backend=backend)
    qtbot.addWidget(dialog)
    dialog.year.setValue(2027)
    dialog.start.setText("2027-01-10")
    dialog.annual.setValue(12.25)
    dialog.exec = lambda: QDialog.Accepted
    monkeypatch.setattr("fedleave_gui.main_window.StartYearDialog", lambda *args, **kwargs: dialog)
    messages = []

    def question(parent, title, text):
        messages.append(text)
        return QMessageBox.Yes if confirm else QMessageBox.No

    monkeypatch.setattr(QMessageBox, "question", question)
    before = tree(tmp_path)
    window.new_leave_year()
    assert MANUAL_YEAR_WARNING in messages[0]
    assert tree(tmp_path) == before
    assert [name for name, _ in backend.calls] == (["manual"] if confirm else [])
    if confirm:
        options = backend.calls[0][1]
        assert options["annual_start"] == 12.25 and "from_year" not in options
    dialog.reject()


def test_GUI_014_carryover_confirmation_submits_only_reviewed_cli_mode(qtbot, monkeypatch):
    backend = Backend()
    dialog = ready_carryover(qtbot, backend)
    monkeypatch.setattr(MainWindow, "refresh", lambda self: None)
    window = MainWindow()
    qtbot.addWidget(window)
    window.backend = backend
    dialog.exec = lambda: QDialog.Accepted
    monkeypatch.setattr("fedleave_gui.main_window.StartYearDialog", lambda *args, **kwargs: dialog)
    monkeypatch.setattr(QMessageBox, "question", lambda *args: QMessageBox.Yes)
    window.new_leave_year()
    assert backend.calls[-1] == (
        "carryover",
        {"year": 2027, "from_year": 2026, "pay_periods": None, "expected_fingerprint": "reviewed"},
    )
    dialog.reject()


def test_GUI_015_016_changed_selection_invalidates_preview(qtbot):
    dialog = ready_carryover(qtbot, Backend())
    dialog.year.setValue(2028)
    assert dialog.preview_payload is None and not dialog.buttons.button(QDialogButtonBox.Ok).isEnabled()
    dialog.mode.setCurrentIndex(0)
    assert dialog.previous.currentData() is None and dialog.annual.isEnabled()
    dialog.reject()


def test_GUI_017_backend_failure_no_apply(qtbot):
    class Failing(Backend):
        def preview_year(self, **options):
            raise BackendError("SOURCE_NOT_CONTIGUOUS")

    dialog = ready_carryover(qtbot, Failing())
    assert dialog.preview_payload is None
    assert "SOURCE_NOT_CONTIGUOUS" in dialog.preview_text.toPlainText()
    assert not dialog.buttons.button(QDialogButtonBox.Ok).isEnabled()
    dialog.reject()


def test_GUI_018_019_020_close_and_mode_change_during_slow_preview(qtbot):
    started, release = threading.Event(), threading.Event()

    class Slow(Backend):
        def leave_years(self):
            started.set()
            release.wait(5)
            return super().leave_years()

    dialog = StartYearDialog(backend=Slow())
    qtbot.addWidget(dialog)
    dialog.mode.setCurrentIndex(1)
    qtbot.waitUntil(started.is_set)
    # The GUI remains responsive while the CLI worker waits.
    dialog.mode.setCurrentIndex(0)
    assert dialog.annual.isEnabled() and dialog.preview_payload is None
    dialog.reject()
    release.set()
    assert dialog._closed


def test_GUI_021_INT_001_selected_real_backend_preview_apply(qtbot, tmp_path):
    store(tmp_path, balances={"annual": 280, "credit": 30, "sick": 600})
    executable = Path(sysconfig.get_path("scripts")) / ("fedleave.exe" if os.name == "nt" else "fedleave")
    assert executable.is_file(), f"Installed test CLI missing: {executable}"
    backend = FedleaveBackend(BackendOptions(fedleave_path=str(executable), data_dir=str(tmp_path)))
    before = tree(tmp_path)
    dialog = ready_carryover(qtbot, backend)
    assert tree(tmp_path) == before
    preview = dialog.preview_payload
    assert preview is not None, dialog.preview_text.toPlainText()
    assert preview["opening_balances"]["annual"] == 240
    assert preview["opening_balances"]["credit"] == 24
    created = backend.create_carryover_year(
        year=2027, from_year=2026, expected_fingerprint=preview["preview_fingerprint"]
    )
    assert created["opening_balances"] == preview["opening_balances"]
    actual = backend.balance(2027, as_of="2027-01-10")
    assert actual["balances"]["annual"] == 240 and actual["balances"]["sick"] == 600
    dialog.reject()


def test_GUI_023_keyboard_mode_source_and_preview(qtbot):
    from PySide6.QtCore import Qt

    backend = Backend()
    dialog = StartYearDialog(backend=backend)
    qtbot.addWidget(dialog)
    dialog.year.setValue(2027)
    dialog.show()
    dialog.mode.setFocus()
    qtbot.keyClick(dialog.mode, Qt.Key_Down)
    qtbot.waitUntil(lambda: dialog._pending is None)
    dialog.previous.setFocus()
    qtbot.keyClick(dialog.previous, Qt.Key_Down)
    assert dialog.previous.currentData() == 2026
    dialog.preview_button.setFocus()
    qtbot.keyClick(dialog.preview_button, Qt.Key_Space)
    qtbot.waitUntil(lambda: dialog.preview_payload is not None)
    assert dialog.buttons.button(QDialogButtonBox.Ok).isEnabled()
    dialog.reject()


def test_reconciliation_GUI_reviews_full_chain_before_apply(qtbot, monkeypatch):
    from fedleave_gui.main_window import CarryoverReconciliationDialog

    class Reconciler(Backend):
        def reconcile_carryover(self, year, **options):
            self.calls.append((year, options))
            return {
                "years": [
                    {
                        "leave_year": 2027,
                        "changed": True,
                        "old_openings": {"annual": 100},
                        "new_openings": {"annual": 92},
                        "new_lots": [],
                        "removed_lot_ids": [],
                    }
                ],
                "warnings": [],
                "preview_fingerprint": "reviewed-chain",
            }

    backend = Reconciler()
    dialog = CarryoverReconciliationDialog(backend, 2027)
    qtbot.addWidget(dialog)
    qtbot.waitUntil(lambda: dialog.preview_payload is not None)
    assert "100.00 → 92.00" in dialog.text.toPlainText()
    assert backend.calls == [(2027, {"preview": True})]
    monkeypatch.setattr(MainWindow, "refresh", lambda self: None)
    window = MainWindow()
    qtbot.addWidget(window)
    window.year = 2027
    window.backend = backend
    dialog.exec = lambda: QDialog.Accepted
    monkeypatch.setattr("fedleave_gui.main_window.CarryoverReconciliationDialog", lambda *args, **kwargs: dialog)
    window.reconcile_carryover()
    assert backend.calls[-1] == (2027, {"preview": False, "expected_fingerprint": "reviewed-chain"})
    dialog.reject()


def _fixed_date(monkeypatch, value):
    from datetime import date

    class Clock(date):
        @classmethod
        def today(cls):
            return value

    monkeypatch.setattr("fedleave_gui.main_window.date", Clock)


def _calendar_with_real_backend(qtbot, tmp_path):
    from fedleave_gui.settings import GuiSettings

    executable = Path(sysconfig.get_path("scripts")) / ("fedleave.exe" if os.name == "nt" else "fedleave")
    window = MainWindow(GuiSettings(data_dir=str(tmp_path), fedleave_path=str(executable)))
    qtbot.addWidget(window)
    return window


def test_early_january_uses_the_existing_containing_ledger_without_prompt(qtbot, monkeypatch, tmp_path):
    from datetime import date

    store(tmp_path, balances={"annual": 20})
    _fixed_date(monkeypatch, date(2027, 1, 9))
    window = _calendar_with_real_backend(qtbot, tmp_path)
    before = tree(tmp_path)

    def unexpected_prompt(*args):
        raise AssertionError("A covered date must not prompt for leave-year creation")

    monkeypatch.setattr(QMessageBox, "question", unexpected_prompt)
    window.start_initial_load()
    assert (window.year, window.month, window.display_ledger_year) == (2027, 1, 2026)
    assert window.balance_snapshot["as_of"] == "2027-01-09"
    assert window.balance_snapshot["balances"]["annual"] == 20
    assert any(row["end"] == "2027-01-09" for row in window.month_json["pay_periods"])
    assert tree(tmp_path) == before


def test_startup_offers_adjacent_year_and_only_creates_after_review(qtbot, monkeypatch, tmp_path):
    from datetime import date

    store(tmp_path, balances={"annual": 20})
    _fixed_date(monkeypatch, date(2027, 1, 10))
    window = _calendar_with_real_backend(qtbot, tmp_path)
    previous = (tmp_path / "leave_years/2026.json").read_bytes()
    prompts = []

    def question(_parent, title, text, *_args):
        prompts.append((title, text))
        assert not (tmp_path / "leave_years/2027.json").exists()
        return QMessageBox.Yes

    def review(dialog):
        qtbot.waitUntil(lambda: dialog._pending is None, timeout=5000)
        assert dialog.year.value() == 2027 and not dialog.year.isEnabled()
        assert dialog.mode.currentData() == "carryover" and dialog.previous.currentData() == 2026
        dialog.preview_creation()
        qtbot.waitUntil(lambda: dialog.preview_payload is not None, timeout=5000)
        dialog.accept()
        return QDialog.Accepted

    monkeypatch.setattr(QMessageBox, "question", question)
    monkeypatch.setattr(StartYearDialog, "exec", review)
    window.start_initial_load()

    assert [title for title, _ in prompts] == ["Create a Leave Year", "Create Leave Year"]
    assert "2027-01-10" in prompts[1][1]
    assert (window.year, window.month, window.display_ledger_year) == (2027, 1, 2027)
    assert window.balance_snapshot["as_of"] == "2027-01-10"
    assert window.balance_snapshot["balances"]["annual"] == 20
    assert {row["end"] for row in window.month_json["pay_periods"]} >= {"2027-01-09", "2027-01-23"}
    assert (tmp_path / "leave_years/2026.json").read_bytes() == previous
    assert sorted(path.name for path in (tmp_path / "leave_years").glob("*.json")) == ["2026.json", "2027.json"]


def test_passive_browse_does_not_create_and_cancelled_future_edit_keeps_context(qtbot, monkeypatch, tmp_path):
    from datetime import date

    store(tmp_path)
    _fixed_date(monkeypatch, date(2026, 12, 15))
    window = _calendar_with_real_backend(qtbot, tmp_path)
    before = tree(tmp_path)
    prompts = []
    monkeypatch.setattr(QMessageBox, "question", lambda _parent, title, *_args: prompts.append(title) or QMessageBox.No)
    window.refresh()
    window.next_month()
    window.next_month()
    assert (window.year, window.month) == (2027, 2)
    assert window.display_ledger_year is None
    assert not prompts

    year, month, cutoff = window.year, window.month, window.balance_as_of
    row = next(row for row in window.month_json["days"] if row["date"] == "2027-02-01")
    window.edit_day(row)
    assert prompts == ["Create a Leave Year"]
    assert (window.year, window.month, window.balance_as_of) == (year, month, cutoff)
    assert tree(tmp_path) == before


def test_unreadable_proposed_year_is_never_replaced(qtbot, monkeypatch, tmp_path):
    from datetime import date

    store(tmp_path)
    broken = tmp_path / "leave_years/2027.json"
    broken.write_bytes(b"incomplete")
    _fixed_date(monkeypatch, date(2027, 1, 10))
    window = _calendar_with_real_backend(qtbot, tmp_path)
    messages = []
    monkeypatch.setattr(QMessageBox, "warning", lambda _parent, _title, message: messages.append(message))
    window.start_initial_load()
    assert messages and "2027 cannot be created" in messages[0]
    assert broken.read_bytes() == b"incomplete"
    assert window.balance_snapshot is None


def test_confirmed_future_year_resumes_the_original_calendar_edit(qtbot, monkeypatch, tmp_path):
    from datetime import date

    store(tmp_path, balances={"annual": 20})
    _fixed_date(monkeypatch, date(2026, 12, 15))
    window = _calendar_with_real_backend(qtbot, tmp_path)
    window.year, window.month = 2027, 2
    window.refresh()
    row = next(row for row in window.month_json["days"] if row["date"] == "2027-02-01")

    def review(dialog):
        qtbot.waitUntil(lambda: dialog._pending is None, timeout=5000)
        dialog.preview_creation()
        qtbot.waitUntil(lambda: dialog.preview_payload is not None, timeout=5000)
        dialog.accept()
        return QDialog.Accepted

    class Edit:
        def __init__(self, day, _parent):
            assert day["date"] == "2027-02-01"
            assert window.backend.leave_years()["years"][-1]["leave_year"] == 2027

        def exec(self):
            return QDialog.Accepted

        def values(self):
            return {"annual": -3}

        def comments(self):
            return {"annual": "Synthetic test leave"}

    class Review:
        def __init__(self, *_args):
            pass

        def exec(self):
            return QDialog.Accepted

    monkeypatch.setattr(QMessageBox, "question", lambda *_args: QMessageBox.Yes)
    monkeypatch.setattr(StartYearDialog, "exec", review)
    monkeypatch.setattr("fedleave_gui.main_window.DayEditDialog", Edit)
    monkeypatch.setattr("fedleave_gui.main_window.SaveDayPreviewDialog", Review)
    window.edit_day(row)

    assert (window.year, window.month, window.balance_as_of) == (2027, 2, date(2026, 12, 15))
    entries = next(item["entries"] for item in window.month_json["days"] if item["date"] == row["date"])
    assert [(entry["category"], entry["hours"], entry["description"]) for entry in entries] == [
        ("annual", 3, "Synthetic test leave")
    ]
