package com.palia.phonebackup

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {
    private val folderPickerRequest = 4102
    private val roots = mutableListOf<DocumentFile>()
    private lateinit var foldersSummary: TextView
    private lateinit var connectionStatus: TextView
    private var server: BackupServer? = null
    private var pairingCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHomeScreen()
        refreshFolders()
    }

    private fun buildHomeScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
            setBackgroundColor(0xFFF3FBF7.toInt())
        }
        val scroll = ScrollView(this).apply { addView(page) }
        val brand = TextView(this).apply {
            text = "☁  Phone Backup"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF087A50.toInt())
            gravity = Gravity.CENTER
        }
        val tagline = TextView(this).apply {
            text = "Wi-Fi File Transfer & Full Backup\nBy PaliaAPK HUB"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF52645B.toInt())
            setPadding(0, dp(8), 0, dp(22))
        }
        val intro = TextView(this).apply {
            text = "Allow access to the folders you want to back up. Android will show its own secure folder picker; you choose exactly what Phone Backup can read."
            textSize = 15f
            setTextColor(0xFF24352D.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val permissionTitle = sectionTitle("1. Folder permissions")
        val permissionHelp = TextView(this).apply {
            text = "No broad storage permission is requested. Tap Add folder and grant access in Android's system screen. You can add several folders, such as DCIM, Pictures, Movies, Download, Documents and Android/media (if Android allows it)."
            textSize = 13f
            setTextColor(0xFF52645B.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        val addFolder = actionButton("＋  Add folder") { pickFolder() }
        foldersSummary = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF24352D.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val connectionTitle = sectionTitle("2. Connect to Windows PC")
        val connectionHelp = TextView(this).apply {
            text = "Connect the phone and PC to the same Wi-Fi or your phone's hotspot. Then start sharing and enter the address and pairing code in the Windows Phone Backup app."
            textSize = 13f
            setTextColor(0xFF52645B.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        val start = actionButton("Start sharing") { startSharing() }
        val stop = secondaryButton("Stop sharing") { stopSharing() }
        connectionStatus = TextView(this).apply {
            text = "Sharing is stopped"
            textSize = 14f
            setTextColor(0xFF145B43.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val note = TextView(this).apply {
            text = "Privacy note: only the folders you select are shared. Keep the pairing code private and stop sharing when your backup is finished. Android-protected app data and some system folders cannot be accessed by ordinary apps."
            textSize = 12f
            setTextColor(0xFF65766D.toInt())
            setPadding(dp(4), dp(18), dp(4), 0)
        }
        listOf(brand, tagline, intro, permissionTitle, permissionHelp, addFolder, foldersSummary,
            connectionTitle, connectionHelp, start, stop, connectionStatus, note).forEach {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(10)
            page.addView(it, lp)
        }
        setContentView(scroll)
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF087A50.toInt())
        setPadding(0, dp(20), 0, dp(2))
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { action() }
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, folderPickerRequest)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Android folder picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Android system document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != folderPickerRequest || resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val grantedFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, grantedFlags)
        } catch (_: SecurityException) {
            // Some document providers return a temporary grant only; use it for this app session.
        }
        val folder = DocumentFile.fromTreeUri(this, uri)
        if (folder != null && roots.none { it.uri == folder.uri }) {
            roots.add(folder)
            Toast.makeText(this, "Folder access granted: ${folder.name ?: "Selected folder"}", Toast.LENGTH_SHORT).show()
        }
        refreshFolders()
    }

    private fun refreshFolders() {
        foldersSummary.text = if (roots.isEmpty()) {
            "No folders selected yet. Add at least one folder to continue."
        } else {
            "Access granted to ${roots.size} folder(s):\n" + roots.joinToString("\n") { "✓ ${it.name ?: "Folder"}" }
        }
    }

    private fun startSharing() {
        if (roots.isEmpty()) {
            Toast.makeText(this, "First tap Add folder and grant access", Toast.LENGTH_LONG).show()
            pickFolder()
            return
        }
        try {
            pairingCode = (100000 + SecureRandom().nextInt(900000)).toString()
            server?.stop()
            server = BackupServer(this, roots.toList(), pairingCode).also { it.start() }
            val ip = wifiIp()
            connectionStatus.text = "SHARING ON\nAddress: http://$ip:8765\nPort: 8765\nPairing code: $pairingCode\n\nKeep Phone Backup open while transferring."
        } catch (e: Exception) {
            connectionStatus.text = "Could not start sharing: ${e.message}"
        }
    }

    private fun stopSharing() {
        server?.stop()
        server = null
        connectionStatus.text = "Sharing is stopped"
    }

    private fun wifiIp(): String {
        val candidates = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "Check Wi-Fi connection"
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    candidates.add(address.hostAddress ?: "")
                }
            }
        }
        return candidates.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
            ?: candidates.firstOrNull()
            ?: "Check Wi-Fi connection"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }
}
