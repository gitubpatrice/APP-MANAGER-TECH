# Política de privacidad — App Manager Tech

_Traducción de la versión del 8 de octubre de 2026._ · 🇫🇷 [Français](PRIVACY.fr.md) · 🇬🇧 [English](PRIVACY.md) · 🇩🇪 [Deutsch](PRIVACY.de.md) · 🇮🇹 [Italiano](PRIVACY.it.md)

> Esta traducción la ha realizado el desarrollador con ayuda de herramientas automáticas y todavía
> no la ha revisado un hablante nativo. **En caso de discrepancia, prevalece la
> [versión francesa](PRIVACY.fr.md).**

App Manager Tech (`com.filestech.appmanager`) es un gestor de apps **totalmente local**: examina
las apps instaladas en su teléfono, en el propio teléfono, y no envía nada a ninguna parte.

## En resumen

- **Ningún dato recogido, ningún dato transmitido.** La app no declara el permiso `INTERNET`: es
  técnicamente incapaz de enviar nada a través de una red. Una comprobación automática lo verifica
  en el propio APK publicado, en cada compilación de integración continua.
- **Sin cuenta, sin registro, sin identificador.**
- **Sin publicidad, sin rastreadores, sin herramientas de analítica**, sin ningún informe de fallos
  enviado.
- **Sin copia de seguridad en la nube**: `allowBackup=false`, y tanto la base de datos como los
  ajustes quedan excluidos de las copias de seguridad automáticas de Android y de las
  transferencias de un dispositivo a otro.

## Qué datos, y dónde

La app lee lo que Android ya sabe de las apps instaladas, y guarda una copia en su **almacenamiento
privado**, al que ninguna otra app tiene acceso. Esta base de datos no está cifrada: describe sus
apps, no sus contenidos.

| Dato | De dónde procede | Durante cuánto tiempo |
|---|---|---|
| Lista de las apps: nombre, versión, tamaños, fechas de instalación, de actualización y de último uso, tienda de origen, estado (activada, hibernada) | Android (`PackageManager`, `StorageStatsManager`, `UsageStatsManager`) | Se sustituye en cada análisis |
| Rastreadores detectados, puntuación de privacidad | Calculados en el teléfono (véase más abajo) | Se recalculan cada vez que se muestran |
| Historial de los permisos concedidos a cada app — **desactivado por defecto** | Instantánea periódica, si lo activa | 90 días por defecto, ajustable de 7 a 365 |
| Historial de las instalaciones, actualizaciones y desinstalaciones, con la huella SHA-256 de cada APK y el motivo de desinstalación que usted decida indicar — **desactivado por defecto** | Avisos de Android recibidos mientras la app está en funcionamiento, si lo activa | 180 días por defecto, ajustable de 30 a 365 |
| Registro de las acciones iniciadas desde App Manager Tech — **desactivado por defecto** | Sus acciones, si lo activa | 180 días por defecto, ajustable de 30 a 365 |
| Papelera y cuarentenas | Sus acciones | Hasta que usted las vacíe o las restaure |
| Ajustes: tema, umbrales, apps ignoradas o protegidas, etiquetas, y la autorización de acceso a la carpeta de copias de seguridad que usted haya elegido | Usted | Hasta la desinstalación |

**Los rastreadores** se detectan comparando los nombres de los componentes que cada app declara a
Android con una lista integrada en App Manager Tech, extraída de la base de datos pública de
Exodus Privacy. Esta lista se actualiza con la app; nunca se descarga.

Desinstalar App Manager Tech, o borrar sus datos en los ajustes de Android, elimina todo lo
anterior. Los archivos que **usted** haya pedido guardar en otra ubicación (véase «Comunicación a
terceros») permanecen donde los puso.

El desarrollador **no tiene acceso** a estos datos y **no recibe ninguna copia** de ellos.

## Permisos solicitados, y para qué

Esta lista es **exhaustiva**: son los doce permisos declarados en el APK publicado, tal como figuran en su manifiesto. Incluye, por tanto, los que ninguna línea de nuestro código solicita, pero que una
biblioteca trajo consigo. Una comprobación automática rechaza cualquier compilación cuyo APK se
aparte de esta lista (`tools/check-manifest-permissions.py`, ejecutado en cada compilación de
integración continua).

### Declarados por la app

| Permiso | Finalidad | ¿Red? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Ver todas las apps instaladas: es la función misma de la app. | No |
| `PACKAGE_USAGE_STATS` | Acceso especial «Acceso a datos de uso», que usted mismo concede en los ajustes de Android. Proporciona los tamaños y la fecha de último uso de cada app. Si se deniega, los tamaños aparecen en 0 y las apps como «Nunca usada»; nada más deja de funcionar. | No |
| `GET_PACKAGE_SIZE` | Leer el tamaño de las apps. | No |
| `REQUEST_DELETE_PACKAGES` | Abrir la ventana de desinstalación de Android. Es Android quien pide confirmación y quien desinstala. | No |
| `REQUEST_INSTALL_PACKAGES` | Restaurar una app puesta en cuarentena: entregar el APK guardado al instalador de Android. Android exige además que usted mismo autorice a App Manager Tech a instalar apps, y pide confirmación en cada instalación. No sirve para nada más. | No |
| `KILL_BACKGROUND_PROCESSES` | Detener los procesos en segundo plano de una app, a petición suya. | No |
| `POST_NOTIFICATIONS` | Tres notificaciones opcionales: umbral de caché alcanzado, permisos modificados, fin de una cuarentena. Se solicita cuando usted activa una de ellas (Android 13 y posteriores) y puede denegarse. | No |

### Traídos por las bibliotecas utilizadas

| Permiso | Procede de | Lo que **realmente** hace aquí | ¿Red? |
|---|---|---|---|
| `WAKE_LOCK` | `androidx.work` | Se mantiene un instante mientras se ejecuta una tarea en segundo plano: análisis automático, instantánea de los permisos, purga de los historiales, recordatorio de cuarentena — las que usted haya activado. | No |
| `RECEIVE_BOOT_COMPLETED` | `androidx.work` | Volver a programar esas tareas tras un reinicio. | No |
| `FOREGROUND_SERVICE` | `androidx.work` | **Nada.** `androidx.work` lo declara para las tareas «expedited»; la app no programa ninguna. | No |
| `ACCESS_NETWORK_STATE` | `androidx.work` | **Nada.** `androidx.work` lo declara para las tareas que esperan una red; todas las de la app se programan sin condición de red. Solo permitiría saber si hay una red disponible: sin `INTERNET`, es imposible usar esa red. | No |
| `com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | Permiso **autoconcedido**, de nivel «signature»: solo una app firmada con nuestra clave puede obtenerlo. Impide que las demás apps accedan al receptor que la app registra para seguir las instalaciones. | No |

La app **nunca** solicita acceso a Internet, a la ubicación, a los contactos, a los SMS, al
calendario, al micrófono, a la cámara ni a sus archivos (`MANAGE_EXTERNAL_STORAGE`,
`READ_EXTERNAL_STORAGE`).

## Comunicación a terceros

**Ninguna.** Ningún dato se comparte, se vende ni se transmite a nadie — la app no tiene ningún
medio técnico para hacerlo.

Los únicos intercambios posibles son los que **usted** inicia, y permanecen en su teléfono:

- **Exportación (JSON, CSV, PDF)**: un informe sobre sus apps, escrito en la ubicación que usted
  elija en el selector de archivos de Android. Revela qué apps están instaladas en su teléfono; lo
  que ocurra después con el archivo ya solo depende de usted, incluso si lo guarda en una carpeta
  sincronizada con una nube.
- **Cuarentena con copia de seguridad**: la app copia el archivo de instalación (APK) de una app en
  la carpeta que usted haya elegido, antes de su desinstalación. Un APK contiene la app, **nunca sus
  datos**. Esa carpeta no se borra cuando desinstala App Manager Tech.
- **Páginas web** («Buscar actualizaciones», código fuente, informar de un problema, licencia, esta
  política): la app pide al navegador de su teléfono que abra una dirección fija. Es el navegador
  quien se conecta, sujeto a su propia política de privacidad; App Manager Tech solo le transmite
  esa dirección.
- **Pantallas de Android** (desinstalar, información de una app, ajustes): la app les transmite el
  nombre del paquete correspondiente, y nada más.

## Registros

Las versiones publicadas no escriben **ninguna línea de registro**: todo mensaje se descarta antes
de escribirse. Solo las versiones de desarrollo escriben registros, visibles únicamente mediante
ADB.

## Sus derechos (RGPD)

Como la app no trata ningún dato personal fuera de su dispositivo, no existe ningún tratamiento remoto respecto del cual pueda ejercer los derechos de acceso, rectificación o supresión. Usted conserva el control total: desactivar
un historial, vaciar la papelera o desinstalar la app borra los datos correspondientes del
dispositivo. La exportación le proporciona una copia legible de lo que la app sabe de sus apps.

## Menores

La app no recoge ningún dato y es apta para todos los públicos.

## Cambios

Esta política puede evolucionar junto con la app; la fecha de arriba indica la última revisión, y
el historial es público en este repositorio. La versión del 23 de mayo de 2026 omitía los
historiales y sus plazos de conservación, los archivos escritos fuera de la app, y la mitad de los
permisos del APK. La del 8 de octubre de 2026 añade `REQUEST_INSTALL_PACKAGES`, sin el cual Android
rechazaba cualquier restauración de una cuarentena.

## Editor y contacto

App Manager Tech la edita **Patrice Haltaya** (Francia), responsable del tratamiento en el sentido
del RGPD — aunque, como se explica más arriba, ningún dato le llega nunca. Contacto:
**contact@files-tech.com**.

Preguntas o avisos: abra una [issue](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues) en el
repositorio, o contáctenos a través de [files-tech.com](https://files-tech.com). Sobre seguridad,
consulte [SECURITY.md](SECURITY.md).
