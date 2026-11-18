package com.mashimurelay.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.net.NetworkInterface
import java.util.Collections

/**
 * Modern Portrait CaptureActivity for Simurelay Android QR Scanner.
 * Implements the sleek Workspace Simurelay aesthetic with:
 * - Center-aligned header and authentic @drawable/ic_qr_scan icon.
 * - Single outer corner bracket frame outside the camera preview window.
 * - Dynamic device telemetry (Device Model & Local IP).
 * - Flashlight torch toggle control.
 * - Scan QR directly from Photo Gallery.
 */
class PortraitCaptureActivity : CaptureActivity() {

    private var barcodeScannerView: DecoratedBarcodeView? = null
    private var isTorchOn = false

    companion object {
        private const val REQUEST_PICK_QR_IMAGE = 2001
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_modern_qr_scanner)
        val scanner = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
        barcodeScannerView = scanner
        return scanner
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sembunyikan status text & viewfinder bawaan ZXing agar menggunakan bingkai kustom Simurelay
        try {
            barcodeScannerView?.statusView?.visibility = View.GONE
            barcodeScannerView?.viewFinder?.visibility = View.GONE
        } catch (_: Exception) {}

        // Telemetri Perangkat & Jaringan
        setupDeviceTelemetry()

        // Tombol Senter (Flashlight Toggle)
        val btnToggleTorch = findViewById<View>(R.id.btnToggleTorch)
        val ivTorchIcon = findViewById<ImageView>(R.id.ivTorchIcon)
        val tvTorchText = findViewById<TextView>(R.id.tvTorchText)

        btnToggleTorch?.setOnClickListener {
            isTorchOn = !isTorchOn
            if (isTorchOn) {
                barcodeScannerView?.setTorchOn()
                ivTorchIcon?.setColorFilter(Color.parseColor("#38bdf8"))
                tvTorchText?.setTextColor(Color.parseColor("#38bdf8"))
            } else {
                barcodeScannerView?.setTorchOff()
                ivTorchIcon?.setColorFilter(Color.parseColor("#f4f4f5"))
                tvTorchText?.setTextColor(Color.parseColor("#f4f4f5"))
            }
        }

        // Tombol Pilih dari Galeri
        val btnPickFromGallery = findViewById<View>(R.id.btnPickFromGallery)
        btnPickFromGallery?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Pilih Gambar QR"), REQUEST_PICK_QR_IMAGE)
        }
    }

    private fun setupDeviceTelemetry() {
        val tvStatusDeviceInfo = findViewById<TextView>(R.id.tvStatusDeviceInfo) ?: return
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = Build.MODEL
        val ip = getDeviceLocalIp() ?: "Wi-Fi Terhubung"
        tvStatusDeviceInfo.text = "$manufacturer $model • $ip"
    }

    private fun getDeviceLocalIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_QR_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            decodeQrFromUri(uri)
        }
    }

    private fun decodeQrFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    val qrResult = decodeQrFromBitmap(bitmap)
                    if (qrResult != null) {
                        val resultIntent = Intent().apply {
                            putExtra(com.google.zxing.client.android.Intents.Scan.RESULT, qrResult)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                        return
                    }
                }
            }
            Toast.makeText(this, "Tidak ada kode QR yang valid pada gambar", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memproses gambar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (_: Exception) {
            null
        }
    }
}
