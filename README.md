# MINI-CEX Android

Cliente Android offline-first para evaluaciones clínicas MINI-CEX. Usa Kotlin, Room, Retrofit, MVVM y una cola persistente para sincronización bidireccional.

## Configuración local

1. Abre el proyecto en Android Studio.
2. Copia las variables de `local.properties.example` a `local.properties`.
3. Sustituye el host de ejemplo por el endpoint de tu entorno local o desplegado.
4. Nunca confirmes `local.properties`, llaves de firma o archivos de servicios.

```properties
MINICEX_API_BASE_URL=https://api.example.com/minicex/api/
MINICEX_API_HOST=api.example.com
```

La URL base debe terminar en `/` para que Retrofit pueda construir las rutas correctamente.

## Compilar y probar

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## Sincronización

La implementación completa permanece en:

- `data/repository/SyncRepository.kt`
- `data/local/entity/SyncQueueEntity.kt`
- `data/local/dao/SyncQueueDao.kt`
- `data/remote/dto/SyncDtos.kt`

Las altas, actualizaciones y eliminaciones se guardan primero en Room. El cliente envía acciones pendientes a `sync/process_queue`, marca las confirmadas y aplica acciones remotas de usuarios, alumnos y evaluaciones. La sincronización se activa al recuperar conectividad, al iniciar sesión y periódicamente cada cinco minutos.

No se incluyen usuarios ni contraseñas semilla. El primer acceso debe validarse contra el servidor; después el usuario autenticado puede conservar acceso offline según la lógica existente.
