# Conditions d'utilisation — App Manager Tech

**Date d'effet :** 2026-05-23

En installant ou en utilisant App Manager Tech, vous acceptez ces
Conditions. Si vous ne les acceptez pas, ne l'installez pas et ne
l'utilisez pas.

---

## 1. L'application

App Manager Tech est une application Android libre et open source qui
vous permet d'inspecter, d'agir par lot, de nettoyer et d'auditer les
applications installées sur votre propre appareil Android. Distribuée
sous Licence Apache 2.0 (voir [LICENSE](LICENSE)). Code source complet :
https://github.com/gitubpatrice/APP-MANAGER-TECH.

---

## 2. Absence de garantie

App Manager Tech est fournie **« EN L'ÉTAT »**, sans aucune garantie —
expresse ou implicite — incluant la qualité marchande, l'adéquation à un
usage particulier ou l'absence de contrefaçon. Voir la section 7 de la
Licence Apache 2.0 pour le décharge complète.

L'auteur et les contributeurs ne sont pas responsables des dommages
directs, indirects, spéciaux, fortuits ou consécutifs résultant de
l'utilisation ou de l'impossibilité d'utiliser l'app — incluant, sans
limitation :

- Perte de données causée par la désinstallation d'apps (la boîte de
  confirmation système est toujours affichée, mais l'app ne maintient
  pas d'historique d'annulation global) ;
- Changement de comportement après désactivation ou arrêt forcé d'une
  app installée ;
- Notifications manquées parce que `POST_NOTIFICATIONS` a été refusée ;
- Tailles affichées à 0 parce que `PACKAGE_USAGE_STATS` n'a pas été accordée ;
- Toute autre conséquence opérationnelle d'une action que vous avez déclenchée.

---

## 3. Vos responsabilités

En utilisant l'app vous confirmez que :

- Vous possédez ou êtes autorisé à gérer l'appareil Android sur lequel
  l'app s'exécute ;
- Vous comprenez que désinstaller une app ou effacer ses données est
  irréversible — l'OS effectue la suppression réelle ; nous ne faisons
  que la déclencher via les flux publics `Intent.ACTION_DELETE` et la
  page d'informations de l'application ;
- Vous n'utiliserez pas l'app pour violer les termes de licence d'autres
  apps (par exemple extraire un APK pour redistribution si la licence
  d'origine l'interdit).

---

## 4. Permissions et comportement OS

L'app dégrade gracieusement lorsqu'une permission est refusée :

- `PACKAGE_USAGE_STATS` refusée → tailles affichées à 0, pas de crash ;
- `POST_NOTIFICATIONS` refusée (Android 13+) → notifications jetées
  silencieusement, l'app continue de fonctionner ;
- `KILL_BACKGROUND_PROCESSES` est une permission normale (pas de grant
  runtime) mais `ActivityManager.killBackgroundProcesses` n'arrête que
  les processus en arrière-plan — les apps et services en avant-plan
  continuent, par design Android.

Les opérations qui modifient l'état OS (désinstallation, désactivation,
effacement de données) passent toujours par l'UI système. Nous ne
contournons jamais les confirmations système.

---

## 5. Modifications de l'app

Nous pouvons publier de nouvelles versions qui changent, retirent ou
modifient des fonctionnalités. Les mises à jour sont distribuées via
F-Droid et via les Releases GitHub. Vous pouvez refuser une mise à jour
en ne l'installant pas ; la version existante continuera de fonctionner
jusqu'à ce que les exigences de compatibilité Android vous obligent à
mettre à jour ou à désinstaller.

---

## 6. Cessation

Vous pouvez cesser d'utiliser l'app à tout moment en la désinstallant.
Nous n'avons aucun mécanisme de désactivation à distance (pas de
permission Internet) et ne pouvons pas révoquer une copie de l'app
de votre appareil.

---

## 7. Droit applicable

Ces Conditions sont régies par le droit français et tout litige qui
ne peut être résolu à l'amiable relève de la compétence des tribunaux
français du domicile de l'auteur, sauf si le droit impératif de votre
pays de résidence en dispose autrement.

---

## 8. Contact

- Code source & tickets : https://github.com/gitubpatrice/APP-MANAGER-TECH
- E-mail : contact@files-tech.com
- Site du projet : https://files-tech.com/app-manager-tech.php
