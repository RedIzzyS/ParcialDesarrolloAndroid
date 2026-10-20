package com.example.parcialdesarrolloandroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.parcialdesarrolloandroid.ui.theme.ParcialDesarrolloAndroidTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AplicacionConSensores()
        }
    }
}

@Composable
fun AplicacionConSensores() {
    var modoOscuro by remember { mutableStateOf(false) }

    SensorLuz { luz ->
        modoOscuro = luz < 30f
    }

    ParcialDesarrolloAndroidTheme(
        darkTheme = modoOscuro
    ) {
        RecorridoApp()
    }
}

@Composable
fun SensorLuz(onCambio: (Float) -> Unit) {
    val contexto = LocalContext.current

    DisposableEffect(Unit) {
        val administrador = contexto.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

        val sensor = administrador.getDefaultSensor(
            Sensor.TYPE_LIGHT
        )

        val listener = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                onCambio(evento.values[0])
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                precision: Int
            ) {
            }
        }

        if (sensor != null) {
            administrador.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        onDispose {
            administrador.unregisterListener(listener)
        }
    }
}

@Composable
fun SensorPasos(
    activo: Boolean,
    permisoConcedido: Boolean,
    onPasos: (Int) -> Unit
) {
    val contexto = LocalContext.current

    DisposableEffect(activo, permisoConcedido) {
        val administrador = contexto.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

        val sensor = administrador.getDefaultSensor(
            Sensor.TYPE_STEP_COUNTER
        )

        var pasosIniciales: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                val totalPasos = evento.values[0]

                if (pasosIniciales == null) {
                    pasosIniciales = totalPasos
                }

                onPasos(
                    (totalPasos - pasosIniciales!!)
                        .toInt()
                        .coerceAtLeast(0)
                )
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                precision: Int
            ) {
            }
        }

        if (activo && permisoConcedido && sensor != null) {
            administrador.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        onDispose {
            administrador.unregisterListener(listener)
        }
    }
}

@Composable
fun RecorridoApp() {
    val context = LocalContext.current

    val clienteUbicacion = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var recorridoActivo by remember { mutableStateOf(false) }
    var ubicacionActual by remember { mutableStateOf<LatLng?>(null) }
    var distanciaMetros by remember { mutableFloatStateOf(0f) }
    var pasos by remember { mutableStateOf(0) }

    var mensaje by remember {
        mutableStateOf("Inicia un recorrido para comenzar")
    }

    val ruta = remember { mutableStateListOf<LatLng>() }

    val tienePermisoUbicacion =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    val tienePermisoCamara =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    val tienePermisoActividad =
        android.os.Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

    SensorPasos(
        activo = recorridoActivo,
        permisoConcedido = tienePermisoActividad
    ) {
        pasos = it
    }

    val solicitudPermisos =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permisos ->

            val gpsConcedido =
                permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

            val camaraConcedida =
                permisos[Manifest.permission.CAMERA] == true ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

            if (gpsConcedido) {
                recorridoActivo = true

                mensaje = if (camaraConcedida) {
                    "Recorrido iniciado"
                } else {
                    "Para tomar fotos recuerda activar los permisos"
                }
            } else {
                mensaje = "Necesitas permitir la ubicación para comenzar tu recorrido"
            }
        }

    val posicionInicial = LatLng(4.7110, -74.0721)

    val estadoCamara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            posicionInicial,
            15f
        )
    }

    LaunchedEffect(ubicacionActual) {
        ubicacionActual?.let {
            estadoCamara.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory
                    .newLatLngZoom(it, 17f),
                durationMs = 700
            )
        }
    }

    DisposableEffect(recorridoActivo) {
        var callback: LocationCallback? = null

        if (recorridoActivo) {
            val tienePermiso =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

            if (tienePermiso) {
                val solicitudUbicacion =
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        3000L
                    )
                        .setMinUpdateDistanceMeters(2f)
                        .build()

                callback = object : LocationCallback() {
                    override fun onLocationResult(
                        resultado: LocationResult
                    ) {
                        resultado.lastLocation?.let { ubicacion ->
                            val nuevoPunto = LatLng(
                                ubicacion.latitude,
                                ubicacion.longitude
                            )

                            ubicacionActual = nuevoPunto

                            val anterior = ruta.lastOrNull()

                            if (anterior == null) {
                                ruta.add(nuevoPunto)
                            } else {
                                val calculo = FloatArray(1)

                                Location.distanceBetween(
                                    anterior.latitude,
                                    anterior.longitude,
                                    nuevoPunto.latitude,
                                    nuevoPunto.longitude,
                                    calculo
                                )

                                if (calculo[0] in 2f..100f) {
                                    distanciaMetros += calculo[0]
                                    ruta.add(nuevoPunto)
                                }
                            }
                        }
                    }
                }

                try {
                    clienteUbicacion.requestLocationUpdates(
                        solicitudUbicacion,
                        callback!!,
                        Looper.getMainLooper()
                    )
                } catch (_: SecurityException) {
                    mensaje = "No se pudo acceder al GPS"
                }
            }
        }

        onDispose {
            callback?.let {
                clienteUbicacion.removeLocationUpdates(it)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {
            Text(
                text = "Recorrido",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaDato(
                    titulo = "Distancia",
                    valor = "%.2f km".format(
                        java.util.Locale.US,
                        distanciaMetros / 1000f
                    ),
                    modifier = Modifier.weight(1f)
                )

                TarjetaDato(
                    titulo = "Pasos",
                    valor = pasos.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tu ubicación",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cameraPositionState = estadoCamara,
                properties = MapProperties(
                    isMyLocationEnabled = tienePermisoUbicacion
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = true,
                    zoomControlsEnabled = true
                )
            ) {
                if (ruta.size >= 2) {
                    Polyline(
                        points = ruta.toList(),
                        color = Color(0xFF1565C0),
                        width = 8f
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (recorridoActivo) {
                        recorridoActivo = false
                        mensaje = "Recorrido finalizado."
                    } else {
                        solicitudPermisos.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACTIVITY_RECOGNITION
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recorridoActivo) {
                        Color(0xFFB3261E)
                    } else {
                        Color(0xFF176B4D)
                    }
                )
            ) {
                Text(
                    text = if (recorridoActivo) {
                        "Finalizar recorrido"
                    } else {
                        "Iniciar recorrido"
                    }
                )
            }
        }
    }
}

@Composable
fun TarjetaDato(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = valor,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
