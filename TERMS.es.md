# Condiciones de uso — App Manager Tech

_Traducción de la versión del 8 de octubre de 2026._ · 🇫🇷 [Français](TERMS.fr.md) · 🇬🇧 [English](TERMS.md) · 🇩🇪 [Deutsch](TERMS.de.md) · 🇮🇹 [Italiano](TERMS.it.md)

> Esta traducción la ha realizado el desarrollador con ayuda de herramientas automáticas y todavía
> no la ha revisado un hablante nativo. **En caso de discrepancia, prevalece la
> [versión francesa](TERMS.fr.md).**

## Editor

App Manager Tech (`com.filestech.appmanager`) forma parte de la suite **Files Tech**, editada por
**Patrice Haltaya**. Contacto: **contact@files-tech.com**.

## Licencia

App Manager Tech es software libre publicado bajo la **licencia Apache 2.0**. Puede usarlo,
modificarlo y redistribuirlo en las condiciones de esa licencia. El texto completo está en el
archivo `LICENSE` del repositorio de código fuente (https://github.com/gitubpatrice/APP-MANAGER-TECH).
Solo el texto inglés de la licencia es vinculante.

## Uso

La app se pone a disposición **gratuitamente** y se proporciona **tal cual, sin garantía de ningún
tipo** (licencia Apache 2.0, sección 7). Le ayuda a examinar las apps instaladas en su teléfono y a
actuar sobre ellas; las decisiones que tome basándose en ello — desinstalar, desactivar, poner en
cuarentena — siguen siendo suyas.

Al utilizarla, usted confirma que es propietario del teléfono o que está autorizado a gestionarlo.
Copiar el archivo de instalación (APK) de una app no le da ningún derecho sobre ella: no lo
redistribuya si su licencia lo prohíbe.

## Limitaciones

- **Las acciones pasan por Android.** App Manager Tech no desinstala, no desactiva ni borra la caché
  de ninguna app por sí misma: abre la ventana de desinstalación de Android, o la pantalla de
  información de la app en los ajustes de Android, y es Android quien pide confirmación y actúa.
  Nunca elude esas confirmaciones.
- **«Forzar detención» solo detiene los procesos en segundo plano.** Android no permite a una app
  detener otra que esté visible o que ejecute un servicio en primer plano.
- **La detección de rastreadores no es una auditoría de seguridad.** Compara los componentes que
  cada app declara con una lista congelada en la fecha de la versión: puede pasar por alto un
  rastreador ausente de esa lista, renombrado o integrado de otra forma, y señalar uno que está
  presente pero que nunca se utiliza.
- **La puntuación de privacidad es una estimación.** Se calcula a partir de los permisos
  declarados, de ciertos accesos especiales y del origen de la instalación. Mide el alcance de los
  accesos de una app, no sus intenciones: una puntuación baja no significa que la app sea maliciosa, y una puntuación alta no garantiza nada.
- **La advertencia «app crítica» no es exhaustiva.** Antes de una acción sobre una app que reconoce
  como crítica, o que usted ha protegido, la app exige mantener pulsado durante tres segundos. No
  las reconoce todas: desactivar o desinstalar una app del sistema puede volver inestable el
  teléfono.
- Sin el «Acceso a datos de uso», los tamaños aparecen en 0 y las apps como «Nunca usada». Android
  puede retrasar o bloquear las notificaciones; la app no puede garantizar su entrega.

## Pérdida de datos

- **Desinstalar una app, o borrar sus datos, es irreversible.** Lo hace Android, tras su propia
  confirmación; App Manager Tech no tiene ningún medio para deshacerlo.
- **La papelera no desinstala nada** mientras usted no la vacíe: vaciarla inicia la desinstalación
  de cada app que contiene.
- **Una cuarentena con copia de seguridad solo conserva el APK.** Los datos de la app — cuentas,
  preferencias, archivos — se pierden al desinstalarla; al restaurarla, la app se reinstala sin sus datos.
  La restauración pasa por el instalador de Android, que le pide su consentimiento y, la primera
  vez, la autorización para instalar apps desde App Manager Tech.
  La cuarentena con copia de seguridad no se ofrece para una app instalada como varios archivos APK (App Bundle) ni para una app del
  sistema: una copia de seguridad de su archivo principal por sí solo no podría reinstalarla.
- **App Manager Tech todavía no comprueba que el APK de una copia de seguridad no haya sido
  modificado** en su carpeta. Restaure únicamente archivos que usted mismo haya guardado, en una
  carpeta que ninguna otra app modifique.
- Desinstalar App Manager Tech borra sus historiales, su papelera y sus cuarentenas. Los APK
  guardados y las exportaciones permanecen en las carpetas donde usted los puso.

## Datos

Todo lo que la app conserva permanece **en su teléfono** — consulte la
[política de privacidad](PRIVACY.es.md). App Manager Tech no envía nada por Internet y no tiene
permiso técnico para hacerlo (ningún permiso Android `INTERNET`). Por la misma razón, nadie puede
desactivarla ni eliminarla de su teléfono a distancia.

## Actualizaciones

Las actualizaciones se publican en el repositorio oficial de GitHub, firmadas con la misma clave
que cada versión anterior. La app nunca se actualiza sola y nunca comprueba si existe una
actualización: el botón «Buscar actualizaciones» abre la página de versiones en su navegador, e
instalar una nueva versión le corresponde a usted.

## Responsabilidad

En la medida en que la ley lo permita, el editor no será responsable de ningún daño directo o
indirecto derivado del uso de la app (licencia Apache 2.0, sección 8). En particular, **cualquier
pérdida de datos tras una desinstalación, un borrado de datos, una desactivación, una cuarentena o
una restauración que usted haya iniciado es responsabilidad exclusiva del usuario**. Nada en estas
condiciones limita una responsabilidad que la ley no permite limitar.

## Ley aplicable

Estas condiciones se rigen por la **ley francesa**, sin perjuicio de las normas imperativas de
protección de los consumidores de su país de residencia. Cuando la ley lo permita, serán
competentes los tribunales franceses en caso de litigio.

## Cambios

Estas condiciones pueden evolucionar junto con la app; la fecha de arriba indica la última
revisión, y el historial es público en este repositorio. La versión del 23 de mayo de 2026
anunciaba una distribución a través de F-Droid: era falso, App Manager Tech no está publicada allí.

## Contacto

**contact@files-tech.com** · [issues del repositorio](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
· [files-tech.com](https://files-tech.com/app-manager-tech.php)
