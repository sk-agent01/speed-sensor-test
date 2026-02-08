package com.example.speedsensortest

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var linearAccelSensor: Sensor? = null

    private lateinit var speedText: TextView
    private lateinit var accelText: TextView
    private lateinit var accelMagText: TextView
    private lateinit var gyroText: TextView
    private lateinit var linearAccelText: TextView
    private lateinit var statusText: TextView

    // Speed estimation
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private var lastTimestamp: Long = 0

    // Calibration offsets
    private var offsetX = 0f
    private var offsetY = 0f
    private var offsetZ = 0f
    private var isCalibrating = false
    private var calibrationSamples = mutableListOf<FloatArray>()

    // Low-pass filter for acceleration (stronger filtering)
    private var filteredAccelX = 0f
    private var filteredAccelY = 0f
    private var filteredAccelZ = 0f
    private val accelAlpha = 0.1f  // Lower = smoother

    // Speed smoothing - moving average buffer
    private val speedBufferSize = 20
    private val speedBuffer = DoubleArray(speedBufferSize)
    private var speedBufferIndex = 0
    private var speedBufferFilled = false
    
    // Additional EMA for display smoothing
    private var smoothedSpeed = 0.0
    private val speedAlpha = 0.15  // Lower = smoother display

    // Zero velocity update threshold
    private val zeroVelocityThreshold = 0.12  // m/s²
    private val velocityDecay = 0.95  // Stronger decay when stationary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speedText = findViewById(R.id.speedText)
        accelText = findViewById(R.id.accelText)
        accelMagText = findViewById(R.id.accelMagText)
        gyroText = findViewById(R.id.gyroText)
        linearAccelText = findViewById(R.id.linearAccelText)
        statusText = findViewById(R.id.statusText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            resetSpeed()
        }

        findViewById<Button>(R.id.calibrateButton).setOnClickListener {
            startCalibration()
        }

        // Check sensor availability
        val status = StringBuilder()
        if (accelerometer == null) status.append("No accelerometer! ")
        if (gyroscope == null) status.append("No gyroscope! ")
        if (linearAccelSensor == null) status.append("No linear accel! ")
        if (status.isEmpty()) {
            statusText.text = "Sensors OK. Mount phone flat, then Calibrate."
        } else {
            statusText.text = status.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val mag = sqrt(x * x + y * y + z * z)
                accelText.text = String.format("X:%+.2f Y:%+.2f Z:%+.2f", x, y, z)
                accelMagText.text = String.format("Mag: %.2f", mag)
            }

            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                gyroText.text = String.format("X:%+.3f Y:%+.3f Z:%+.3f", x, y, z)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                handleLinearAcceleration(event)
            }
        }
    }

    private fun handleLinearAcceleration(event: SensorEvent) {
        val rawX = event.values[0] - offsetX
        val rawY = event.values[1] - offsetY
        val rawZ = event.values[2] - offsetZ

        // Low-pass filter on acceleration
        filteredAccelX = accelAlpha * rawX + (1 - accelAlpha) * filteredAccelX
        filteredAccelY = accelAlpha * rawY + (1 - accelAlpha) * filteredAccelY
        filteredAccelZ = accelAlpha * rawZ + (1 - accelAlpha) * filteredAccelZ

        linearAccelText.text = String.format(
            "X:%+.3f Y:%+.3f Z:%+.3f",
            filteredAccelX, filteredAccelY, filteredAccelZ
        )

        // Calibration collection
        if (isCalibrating) {
            calibrationSamples.add(floatArrayOf(event.values[0], event.values[1], event.values[2]))
            if (calibrationSamples.size >= 100) {
                finishCalibration()
            }
            return
        }

        // Time delta
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }
        val dt = (event.timestamp - lastTimestamp) / 1_000_000_000.0
        lastTimestamp = event.timestamp

        if (dt <= 0 || dt > 0.5) return

        val accelMagnitude = sqrt(
            filteredAccelX * filteredAccelX + 
            filteredAccelY * filteredAccelY + 
            filteredAccelZ * filteredAccelZ
        )

        // Zero velocity update
        if (accelMagnitude < zeroVelocityThreshold) {
            velocityX *= velocityDecay
            velocityY *= velocityDecay
            velocityZ *= velocityDecay
            
            if (abs(velocityX) < 0.005) velocityX = 0.0
            if (abs(velocityY) < 0.005) velocityY = 0.0
            if (abs(velocityZ) < 0.005) velocityZ = 0.0
        } else {
            velocityX += filteredAccelX * dt
            velocityY += filteredAccelY * dt
            velocityZ += filteredAccelZ * dt
        }

        // Raw speed
        val rawSpeedMs = sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ)
        val rawSpeedKmh = rawSpeedMs * 3.6

        // Add to moving average buffer
        speedBuffer[speedBufferIndex] = rawSpeedKmh
        speedBufferIndex = (speedBufferIndex + 1) % speedBufferSize
        if (speedBufferIndex == 0) speedBufferFilled = true

        // Calculate moving average
        val samplesToUse = if (speedBufferFilled) speedBufferSize else speedBufferIndex.coerceAtLeast(1)
        val avgSpeed = speedBuffer.take(samplesToUse).average()

        // Apply additional EMA smoothing for display
        smoothedSpeed = speedAlpha * avgSpeed + (1 - speedAlpha) * smoothedSpeed

        // Clamp and display
        val displaySpeed = smoothedSpeed.coerceIn(0.0, 250.0)
        speedText.text = String.format("%.1f", displaySpeed)
    }

    private fun resetSpeed() {
        velocityX = 0.0
        velocityY = 0.0
        velocityZ = 0.0
        lastTimestamp = 0
        filteredAccelX = 0f
        filteredAccelY = 0f
        filteredAccelZ = 0f
        smoothedSpeed = 0.0
        speedBufferIndex = 0
        speedBufferFilled = false
        speedBuffer.fill(0.0)
        speedText.text = "0.0"
        statusText.text = "Speed reset to zero"
    }

    private fun startCalibration() {
        isCalibrating = true
        calibrationSamples.clear()
        statusText.text = "Calibrating... keep still!"
    }

    private fun finishCalibration() {
        isCalibrating = false
        
        if (calibrationSamples.isNotEmpty()) {
            offsetX = calibrationSamples.map { it[0] }.average().toFloat()
            offsetY = calibrationSamples.map { it[1] }.average().toFloat()
            offsetZ = calibrationSamples.map { it[2] }.average().toFloat()
            
            statusText.text = String.format(
                "Done! Bias: %.3f, %.3f, %.3f",
                offsetX, offsetY, offsetZ
            )
        }
        
        resetSpeed()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
