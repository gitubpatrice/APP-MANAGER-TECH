# Politique de confidentialité — App Manager Tech

**Date d'effet :** 2026-05-23

App Manager Tech est une application Android entièrement locale. Elle ne
collecte, ne transmet, ni ne partage aucune donnée utilisateur. Cette
politique de confidentialité documente cet engagement en termes simples.

---

## 1. Données que nous collectons

**Aucune.** L'application lit des métadonnées qui existent déjà sur votre
appareil (catalogue des apps installées via `PackageManager`, tailles via
`StorageStatsManager`, dates de dernière utilisation via `UsageStatsManager`)
et stocke une copie en cache dans une base SQLite privée (Room) à l'intérieur
du stockage privé de l'app. Ce cache ne quitte jamais l'appareil.

L'app ne demande pas la permission `INTERNET`. Elle ne peut pas ouvrir de
socket réseau. Elle ne peut envoyer quoi que ce soit à aucun serveur, ni
le nôtre ni un autre.

---

## 2. Données stockées sur votre appareil

| Quoi | Où | Quand supprimé |
|---|---|---|
| Snapshot du catalogue d'apps | Base `Room` dans le stockage privé de l'app | Désinstallation de l'app, ou « Effacer les données » dans Paramètres OS |
| Préférences utilisateur (thème, cadence d'analyse, liste d'ignorance…) | `DataStore` Préférences dans le stockage privé | Idem |
| Enregistrement du canal de notification | Paramètres OS | Idem |

Le fichier Room est exclu de la sauvegarde cloud
(règle `res/xml/backup_rules.xml`). Les préférences DataStore sont exclues
par la même règle.

---

## 3. Permissions

| Permission | Pourquoi | Optionnelle ? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Énumérer les apps installées | Non — fonction de base |
| `PACKAGE_USAGE_STATS` | Tailles & dernière utilisation | Oui — dégradation gracieuse si refusé (tailles à 0) |
| `GET_PACKAGE_SIZE` | Tailles stockage via StorageStatsManager | Non — install-time normal |
| `REQUEST_DELETE_PACKAGES` | Déclencher la boîte de dialogue système de désinstallation | Non — install-time normal |
| `KILL_BACKGROUND_PROCESSES` | Arrêt forcé en mode meilleur effort | Non — install-time normal |
| `POST_NOTIFICATIONS` (Android 13+) | Notification de seuil de cache | Oui — accord runtime, demandé seulement au premier opt-in |

**Jamais demandées :** `INTERNET`, localisation, contacts, SMS, agenda,
microphone, caméra, `MANAGE_EXTERNAL_STORAGE`.

---

## 4. Tiers

App Manager Tech intègre zéro SDK tiers de type « phone home » :

- Pas de Google Mobile Services (GMS)
- Pas de Firebase
- Pas de Google Analytics, AppsFlyer, Mixpanel, Sentry SaaS, ou autre télémétrie
- Aucun SDK publicitaire
- Aucun rapporteur de crash qui téléverse quoi que ce soit

Toutes les bibliothèques embarquées sont sous licence Apache 2.0 / MIT / BSD /
LGPL / GPL / AGPL — voir [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
pour la liste complète.

---

## 5. Logs

En build debug, l'app écrit des lignes de log dans Logcat (visibles
uniquement via USB debug + ADB). Elles incluent des noms de paquets, des
tailles et des résultats d'opérations. Aucune PII au sens RGPD : les noms
de paquets sont des informations publiques par design Android.

**Les builds release n'émettent aucun log** — Timber utilise
`NoOpReleaseTree` qui jette chaque entrée silencieusement.

---

## 6. Vie privée des enfants

App Manager Tech ne collecte aucune donnée d'aucun utilisateur, enfants
de moins de 13 ans inclus. L'app n'a ni système de compte ni présence en ligne.

---

## 7. Vos droits (RGPD / CNIL France)

Puisque nous ne collectons, stockons hors appareil, ni traitons aucune
donnée personnelle, les droits RGPD standards (accès, rectification,
effacement, portabilité, restriction, opposition) n'ont pas matière à
s'appliquer :

- **Effacement :** désinstallez l'app, ou Paramètres OS → Applications →
  App Manager Tech → Stockage → Effacer les données.
- **Accès / portabilité :** la fonction Export permet déjà de sauvegarder
  le rapport de stockage complet en JSON ou CSV à l'emplacement de votre
  choix via le Storage Access Framework.

Si vous estimez que cette app viole vos droits malgré tout, ouvrez un
ticket sur https://github.com/gitubpatrice/APP-MANAGER-TECH/issues ou
contactez contact@files-tech.com.

---

## 8. Modifications de cette politique

Les changements majeurs apportés à cette politique seront reflétés dans
CHANGELOG.md et annoncés dans les notes de version correspondantes
(`fastlane/metadata/android/.../changelogs/`).

La version courante est suivie en même temps que le code source :
https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/PRIVACY.fr.md
