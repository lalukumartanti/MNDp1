package com.maanirmala.mndplayer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private enum class LibraryMode { ALL, VIDEOS, AUDIO }
    private data class MediaEntry(
        val uri: Uri,
        val title: String,
        val mime: String?,
        val durationMs: Long,
        val sizeBytes: Long,
        val isVideo: Boolean
    )

    private lateinit var root: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var listView: ListView
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var adapter: MediaAdapter
    private var player: ExoPlayer? = null
    private var allItems: List<MediaEntry> = emptyList()
    private var shownItems: List<MediaEntry> = emptyList()
    private var mode = LibraryMode.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(14, 14, 14)
        window.navigationBarColor = Color.BLACK
        buildUi()
        handleIncomingIntent(intent)
        if (hasMediaPermission()) loadLibrary() else requestMediaPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(17, 17, 17))
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        val heading = TextView(this).apply {
            text = "MND PLAYER"
            textSize = 23f
            setTextColor(Color.rgb(235, 199, 72))
            gravity = Gravity.CENTER_VERTICAL
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(5), dp(4), dp(8))
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, -2))

        statusText = TextView(this).apply {
            text = "Your local media library"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        root.addView(statusText)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addTab(tabs, "All", LibraryMode.ALL)
        addTab(tabs, "Videos", LibraryMode.VIDEOS)
        addTab(tabs, "Audio", LibraryMode.AUDIO)
        root.addView(tabs, LinearLayout.LayoutParams(-1, dp(44)))

        searchBox = EditText(this).apply {
            hint = "Search media..."
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.rgb(38, 38, 38))
        }
        root.addView(searchBox, LinearLayout.LayoutParams(-1, dp(46)))
        searchBox.addTextChangedListener(SimpleTextWatcher { applyFilter() })

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, dp(220)))

        val libraryHeader = TextView(this).apply {
            text = "ON THIS DEVICE"
            textSize = 12f
            setTextColor(Color.rgb(235, 199, 72))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(10), dp(4), dp(6))
        }
        root.addView(libraryHeader)

        listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.rgb(48, 48, 48))
            dividerHeight = dp(1)
            setBackgroundColor(Color.rgb(17, 17, 17))
            setOnItemClickListener { _, _, position, _ ->
                shownItems.getOrNull(position)?.let { play(it) }
            }
        }
        adapter = MediaAdapter()
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addTab(parent: LinearLayout, label: String, target: LibraryMode) {
        val button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45, 40, 25))
            setOnClickListener {
                mode = target
                applyFilter()
            }
        }
        parent.addView(button, LinearLayout.LayoutParams(0, -1, 1f).apply {
            setMargins(dp(2), 0, dp(2), dp(4))
        })
    }

    private fun hasMediaPermission(): Boolean {
        val permissions = neededPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun neededPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun requestMediaPermission() {
        ActivityCompat.requestPermissions(this, neededPermissions(), REQUEST_MEDIA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA) {
            if (hasMediaPermission()) loadLibrary()
            else statusText.text = "Allow media access to list local videos and audio."
        }
    }

    private fun loadLibrary() {
        val items = mutableListOf<MediaEntry>()
        queryCollection(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true, items
        )
        queryCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            false, items
        )
        allItems = items.sortedBy { it.title.lowercase(Locale.getDefault()) }
        statusText.text = "${allItems.size} media items found • Stored on your device"
        applyFilter()
        // Play a shared/opened file if it wasn't part of the MediaStore listing.
        val incoming = incomingUri
        if (incoming != null && allItems.none { it.uri == incoming }) {
            play(MediaEntry(incoming, incoming.lastPathSegment ?: "Opened media", contentType(incoming), 0, 0,
                contentType(incoming)?.startsWith("video/") == true))
            incomingUri = null
        }
    }

    private fun queryCollection(collection: Uri, video: Boolean, destination: MutableList<MediaEntry>) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE
        ) + if (video) arrayOf(MediaStore.Video.VideoColumns.DURATION) else arrayOf(MediaStore.Audio.AudioColumns.DURATION)

        val selection = if (video) null else "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} = 0"
        val sortOrder = "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(collection, projection, selection, null, sortOrder)
            cursor?.use { c ->
                val idIndex = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val titleIndex = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val durationIndex = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    destination.add(
                        MediaEntry(
                            uri,
                            c.getString(titleIndex) ?: "Unknown media",
                            if (mimeIndex >= 0) c.getString(mimeIndex) else null,
                            if (durationIndex >= 0) c.getLong(durationIndex) else 0L,
                            if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L,
                            video
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission may have been denied or restricted by the system.
        } catch (_: Exception) {
            // Keep the library usable if one media collection is unavailable.
        }
    }

    private fun applyFilter() {
        val query = if (::searchBox.isInitialized) searchBox.text.toString().trim().lowercase(Locale.getDefault()) else ""
        shownItems = allItems.filter {
            (mode == LibraryMode.ALL ||
                (mode == LibraryMode.VIDEOS && it.isVideo) ||
                (mode == LibraryMode.AUDIO && !it.isVideo)) &&
                it.title.lowercase(Locale.getDefault()).contains(query)
        }
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        if (::statusText.isInitialized) statusText.text =
            "${shownItems.size} shown • ${allItems.size} found on device"
    }

    private fun play(entry: MediaEntry) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build().also { playerView.player = it }
            }
            player?.setMediaItem(MediaItem.fromUri(entry.uri))
            player?.prepare()
            player?.playWhenReady = true
            statusText.text = "Playing: ${entry.title}"
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play this file: ${e.localizedMessage ?: "unsupported format"}", Toast.LENGTH_LONG).show()
        }
    }

    private var incomingUri: Uri? = null

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
        if (uri != null) {
            incomingUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            if (::playerView.isInitialized && hasMediaPermission()) {
                play(MediaEntry(uri, uri.lastPathSegment ?: "Shared media", contentType(uri), 0, 0,
                    contentType(uri)?.startsWith("video/") == true))
            }
        }
    }

    private fun contentType(uri: Uri): String? = try {
        contentResolver.getType(uri)
    } catch (_: Exception) { null }

    private inner class MediaAdapter : android.widget.BaseAdapter() {
        override fun getCount() = shownItems.size
        override fun getItem(position: Int) = shownItems[position]
        override fun getItemId(position: Int) = position
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(8), dp(10))
                setBackgroundColor(Color.rgb(17, 17, 17))
            }
            row.removeAllViews()
            val item = getItem(position)
            val title = TextView(this@MainActivity).apply {
                text = item.title
                textSize = 15f
                setTextColor(Color.WHITE)
                maxLines = 2
            }
            val detail = TextView(this@MainActivity).apply {
                val type = if (item.isVideo) "VIDEO" else "AUDIO"
                text = "$type  •  ${formatDuration(item.durationMs)}  •  ${formatSize(item.sizeBytes)}"
                textSize = 11f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(3), 0, 0)
            }
            row.addView(title)
            row.addView(detail)
            return row
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "Size unknown"
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
        else -> "${bytes / 1024} KB"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        if (player == null) player = ExoPlayer.Builder(this).build().also { playerView.player = it }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object { private const val REQUEST_MEDIA = 2104 }
}

private class SimpleTextWatcher(val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed() }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
