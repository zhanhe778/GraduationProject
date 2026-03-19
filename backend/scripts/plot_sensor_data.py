from __future__ import annotations

import math
import random
from datetime import datetime, timedelta
from pathlib import Path

import matplotlib.pyplot as plt

BASE_DATE = datetime(2026, 3, 1)
LOGICAL_INTERVAL_MINUTES = 5
TOTAL_SLOTS = (24 * 60) // LOGICAL_INTERVAL_MINUTES

# Use a fixed seed so the generated plot is reproducible.
rng = random.Random(20260319)
HUMI_PHASE_1 = rng.random() * 2 * math.pi
HUMI_PHASE_2 = rng.random() * 2 * math.pi
LIGHT_PHASE_1 = rng.random() * 2 * math.pi
LIGHT_PHASE_2 = rng.random() * 2 * math.pi


def get_day_progress(dt: datetime) -> float:
    minutes = dt.hour * 60.0 + dt.minute + dt.second / 60.0
    return minutes / (24.0 * 60.0)


def generate_temperature(dt: datetime) -> float:
    day_progress = get_day_progress(dt)
    # Smoother single-cycle curve, constrained to 18-35 C.
    theta = 2 * math.pi * (day_progress - 1.0 / 3.0)
    value = 26.5 + 8.5 * math.sin(theta)
    value = max(18.0, min(35.0, value))
    return round(value, 1)


def generate_humidity(dt: datetime) -> float:
    day_progress = get_day_progress(dt)
    # Range 30-85 with an extremum near 14:00 (daily low, consistent with daytime drying).
    daily_cycle = 57.5 - 27.5 * math.sin(2 * math.pi * (day_progress - 1.0 / 3.0))
    smooth_fluctuation = (
        0.8 * math.sin(2 * math.pi * 1.5 * day_progress + HUMI_PHASE_1)
        + 0.5 * math.sin(2 * math.pi * 2.5 * day_progress + HUMI_PHASE_2)
    )
    value = daily_cycle + smooth_fluctuation
    value = max(30.0, min(85.0, value))
    return round(value, 1)


def generate_light(dt: datetime) -> float:
    day_progress = get_day_progress(dt)
    sunrise = 6.0 / 24.0
    sunset = 19.5 / 24.0
    if day_progress <= sunrise or day_progress >= sunset:
        return 0.0

    daylight_progress = (day_progress - sunrise) / (sunset - sunrise)
    envelope = math.sin(math.pi * daylight_progress)

    max_lux = 50000.0
    base = max_lux * envelope
    smooth_fluctuation = (
        180.0 * math.sin(2 * math.pi * 3.0 * day_progress + LIGHT_PHASE_1)
        + 90.0 * math.sin(2 * math.pi * 5.0 * day_progress + LIGHT_PHASE_2)
    )
    value = base + smooth_fluctuation * envelope
    return round(max(0.0, value), 0)


def main() -> None:
    timestamps = [BASE_DATE + timedelta(minutes=i * LOGICAL_INTERVAL_MINUTES) for i in range(TOTAL_SLOTS)]
    temperature = [generate_temperature(t) for t in timestamps]
    humidity = [generate_humidity(t) for t in timestamps]
    light = [generate_light(t) for t in timestamps]

    output_dir = Path(__file__).resolve().parents[1] / "plots"
    output_dir.mkdir(parents=True, exist_ok=True)

    fig, axes = plt.subplots(3, 1, figsize=(14, 10), sharex=True)
    fig.suptitle("Greenhouse Simulated Sensor Curves (24h, 5-min interval)", fontsize=14)

    axes[0].plot(timestamps, temperature, color="#d9480f", linewidth=1.8)
    axes[0].set_ylabel("Temperature (C)")
    axes[0].grid(alpha=0.25)

    axes[1].plot(timestamps, humidity, color="#1d4ed8", linewidth=1.8)
    axes[1].set_ylabel("Humidity (%)")
    axes[1].grid(alpha=0.25)

    axes[2].plot(timestamps, light, color="#ca8a04", linewidth=1.6)
    axes[2].set_ylabel("Light (Lux)")
    axes[2].set_xlabel("Time")
    axes[2].grid(alpha=0.25)

    for ax in axes:
        ax.margins(x=0)

    fig.autofmt_xdate()
    fig.tight_layout(rect=[0, 0.03, 1, 0.97])

    combined_png = output_dir / "sensor_curves_24h.png"
    fig.savefig(combined_png, dpi=150)
    plt.close(fig)

    # Also export single charts for easier reporting.
    for name, values, color, ylabel in [
        ("temperature_24h.png", temperature, "#d9480f", "Temperature (C)"),
        ("humidity_24h.png", humidity, "#1d4ed8", "Humidity (%)"),
        ("light_24h.png", light, "#ca8a04", "Light (Lux)"),
    ]:
        single_fig, ax = plt.subplots(figsize=(14, 3.5))
        ax.plot(timestamps, values, color=color, linewidth=1.8)
        ax.set_ylabel(ylabel)
        ax.set_xlabel("Time")
        ax.grid(alpha=0.25)
        ax.margins(x=0)
        single_fig.autofmt_xdate()
        single_fig.tight_layout()
        single_fig.savefig(output_dir / name, dpi=150)
        plt.close(single_fig)

    print(f"Generated: {combined_png}")
    print(f"Generated: {output_dir / 'temperature_24h.png'}")
    print(f"Generated: {output_dir / 'humidity_24h.png'}")
    print(f"Generated: {output_dir / 'light_24h.png'}")


if __name__ == "__main__":
    main()
