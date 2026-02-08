# Speed Sensor Test

Simple Android app to test IMU-based speed estimation. Displays raw sensor data and estimated speed for validation before using in navigation apps.

## Features

- Real-time accelerometer readings (X, Y, Z + magnitude)
- Real-time gyroscope readings (X, Y, Z)
- Linear acceleration (gravity removed by Android sensor fusion)
- Speed estimation from integrating linear acceleration
- Calibration to zero out sensor bias when stationary
- Zero Velocity Update (ZVU) when low acceleration detected
- Low-pass filtering for smoother readings

## Usage

1. Mount phone stably in car (flat orientation preferred)
2. While stationary, tap **Calibrate** to zero out sensor bias
3. Start driving
4. Compare estimated speed vs actual speedometer
5. Tap **Reset Speed** to restart integration

## How Speed Estimation Works

1. Android's `TYPE_LINEAR_ACCELERATION` sensor provides acceleration with gravity removed
2. Apply calibration offset (zeroed when stationary)
3. Low-pass filter to reduce noise
4. Integrate acceleration over time → velocity
5. When acceleration magnitude is very low, assume stationary and decay velocity toward zero (ZVU)
6. Calculate total speed: √(vx² + vy² + vz²)

## Limitations

- **Drift**: Integration accumulates error over time
- **Orientation**: Works best when phone is mounted stably
- **Noise**: Smartphone sensors are noisy; expect ±5-10 km/h error
- **No absolute reference**: Cannot correct drift without GPS

## Build

```bash
./gradlew assembleDebug
```

APK will be in `app/build/outputs/apk/debug/`

## Requirements

- Android 8.0+ (API 26)
- Accelerometer and gyroscope sensors
