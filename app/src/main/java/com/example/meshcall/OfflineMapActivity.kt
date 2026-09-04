package com.example.meshcall

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.layer.overlay.Marker
import java.io.File
import java.io.FileOutputStream

class OfflineMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var tileCache: TileCache? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AndroidGraphicFactory.createInstance(this.application)
        
        setContentView(R.layout.activity_offline_map)
        
        mapView = findViewById(R.id.mapView)
        mapView.isClickable = true
        mapView.mapScaleBar.isVisible = true
        mapView.setBuiltInZoomControls(true)

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lon = intent.getDoubleExtra("lon", 0.0)

        findViewById<Button>(R.id.buttonClose).setOnClickListener {
            finish()
        }
        
        // Copy map file from assets to internal storage if not exists
        val mapFile = File(filesDir, "monaco.map")
        if (!mapFile.exists()) {
            assets.open("monaco.map").use { input ->
                FileOutputStream(mapFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        // Setup Mapsforge Layer
        val mapDataStore: MapDataStore = MapFile(mapFile)
        tileCache = AndroidUtil.createTileCache(
            this, "mapcache", mapView.model.displayModel.tileSize, 1f,
            mapView.model.frameBufferModel.overdrawFactor
        )
        val tileRendererLayer = TileRendererLayer(
            tileCache, mapDataStore,
            mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE
        )
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT)
        
        mapView.layerManager.layers.add(tileRendererLayer)
        
        val latLong = LatLong(lat, lon)
        mapView.setCenter(latLong)
        mapView.setZoomLevel(15)

        val drawable = resources.getDrawable(R.drawable.ic_location_pin, theme)
        val androidBitmap = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 100,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 100,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(androidBitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        
        val bitmapDrawable = android.graphics.drawable.BitmapDrawable(resources, androidBitmap)
        val mapsforgeBitmap = AndroidGraphicFactory.convertToBitmap(bitmapDrawable)
        
        val marker = Marker(latLong, mapsforgeBitmap, 0, -mapsforgeBitmap.height / 2)
        mapView.layerManager.layers.add(marker)
    }

    override fun onDestroy() {
        mapView.destroyAll()
        AndroidGraphicFactory.clearResourceMemoryCache()
        tileCache?.destroy()
        super.onDestroy()
    }
}
