"""Run the Use or Lose command-line application as ``python -m fedleave``."""

from .cli import app


def main() -> None:
    """Invoke the Typer application entry point."""
    app()


if __name__ == "__main__":
    main()
