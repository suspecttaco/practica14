package com.simposio.practica14

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Convierte un icono de Compose (ImageVector) a Bitmap rojo, como exige MyLocationNewOverlay
private fun Painter.toRedBitmap(density: Density, sizePx: Int = 96): Bitmap {
    val imageBitmap = ImageBitmap(sizePx, sizePx)
    val painter = this
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(imageBitmap),
        Size(sizePx.toFloat(), sizePx.toFloat())
    ) {
        with(painter) {
            draw(size = size, colorFilter = ColorFilter.tint(Color.Red))
        }
    }
    return imageBitmap.asAndroidBitmap()
}

@Composable
fun MapOSMScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pinPainter = rememberVectorPainter(Icons.Filled.LocationOn)
    val arrowPainter = rememberVectorPainter(Icons.Filled.Navigation)
    var permissionGranted by remember { mutableStateOf(false) }

    // Solicitar permiso de GPS
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }

    // Verificar el permiso al iniciar la aplicacion
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.OpenTopo)
                tileProvider.clearTileCache()
                setMultiTouchControls(true)
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(25.7905, -108.9875))
            }
        },
        update = { mapView ->
            if (permissionGranted && mapView.overlays.none { it is MyLocationNewOverlay }) {
                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                // iconos rojos
                val personBitmap = pinPainter.toRedBitmap(density)
                val arrowBitmap = arrowPainter.toRedBitmap(density)
                locationOverlay.setPersonIcon(personBitmap)
                // pin
                locationOverlay.setPersonAnchor(0.5f, 1.0f)
                locationOverlay.setDirectionArrow(personBitmap, arrowBitmap)
                locationOverlay.enableMyLocation()
                locationOverlay.enableFollowLocation()

                // Obliga a la camara a viajar a la ubicacion en el primer impacto de señal
                locationOverlay.runOnFirstFix {
                    mapView.post {
                        val currentLocation = locationOverlay.myLocation

                        if (currentLocation != null) {
                            mapView.controller.setZoom(17.0)
                            mapView.controller.setCenter(currentLocation)
                        }
                    }
                }

                mapView.overlays.add(locationOverlay)
                mapView.invalidate()
            }
        }
    )
}