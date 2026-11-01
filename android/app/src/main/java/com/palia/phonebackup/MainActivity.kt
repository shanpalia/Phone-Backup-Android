package com.palia.phonebackup

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    private lateinit var page: LinearLayout
    private lateinit var pageScroll: ScrollView
    private var server: BackupServer? = null
    private var pairingCode = ""
    private var selectedTab = "Home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreGrantedFolders()
        buildHomeScreen()
        refreshFolders()
        // Android's own secure folder picker is shown on first launch.
        val prefs = getSharedPreferences("phone_backup_setup", MODE_PRIVATE)
        if (!prefs.getBoolean("folder_access_prompted", false)) {
            prefs.edit().putBoolean("folder_access_prompted", true).apply()
            page.post { if (roots.isEmpty()) pickFolder() }
        }
    }

    private fun buildHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4FAF7.toInt())
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
        }
        pageScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        root.addView(pageScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = dp(10).toFloat()
        }
        listOf("Home", "Folders", "Connect", "Settings").forEach { tab ->
            val item = TextView(this).apply {
                text = when (tab) {
                    "Home" -> "⌂\nHome"
                    "Folders" -> "▣\nFolders"
                    "Connect" -> "⌁\nConnect"
                    else -> "⚙\nSettings"
                }
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener { showTab(tab) }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav)
        setContentView(root)
        showTab("Home")
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        page.removeAllViews()
        when (tab) {
            "Home" -> buildDashboard()
            "Folders" -> buildFoldersPage()
            "Connect" -> buildConnectPage()
            else -> buildSettingsPage()
        }
        pageScroll.scrollTo(0, 0)
    }

    private fun buildDashboard() {
        addBrandHeader()
        addCard {
            addText("Your phone, safely backed up", 20f, 0xFF173D2E.toInt(), true)
            addText("Transfer selected folders to your Windows PC over the same Wi-Fi network.", 14f, 0xFF607168.toInt())
            addText(if (roots.isEmpty()) "Setup needed · Select folders to begin" else "Ready · ${roots.size} folder(s) selected", 13f, 0xFF087A50.toInt(), true)
        }
        addSectionTitle("QUICK ACTIONS")
        addActionButton("＋  Choose backup folders") { showTab("Folders") }
        addActionButton("Start Wi-Fi sharing") { showTab("Connect"); if (roots.isNotEmpty()) startSharing() }
        addSectionTitle("BACKUP OVERVIEW")
        addInfoCard("Selected folders", if (roots.isEmpty()) "No folders selected yet" else roots.joinToString("  •  ") { it.name ?: "Folder" })
        addInfoCard("Connection", if (server == null) "Not connected to your PC" else "Sharing is active")
        addSmallNote("Only folders you select are shared. Android-protected app data and some system folders are not available to ordinary apps.")
    }

    private fun buildFoldersPage() {
        addBrandHeader()
        addText("Backup folders", 23f, 0xFF173D2E.toInt(), true)
        addText("Choose the folders you want to copy. Android will open its official folder picker and ask you to grant access.", 14f, 0xFF607168.toInt())
        addActionButton("＋  Add folder") { pickFolder() }
        addInfoCard("Access granted", "")
        foldersSummary = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF29483A.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xFFFFFFFF.toInt(), 16)
        }
        page.addView(foldersSummary, matchWrap(dp(12)))
        refreshFolders()
        addSmallNote("Tip: You can add DCIM, Pictures, Movies, Download and Documents separately. Android may block protected folders.")
    }

    private fun buildConnectPage() {
        addBrandHeader()
        addText("Connect to Windows PC", 23f, 0xFF173D2E.toInt(), true)
        addText("Keep your phone and PC on the same Wi-Fi network, or connect both to your phone's hotspot.", 14f, 0xFF607168.toInt())
        addActionButton("Start sharing") { startSharing() }
        addSecondaryButton("Stop sharing") { stopSharing() }
        connectionStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF20543E.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(0xFFFFFFFF.toInt(), 16)
        }
        page.addView(connectionStatus, matchWrap(dp(14)))
        refreshConnectionStatus()
        addSmallNote("Enter the address and pairing code shown here in the Windows Phone Backup app. Keep the code private and stop sharing when finished.")
    }

    private fun buildSettingsPage() {
        addBrandHeader()
        addText("Settings & privacy", 23f, 0xFF173D2E.toInt(), true)
        addInfoCard("App permissions", "Folder access is granted securely through Android's system folder picker. You can change access by adding folders here.")
        addInfoCard("Network access", "Internet and network-state permissions are declared in AndroidManifest. Android does not show a runtime popup for these normal permissions.")
        addInfoCard("Your privacy", "Only selected folders are shared while Wi-Fi sharing is running. The pairing code protects the transfer endpoints.")
        addInfoCard("App version", "Phone Backup · By PaliaAPK HUB")
    }

    private fun addBrandHeader() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xFFE0F4E9.toInt(), 18)
        }
        val mark = TextView(this).apply {
            text = "PB"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(0xFF087A50.toInt(), 14)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(48), dp(48)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        labels.addView(makeText("Phone Backup", 20f, 0xFF087A50.toInt(), true))
        labels.addView(makeText("BY PALIAAPK HUB", 10f, 0xFF607168.toInt(), true))
        header.addView(labels)
        page.addView(header, matchWrap(dp(18)))
    }

    private fun addSectionTitle(value: String) {
        page.addView(makeText(value, 12f, 0xFF087A50.toInt(), true), matchWrap(dp(8)))
    }

    private fun addText(value: String, size: Float, color: Int, bold: Boolean = false) {
        page.addView(makeText(value, size, color, bold), matchWrap(dp(8)))
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun addCard(content: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(0xFFFFFFFF.toInt(), 18)
            content()
        }
        page.addView(card, matchWrap(dp(14)))
    }

    private fun addInfoCard(title: String, value: String) {
        addCard {
            addView(makeText(title, 14f, 0xFF087A50.toInt(), true))
            if (value.isNotBlank()) {
                val detail = makeText(value, 13f, 0xFF52645B.toInt())
                detail.setPadding(0, dp(6), 0, 0)
                addView(detail)
            }
        }
    }

    private fun addActionButton(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(0xFF087A50.toInt(), 14)
            setOnClickListener { action() }
        }
        page.addView(button, matchWrap(dp(10)))
    }

    private fun addSecondaryButton(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setOnClickListener { action() }
        }
        page.addView(button, matchWrap(dp(10)))
    }

    private fun addSmallNote(value: String) {
        val note = makeText(value, 12f, 0xFF718078.toInt())
        note.setPadding(dp(4), dp(12), dp(4), dp(12))
        page.addView(note, matchWrap())
    }

    private fun matchWrap(bottom: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = bottom }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun restoreGrantedFolders() {
        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .forEach { permission ->
                try {
                    DocumentFile.fromTreeUri(this, permission.uri)?.let { folder ->
                        if (roots.none { it.uri == folder.uri }) roots.add(folder)
                    }
                } catch (_: Exception) { }
            }
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
        } catch (_: SecurityException) { }
        val folder = DocumentFile.fromTreeUri(this, uri)
        if (folder != null && roots.none { it.uri == folder.uri }) {
            roots.add(folder)
            Toast.makeText(this, "Folder access granted: ${folder.name ?: "Selected folder"}", Toast.LENGTH_SHORT).show()
        }
        refreshFolders()
        if (selectedTab == "Home") showTab("Home")
    }

    private fun refreshFolders() {
        if (!::foldersSummary.isInitialized) return
        foldersSummary.text = if (roots.isEmpty()) {
            "No folders selected yet. Tap Add folder to grant access."
        } else {
            "Access granted to ${roots.size} folder(s):\n" + roots.joinToString("\n") { "✓  ${it.name ?: "Folder"}" }
        }
    }

    private fun refreshConnectionStatus() {
        if (!::connectionStatus.isInitialized) return
        connectionStatus.text = if (server == null) {
            "SHARING OFF\nStart sharing when you are ready to connect."
        } else {
            "SHARING ON\nAddress: http://${wifiIp()}:8765\nPort: 8765\nPairing code: $pairingCode\n\nKeep Phone Backup open while transferring."
        }
    }

    private fun startSharing() {
        if (roots.isEmpty()) {
            Toast.makeText(this, "First choose a folder and grant Android access", Toast.LENGTH_LONG).show()
            showTab("Folders")
            pickFolder()
            return
        }
        try {
            pairingCode = (100000 + SecureRandom().nextInt(900000)).toString()
            server?.stop()
            server = BackupServer(this, roots.toList(), pairingCode).also { it.start() }
            refreshConnectionStatus()
            Toast.makeText(this, "Wi-Fi sharing started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            if (::connectionStatus.isInitialized) connectionStatus.text = "Could not start sharing: ${e.message}"
        }
    }

    private fun stopSharing() {
        server?.stop()
        server = null
        if (::connectionStatus.isInitialized) refreshConnectionStatus()
        Toast.makeText(this, "Sharing stopped", Toast.LENGTH_SHORT).show()
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
