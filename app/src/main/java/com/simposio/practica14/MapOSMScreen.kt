package com.simposio.practica14

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.simposio.practica14.data.AppDatabase
import com.simposio.practica14.data.MarkerEntity
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Convierte un icono de Compose a un Bitmap rojo
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

    // Obtiene los marcadores guardados en Room
    val dao = remember { AppDatabase.getInstance(context).markerDao() }
    val markers by dao.getAll().collectAsState(initial = emptyList())
    val currentMarkers by rememberUpdatedState(markers)
    val scope = rememberCoroutineScope()
    var selectedMarker by remember { mutableStateOf<MarkerEntity?>(null) }

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

                // Un toque prolongado en el mapa guarda un marcador nuevo
                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        scope.launch {
                            dao.insert(
                                MarkerEntity(
                                    latitude = p.latitude,
                                    longitude = p.longitude,
                                    title = "Marcador ${currentMarkers.size + 1}"
                                )
                            )
                        }
                        return true
                    }
                }))
            }
        },
        update = { mapView ->
            // Dibuja de nuevo los marcadores guardados
            InfoWindow.closeAllInfoWindowsOn(mapView)
            mapView.overlays.removeAll { it is Marker }
            markers.forEach { marker ->
                mapView.overlays.add(object : Marker(mapView) {
                    // Un doble toque sobre el marcador abre el dialogo de edicion
                    override fun onDoubleTap(event: MotionEvent, mapView: MapView): Boolean {
                        val touched = hitTest(event, mapView)
                        if (touched) selectedMarker = marker
                        return touched
                    }
                }.apply {
                    position = GeoPoint(marker.latitude, marker.longitude)
                    title = marker.title
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            mapView.invalidate()

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

    selectedMarker?.let { marker ->
        var title by remember(marker.id) { mutableStateOf(marker.title) }
        AlertDialog(
            onDismissRequest = { selectedMarker = null },
            title = { Text("Editar marcador") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titulo") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        scope.launch { dao.update(marker.copy(title = title.trim())) }
                        selectedMarker = null
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            scope.launch { dao.delete(marker) }
                            selectedMarker = null
                        }
                    ) { Text("Eliminar") }
                    TextButton(onClick = { selectedMarker = null }) { Text("Cancelar") }
                }
            }
        )
    }
}
