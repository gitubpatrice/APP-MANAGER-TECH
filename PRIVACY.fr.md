# Politique de confidentialité — App Manager Tech

_Dernière mise à jour : 9 octobre 2026_ · 🇬🇧 [English](PRIVACY.md) · 🇩🇪 [Deutsch](PRIVACY.de.md) · 🇮🇹 [Italiano](PRIVACY.it.md) · 🇪🇸 [Español](PRIVACY.es.md)

> **Version de référence.** En cas de divergence entre cette politique et l'une de ses traductions,
> c'est cette version française qui fait foi.

App Manager Tech (`com.filestech.appmanager`) est un gestionnaire d'applications **entièrement
local** : il examine les applications installées sur votre téléphone depuis ce téléphone même,
et n'envoie rien nulle part.

## En résumé

- **Aucune donnée collectée, aucune donnée transmise.** L'application ne déclare pas la permission
  `INTERNET` : elle est techniquement incapable d'envoyer quoi que ce soit sur un réseau. Un contrôle
  automatique le vérifie sur l'APK publié lui-même, à chaque intégration continue.
- **Aucun compte, aucune inscription, aucun identifiant.**
- **Aucune publicité, aucun traceur, aucun outil d'analyse**, aucun rapport de plantage envoyé.
- **Aucune sauvegarde cloud** : `allowBackup=false`, et la base de données comme les réglages sont
  exclus des sauvegardes automatiques d'Android et des transferts d'un appareil à l'autre.

## Quelles données, et où

L'application lit ce qu'Android sait déjà des applications installées, et en garde une copie dans
son **stockage privé**, auquel aucune autre application n'a accès. Cette base n'est pas chiffrée :
elle décrit vos applications, pas vos contenus.

| Donnée | D'où elle vient | Combien de temps |
|---|---|---|
| Liste des applications : nom, version, tailles, dates d'installation, de mise à jour et de dernière utilisation, magasin d'origine, état (activée, en veille) | Android (`PackageManager`, `StorageStatsManager`, `UsageStatsManager`) | Remplacée à chaque analyse |
| Pisteurs détectés, score de confidentialité | Calculés sur le téléphone (voir plus bas) | Recalculés à chaque affichage |
| Historique des permissions accordées à chaque application — **désactivé par défaut** | Relevé périodique, si vous l'activez | 90 jours par défaut, réglable de 7 à 365 |
| Historique des installations, mises à jour et désinstallations, avec l'empreinte SHA-256 de chaque APK et le motif de désinstallation que vous choisissez de donner — **désactivé par défaut** | Annonces d'Android reçues pendant que l'application fonctionne, si vous l'activez | 180 jours par défaut, réglable de 30 à 365 |
| Journal des actions lancées depuis App Manager Tech — **désactivé par défaut** | Vos actions, si vous l'activez | 180 jours par défaut, réglable de 30 à 365 |
| Corbeille et quarantaines (avec l'empreinte SHA-256 de chaque APK sauvegardé) | Vos actions | Jusqu'à ce que vous les vidiez ou restauriez |
| Réglages : thème, seuils, applications ignorées ou protégées, étiquettes, et l'autorisation d'accès au dossier de sauvegarde que vous avez choisi | Vous | Jusqu'à la désinstallation |

**Les pisteurs** sont repérés en comparant les noms des composants que chaque application déclare à
Android à une liste intégrée à App Manager Tech, tirée de la base publique d'Exodus Privacy. Cette
liste est mise à jour avec l'application ; elle n'est jamais téléchargée.

Désinstaller App Manager Tech, ou effacer ses données dans les réglages d'Android, supprime tout ce
qui précède. Les fichiers que **vous** avez fait écrire ailleurs (voir « Partage ») restent où vous
les avez mis.

Le développeur n'a **aucun accès** à ces données et n'en reçoit **aucune copie**.

## Permissions demandées et pourquoi

Cette liste est **exhaustive** : ce sont les douze permissions que porte l'APK publié, telles qu'on
les lit dans son manifeste. Elle inclut donc celles qu'aucune ligne de notre code ne demande, mais
qu'une bibliothèque a apportées avec elle. Un contrôle automatique refuse toute construction dont
l'APK s'écarterait de cette liste (`tools/check-manifest-permissions.py`, exécuté à chaque
intégration continue).

### Déclarées par l'application

| Permission | Usage | Réseau ? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Voir toutes les applications installées : c'est la fonction même de l'application. | Non |
| `PACKAGE_USAGE_STATS` | Accès spécial « Accès aux données d'utilisation », que vous accordez vous-même dans les réglages d'Android. Il donne les tailles et la date de dernière utilisation de chaque application. Refusé, les tailles s'affichent à 0 et les applications comme « Jamais utilisée » ; rien d'autre ne cesse de fonctionner. | Non |
| `GET_PACKAGE_SIZE` | Lire la taille des applications. | Non |
| `REQUEST_DELETE_PACKAGES` | Ouvrir la fenêtre de désinstallation d'Android. C'est Android qui demande confirmation et qui désinstalle. | Non |
| `REQUEST_INSTALL_PACKAGES` | Restaurer une application mise en quarantaine : remettre l'APK sauvegardé à l'installateur d'Android. Android exige en plus que vous autorisiez vous-même App Manager Tech à installer des applications, et demande confirmation à chaque installation. Elle ne sert à rien d'autre. | Non |
| `KILL_BACKGROUND_PROCESSES` | Arrêter les processus d'arrière-plan d'une application, à votre demande. | Non |
| `POST_NOTIFICATIONS` | Trois notifications facultatives : seuil de cache atteint, permissions modifiées, fin d'une quarantaine. Demandée quand vous activez l'une d'elles (Android 13 et plus), refusable. | Non |

### Apportées par les bibliothèques utilisées

| Permission | Origine | Ce qu'elle sert **réellement** ici | Réseau ? |
|---|---|---|---|
| `WAKE_LOCK` | `androidx.work` | Prise un instant pendant qu'une tâche de fond s'exécute : analyse automatique, relevé des permissions, purge des historiques, rappel de quarantaine — celles que vous avez activées. | Non |
| `RECEIVE_BOOT_COMPLETED` | `androidx.work` | Reprogrammer ces tâches après un redémarrage. | Non |
| `FOREGROUND_SERVICE` | `androidx.work` | **Rien.** `androidx.work` la déclare pour les tâches « expedited » ; l'application n'en planifie aucune. | Non |
| `ACCESS_NETWORK_STATE` | `androidx.work` | **Rien.** `androidx.work` la déclare pour les tâches qui attendent un réseau ; toutes celles de l'application sont planifiées sans condition de réseau. Elle ne permettrait que de savoir si un réseau est présent : sans `INTERNET`, il est impossible de s'en servir. | Non |
| `com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | Permission **auto-attribuée**, de niveau « signature » : seule une application signée avec notre clé peut l'obtenir. Elle ferme aux autres applications le récepteur que l'application enregistre pour suivre les installations. | Non |

L'application ne demande **jamais** l'accès à Internet, à la localisation, aux contacts, aux SMS, à
l'agenda, au micro, à la caméra, ni à vos fichiers (`MANAGE_EXTERNAL_STORAGE`,
`READ_EXTERNAL_STORAGE`).

## Partage avec des tiers

**Aucun.** Aucune donnée n'est partagée, vendue ou transmise à qui que ce soit — l'application n'a
aucun moyen technique de le faire.

Les seuls échanges possibles sont ceux que **vous** déclenchez, et qui restent sur votre téléphone :

- **Export (JSON, CSV, PDF)** : un rapport sur vos applications, écrit à l'emplacement que vous
  choisissez dans le sélecteur de fichiers d'Android. Il révèle quelles applications sont installées
  sur votre téléphone ; ce que devient ensuite le fichier ne dépend plus que de vous, y compris si
  vous l'enregistrez dans un dossier synchronisé vers un cloud.
- **Quarantaine avec sauvegarde** : l'application copie le fichier d'installation (APK) d'une
  application dans le dossier que vous avez choisi, avant sa désinstallation. Un APK contient
  l'application, **jamais ses données**. Ce dossier n'est pas effacé quand vous désinstallez
  App Manager Tech.
- **Pages web** (« Vérifier les mises à jour », code source, signaler un problème, licence, cette
  politique) : l'application demande au navigateur de votre téléphone d'ouvrir une adresse fixe.
  C'est lui qui se connecte, sous sa propre politique de confidentialité ; App Manager Tech ne lui
  transmet que cette adresse.
- **Écrans d'Android** (désinstaller, fiche d'une application, réglages) : l'application leur
  transmet le nom du paquet concerné, et rien d'autre.

## Journaux

Les versions publiées n'écrivent **aucune ligne de journal** : tout message est jeté avant d'être
écrit. Seules les versions de développement en écrivent, visibles uniquement par ADB.

## Vos droits (RGPD)

L'application ne traitant aucune donnée personnelle en dehors de votre appareil, il n'existe aucun
traitement distant à consulter, rectifier ou supprimer. Vous gardez le contrôle total : désactiver
un historique, vider la corbeille, ou désinstaller l'application efface les données
correspondantes de l'appareil. L'export vous donne une copie lisible de ce que l'application
connaît de vos applications.

## Enfants

L'application ne collecte aucune donnée et convient à tous les publics.

## Modifications

Cette politique pourra évoluer avec l'application ; la date en tête de document indique la dernière
révision, et l'historique est public dans ce dépôt. La version du 23 mai 2026 omettait les
historiques et leurs durées de conservation, les fichiers écrits hors de l'application, et la
moitié des permissions de l'APK. Celle du 9 octobre 2026 ajoute `REQUEST_INSTALL_PACKAGES`, sans
laquelle Android refusait toute restauration d'une quarantaine.

## Éditeur et contact

App Manager Tech est édité par **Patrice Haltaya** (France), responsable du traitement au sens du
RGPD — même si, comme expliqué plus haut, aucune donnée ne lui parvient jamais. Contact :
**contact@files-tech.com**.

Question ou signalement : ouvrez une [issue](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
sur le dépôt, ou via [files-tech.com](https://files-tech.com). Pour la sécurité, voir
[SECURITY.md](SECURITY.md).
