"""Render editable phone mockups to PNG for the FedLeave Android specification."""

import argparse
import json
from datetime import date, timedelta
from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SURFACE = "#FCFCFF"
CANVAS = "#E9EEF7"
INK = "#13223D"
MUTED = "#485A77"
PRIMARY = "#194AAB"
SELECTED = "#DDE8FF"
LINE = "#D9E3F2"
WARN = "#765009"
WARN_BG = "#FFF3D6"
PHONE_WIDTH = 366
PHONE_HEIGHT = 674
PADDING = 22
GAP = 24
MOCKUPS = Path(__file__).resolve().parent
DAY_MARKER_FILES = {
    "holidays": "holiday",
    "paydays": "payday",
    "ends": "pay-period-end",
    "year_ends": "leave-year-end",
}


@lru_cache
def get_font(font_dir, size, bold=False):
    name = "Lato-Bold.ttf" if bold else "Lato-Regular.ttf"
    return ImageFont.truetype(str(font_dir / name), size)


def draw_day_marker(draw, key, x, y, color):
    if key == "holidays":
        draw.polygon(
            ((x + 4, y), (x + 5, y + 3), (x + 8, y + 3), (x + 6, y + 5), (x + 7, y + 8),
             (x + 4, y + 6), (x + 1, y + 8), (x + 2, y + 5), (x, y + 3), (x + 3, y + 3)),
            fill=color,
        )
    elif key == "paydays":
        draw.ellipse((x, y, x + 8, y + 8), outline=color)
        draw.line((x + 4, y + 2, x + 4, y + 6), fill=color)
    elif key == "ends":
        draw.line((x + 1, y, x + 1, y + 8), fill=color)
        draw.polygon(((x + 2, y), (x + 8, y + 2), (x + 2, y + 5)), fill=color)
    elif key == "year_ends":
        draw.rectangle((x, y + 1, x + 8, y + 8), outline=color)
        draw.line((x + 2, y, x + 2, y + 2), fill=color)
        draw.line((x + 6, y, x + 6, y + 2), fill=color)
        draw.line((x + 2, y + 5, x + 4, y + 7, x + 6, y + 4), fill=color)


class Screen:
    def __init__(self, image, font_dir, x, top, screen, width, height):
        self.image = image
        self.draw = ImageDraw.Draw(image)
        self.font_dir = font_dir
        self.x = x
        self.top = top
        self.right = x + width
        self.bottom = top + height
        self.left = x + PADDING
        self.inner_right = self.right - PADDING
        self.width = width - 2 * PADDING
        self.cursor = top + 22
        self.screen = screen

    def text(self, x, y, label, size=16, color=INK, bold=False, anchor="la"):
        font = get_font(self.font_dir, size, bold)
        self.draw.text((x, y), label, font=font, fill=color, anchor=anchor)

    def wrap(self, value, size, width, bold=False):
        font = get_font(self.font_dir, size, bold)
        lines = []
        current = ""
        for word in value.split():
            joined = f"{current} {word}".strip()
            if self.draw.textlength(joined, font=font) <= width or not current:
                current = joined
            else:
                lines.append(current)
                current = word
        return [*lines, current] if current else lines

    def box(self, x, y, right, bottom, fill, radius=10):
        self.draw.rounded_rectangle((x, y, right, bottom), radius, fill=fill)

    def mark(self, number, y, x=None):
        if number is None:
            return
        x = self.right - 14 if x is None else x
        label = str(number)
        self.draw.ellipse((x - 11, y - 11, x + 11, y + 11), fill=PRIMARY, outline=SURFACE, width=2)
        self.text(x, y, label, 12, "#FFFFFF", bold=True, anchor="mm")

    def render(self):
        self.box(self.x + 3, self.top + 5, self.right + 3, self.bottom + 5, "#C5D2E6", 24)
        self.box(self.x, self.top, self.right, self.bottom, SURFACE, 24)
        if toolbar := self.screen.get("toolbar"):
            self.toolbar(toolbar)
        else:
            self.text(self.left, self.cursor, "FedLeave", 13, PRIMARY, bold=True)
            top_action = self.screen.get("top_action")
            self.text(self.inner_right, self.cursor, top_action or "9:41", 13, PRIMARY if top_action else MUTED,
                      bool(top_action), "ra")
        self.cursor += 28
        subtitle = self.screen.get("subtitle")
        if not self.screen.get("compact_header"):
            self.paragraph({"text": self.screen["title"]}, size=28, bold=True, padding=4)
        if subtitle:
            self.paragraph({"text": subtitle}, size=14, color=MUTED, padding=12)
        for element in self.screen["items"]:
            if element["type"] == "heading":
                self.paragraph(element, size=17, bold=True, padding=8)
            elif element["type"] == "text":
                self.paragraph(element, size=14, color=MUTED, padding=11)
            else:
                getattr(self, element["type"])(element)
        nav = self.screen.get("nav")
        limit = self.bottom - (58 if nav else 14)
        if self.cursor > limit:
            raise ValueError(f"{self.screen['id']} overflows by {self.cursor - limit}px")
        if nav:
            y = self.bottom - 48
            self.draw.line((self.left, y - 5, self.inner_right, y - 5), fill=LINE)
            for index, label in enumerate(("Calendar", "Reports", "Settings")):
                center = self.left + (index + 0.5) * (self.width // 3)
                if label == nav:
                    self.box(center - 50, y + 3, center + 50, y + 33, SELECTED, 14)
                self.text(center, y + 18, label, 13, PRIMARY if label == nav else MUTED, label == nav, "mm")
        if menu := self.screen.get("dropdown_menu"):
            self.menu_panel(menu)
        self.text(self.x, self.bottom + 11, self.screen["id"], 12, MUTED, bold=True)

    def toolbar(self, actions):
        y = self.cursor + 9
        label_x = self.left
        if "back" in actions:
            label_x += 30
            x = self.left + 8
            self.draw.line((x + 8, y, x - 7, y), fill=PRIMARY, width=2)
            self.draw.line((x - 1, y - 6, x - 7, y, x - 1, y + 6), fill=PRIMARY, width=2)
            self.mark(self.screen.get("back_note"), y + 5, x=self.x + 8)
        if "menu" in actions:
            label_x += 30
            x = self.left + 8
            for offset in (-5, 0, 5):
                self.draw.line((x - 8, y + offset, x + 8, y + offset), fill=PRIMARY, width=2)
        if self.screen.get("compact_header"):
            self.text(label_x, self.cursor - 3, self.screen["title"], 21, bold=True)
        else:
            self.text(label_x, self.cursor, "FedLeave", 13, PRIMARY, bold=True)
        if "share" in actions:
            x = self.inner_right - 9
            points = ((x - 7, y), (x + 7, y - 7), (x + 7, y + 7))
            for point in points[1:]:
                self.draw.line((*points[0], *point), fill=PRIMARY, width=2)
            for center_x, center_y in points:
                self.draw.ellipse((center_x - 3, center_y - 3, center_x + 3, center_y + 3), fill=PRIMARY)
            self.mark(self.screen.get("share_note"), y + 16)

    def paragraph(self, item, size, color=INK, bold=False, padding=8):
        y = self.cursor
        step = size + 5
        for line in self.wrap(item["text"], size, self.width - 8, bold):
            self.text(self.left, self.cursor, line, size, color, bold)
            self.cursor += step
        self.mark(item.get("note"), y + 12)
        self.cursor += padding

    def row(self, item):
        label, value = item["text"], item.get("value", "")
        value_end = self.inner_right - 10
        self.box(self.left, self.cursor, self.inner_right, self.cursor + 35, "#F0F4FC", 8)
        label_x = self.left + 10
        if marker := item.get("marker"):
            draw_day_marker(self.draw, marker, label_x, self.cursor + 13, PRIMARY)
            label_x += 18
        self.text(label_x, self.cursor + 17, label, 15, INK, True, "lm")
        if item.get("dropdown"):
            x, y = value_end - 5, self.cursor + 17
            self.draw.line((x - 4, y - 2, x, y + 2, x + 4, y - 2), fill=PRIMARY, width=2)
            value_end -= 16
        self.text(value_end, self.cursor + 17, value, 14, MUTED, anchor="rm")
        self.mark(item.get("note"), self.cursor + 17)
        self.cursor += 42

    def card(self, item):
        y = self.cursor
        lines = self.wrap(item.get("body", ""), 14, self.width - 28)
        height = 42 + 19 * len(lines)
        fill = WARN_BG if item.get("warning") else SELECTED
        color = WARN if item.get("warning") else INK
        self.box(self.left, y, self.inner_right, y + height, fill, 13)
        self.text(self.left + 14, y + 10, item["text"], 16, color, bold=True)
        if indicator := item.get("indicator"):
            self.text(self.inner_right - 14, y + 10, indicator, 21, PRIMARY, bold=True, anchor="ra")
        for index, text in enumerate(lines):
            self.text(self.left + 14, y + 34 + 19 * index, text, 14, color)
        self.mark(item.get("note"), y + 19)
        self.cursor += height + 10

    def balance_card(self, item):
        y = self.cursor
        balances = item["balances"]
        height = 60 + 41 * ((len(balances) + 3) // 4)
        self.box(self.left, y, self.inner_right, y + height, SELECTED, 13)
        self.text(self.left + 14, y + 9, item["text"], 16, bold=True)
        self.text(self.inner_right - 14, y + 9, "›", 21, PRIMARY, True, "ra")
        for index, (label, hours) in enumerate(balances):
            row, column = divmod(index, 4)
            x = self.left + 14 + column * ((self.width - 28) // 4)
            row_y = y + 34 + row * 41
            self.text(x, row_y, label, 12, MUTED)
            self.text(x, row_y + 15, hours, 17, bold=True)
        if hint := item.get("hint"):
            self.text(self.left + 14, y + height - 23, hint, 13, PRIMARY, True)
        self.mark(item.get("note"), y + 19)
        self.cursor += height + 10

    def action(self, item):
        emphasis = item.get("emphasis", True)
        fill = PRIMARY if emphasis else SELECTED
        self.box(self.left, self.cursor, self.inner_right, self.cursor + 38, fill, 18)
        self.text(
            (self.left + self.inner_right) // 2, self.cursor + 19, item["text"], 15,
            "#FFFFFF" if emphasis else PRIMARY, bold=True, anchor="mm",
        )
        self.mark(item.get("note"), self.cursor + 18)
        self.cursor += 47

    def menu_panel(self, item):
        top = self.top + 49
        right = self.left + min(248, self.width)
        row_height = 48
        height = len(item["entries"]) * row_height + 12
        self.box(self.left + 3, top + 4, right + 3, top + height + 4, "#C5D2E6", 13)
        self.box(self.left, top, right, top + height, "#FFFFFF", 13)
        for index, label in enumerate(item["entries"]):
            center_y = top + 6 + (index + 0.5) * row_height
            self.text(self.left + 15, center_y, label, 15, INK, True, "lm")
        self.mark(item.get("note"), top + 29, x=right + 15)

    def day_markers(self, item, day, x, y, color=PRIMARY):
        visible = [key for key in DAY_MARKER_FILES if day in item.get(key, ())]
        for index, key in enumerate(visible):
            row, column = divmod(index, 2)
            draw_day_marker(self.draw, key, x + column * 11, y + row * 11, color)

    def calendar(self, item):
        y = self.cursor
        self.text(self.left, y, item["text"], 17, bold=True)
        self.text(self.inner_right - 6, y, "‹   ›", 17, PRIMARY, bold=True, anchor="ra")
        left = self.x + 8
        step = (PHONE_WIDTH - 16) // 7
        first_row = y + 44
        for column, label in enumerate(("S", "M", "T", "W", "T", "F", "S")):
            self.text(left + step * column + step // 2, y + 34, label, 12, MUTED, anchor="mm")
        for day in range(1, item.get("days", 31) + 1):
            row, column = divmod(item["start"] + day - 1, 7)
            x = left + step * column
            cell_y = first_row + row * 48
            active = day == item.get("selected")
            if active:
                self.box(x + 1, cell_y + 1, x + step - 1, cell_y + 44, PRIMARY, 6)
            self.text(x + 4, cell_y + 15, str(day), 13, "#FFFFFF" if active else INK, bold=active)
            self.day_markers(item, day, x + 25, cell_y + 12, "#FFFFFF" if active else PRIMARY)
        self.mark(item.get("note"), first_row + 28)
        row_count = (item["start"] + item.get("days", 31) + 6) // 7
        self.cursor = first_row + 48 * row_count + 9

    def activity_calendar(self, item):
        left = self.x + 8
        column_width = (PHONE_WIDTH - 16) // 7
        row_height = 72
        first_row = self.cursor + 24
        for column, label in enumerate(("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")):
            x = left + column * column_width + column_width // 2
            self.text(x, self.cursor + 5, label, 11, MUTED, anchor="mm")

        activity = item.get("activity", {})
        for day in range(1, item["days"] + 1):
            row, column = divmod(item["start"] + day - 1, 7)
            x = left + column * column_width
            y = first_row + row * row_height
            today = day == item.get("today")
            self.box(x + 1, y + 1, x + column_width - 1, y + row_height - 2, SELECTED if today else "#F0F4FC", 7)
            if today:
                self.draw.rounded_rectangle(
                    (x + 1, y + 1, x + column_width - 1, y + row_height - 2), 7, outline=PRIMARY, width=1,
                )
            self.text(x + 5, y + 4, str(day), 13, PRIMARY if today else INK, bold=today)
            self.day_markers(item, day, x + 26, y + 4)

            entries = activity.get(str(day), [])
            for index, entry in enumerate(entries[:2]):
                line_y = y + 25 + index * 14
                self.text(x + 5, line_y, entry["text"], 12, INK, bold=True)
            if len(entries) > 2:
                self.text(x + 5, y + 54, f"+{len(entries) - 2}", 10, PRIMARY, bold=True)

        row_count = (item["start"] + item["days"] + 6) // 7
        self.mark(item.get("note"), first_row + row_height * 2)
        self.cursor = first_row + row_count * row_height + 9

    def expand_icon(self, x, y):
        self.draw.line((x - 7, y - 2, x - 7, y - 7, x - 2, y - 7), fill=PRIMARY, width=2)
        self.draw.line((x + 2, y + 7, x + 7, y + 7, x + 7, y + 2), fill=PRIMARY, width=2)

    def chart(self, item):
        y = self.cursor
        height = item.get("height", 118)
        self.box(self.left, y, self.inner_right, y + height, "#F0F4FC", 12)
        self.text(self.left + 12, y + 9, item["text"], 13, bold=True)
        if item.get("expandable"):
            self.expand_icon(self.inner_right - 20, y + 17)
        start, end, base = self.left + 28, self.inner_right - 18, y + height - 28
        plot_height = height - 61
        values = item["values"]
        ticks = item.get("ticks")
        scale_values = ticks or [value for value in values if value is not None]
        scale = max(scale_values, default=0) or 1
        if ticks:
            start = self.left + 44
        if ticks:
            for tick in ticks:
                tick_y = base - tick * plot_height / scale
                self.draw.line((start, tick_y, end, tick_y), fill=LINE, width=1)
                self.text(start - 8, tick_y, str(tick), 11, MUTED, anchor="rm")
        else:
            for offset in (0, plot_height * 25 / 57, plot_height * 50 / 57):
                self.draw.line((start, base - offset, end, base - offset), fill=LINE, width=1)
        if item.get("style") == "line":
            positions = item.get("positions", [index / max(1, len(values) - 1) for index in range(len(values))])
            points = [
                (start + (end - start) * position, base - value * plot_height / scale)
                for position, value in zip(positions, values, strict=True)
            ]
            if item.get("step"):
                points = [points[0], *[point for previous, current in zip(points, points[1:]) for point in (
                    (current[0], previous[1]), current,
                )]]
            self.draw.line(points, fill=PRIMARY, width=3, joint="curve")
            if item.get("mark_endpoint"):
                x, value_y = points[-1]
                self.draw.ellipse((x - 4, value_y - 4, x + 4, value_y + 4), fill=PRIMARY, outline=SURFACE)
        else:
            step = (end - start) / len(values)
            for index, value in enumerate(values):
                if value is None or value == 0:
                    continue
                x = start + index * step + 5
                selected = index == item.get("selected")
                self.box(x, base - value * plot_height / scale, x + step - 10, base,
                         INK if selected else PRIMARY, 4)
            for index, label in enumerate(item.get("labels", [])):
                self.text(start + (index + 0.5) * step, base + 12, label, 11, MUTED, anchor="mm")
        self.text(start, base + 6, item.get("start_label", ""), 11, MUTED)
        self.text(end, base + 6, item.get("end_label", ""), 11, MUTED, anchor="ra")
        self.mark(item.get("note"), y + 18)
        self.cursor += height + 11

    def heatmap(self, item):
        y = self.cursor
        rows = item["rows"]
        columns = len(rows[0])
        dates = item.get("dates")
        step = (self.width - 26) // columns
        height, grid_y = (23, y + 55) if dates else (min(13, step), y + 37)
        if dates is None:
            step = min(24, step)
        legend_y = grid_y + 6 + len(rows) * (height + 2)
        self.box(self.left, y, self.inner_right, legend_y + 25, "#F0F4FC", 12)
        self.text(self.left + 12, y + 10, item["text"], 13, bold=True)
        if item.get("expandable"):
            self.expand_icon(self.inner_right - 20, y + 17)
        colors = ("#E1E8F2", "#BDD1F6", "#789FDE", PRIMARY)
        if dates:
            for index, label in enumerate(("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")):
                self.text(self.left + 12 + (index + 0.5) * step, grid_y - 12, label, 12, MUTED, anchor="mm")
        for row_index, row in enumerate(rows):
            for column, value in enumerate(row):
                x = self.left + 12 + column * step
                cell_y = grid_y + row_index * (height + 2)
                day = dates[row_index][column] if dates else None
                if dates and day is None:
                    continue
                self.box(x, cell_y, x + step - 2, cell_y + height, colors[value], 2)
                if [row_index, column] == item.get("selected"):
                    self.draw.rounded_rectangle((x, cell_y, x + step - 2, cell_y + height), 2, outline=INK, width=2)
                if day is not None:
                    self.text(x + step // 2, cell_y + height // 2, str(day), 11,
                              "#FFFFFF" if value == 3 else INK, anchor="mm")
        self.text(self.left + 12, legend_y, "0 h    ·    up to 8 h", 11, MUTED)
        self.mark(item.get("note"), y + 16)
        self.cursor = legend_y + 37


    def year_heatmap(self, item):
        start, end = date.fromisoformat(item["start"]), date.fromisoformat(item["end"])
        first_weekday = item.get("first_weekday", 6)
        first_week = start - timedelta(days=(start.weekday() - first_weekday) % 7)
        weeks = (end - first_week).days // 7 + 1
        y = self.cursor
        grid_left, grid_right, grid_top = self.left + 40, self.inner_right - 12, y + 45
        week_width = (grid_right - grid_left) / weeks
        row_height = 24
        legend_y = grid_top + 7 * row_height + 9
        colors = ("#E1E8F2", "#BDD1F6", "#789FDE", PRIMARY)
        self.box(self.left, y, self.inner_right, legend_y + 22, "#F0F4FC", 12)
        self.text(self.left + 12, y + 9, item["text"], 13, bold=True)
        weekdays = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        for row in range(7):
            label = weekdays[(first_weekday + row) % 7]
            self.text(grid_left - 8, grid_top + row * row_height + 11, label, 11, MUTED, anchor="rm")
        for offset in range((end - start).days + 1):
            day = start + timedelta(days=offset)
            week, row = divmod((day - first_week).days, 7)
            x = grid_left + week * week_width
            cell_y = grid_top + row * row_height
            if day == start or day.day == 1:
                month = day.strftime("%b")
                if day.month == 1:
                    month = day.strftime("%b ’%y")
                label_x = min(x + 2, grid_right - 38)
                self.text(label_x, grid_top - 13, month, 10, MUTED, anchor="lm")
            level = item["activity"].get(day.isoformat(), 0)
            self.box(x, cell_y, x + week_width - 1, cell_y + row_height - 2, colors[level], 2)
            if day.isoformat() == item["selected"]:
                self.draw.rounded_rectangle(
                    (x, cell_y, x + week_width - 1, cell_y + row_height - 2), 2, outline=INK, width=2,
                )
        self.text(self.left + 12, legend_y, "0 h    ·    up to 8 h", 11, MUTED)
        self.mark(item.get("note"), y + 17)
        self.cursor = legend_y + 31


def render_board(board, font_dir, destination):
    screens = board["screens"]
    phone_width = board.get("phone_width", PHONE_WIDTH)
    width = len(screens) * phone_width + (len(screens) + 1) * GAP
    height = board.get("phone_height", PHONE_HEIGHT)
    image = Image.new("RGB", (width, height + 118), CANVAS)
    draw = ImageDraw.Draw(image)
    draw.text((GAP, 16), board["title"], font=get_font(font_dir, 25, True), fill=INK)
    draw.text((GAP, 54), board["subtitle"], font=get_font(font_dir, 14), fill=MUTED)
    ids = [screen["id"] for screen in screens]
    if len(ids) != len(set(ids)):
        raise ValueError(f"Duplicate screen in {board['file']}")
    for index, screen in enumerate(screens):
        x = GAP + index * (phone_width + GAP)
        Screen(image, font_dir, x, 82, screen, phone_width, height).render()
    image.save(destination / board["file"], optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, nargs="*")
    parser.add_argument("--font-dir", type=Path, default=Path("/usr/share/fonts/truetype/lato"))
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--marker-output", type=Path, help="Render the 9 px date-marker reference glyphs.")
    args = parser.parse_args()
    if not args.source and args.marker_output is None:
        parser.error("provide a mockup manifest or --marker-output")
    if args.marker_output is not None:
        args.marker_output.mkdir(parents=True, exist_ok=True)
        for key, name in DAY_MARKER_FILES.items():
            icon = Image.new("RGBA", (9, 9))
            draw_day_marker(ImageDraw.Draw(icon), key, 0, 0, PRIMARY)
            icon.save(args.marker_output / f"{name}.png", optimize=True)
    args.output.mkdir(parents=True, exist_ok=True)
    for source in args.source:
        for board in json.loads(source.read_text(encoding="utf-8")):
            render_board(board, args.font_dir, args.output)


if __name__ == "__main__":
    main()
