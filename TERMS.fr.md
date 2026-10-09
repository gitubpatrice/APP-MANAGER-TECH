# Conditions d'utilisation — App Manager Tech

_Dernière mise à jour : 9 octobre 2026_ · 🇬🇧 [English](TERMS.md) · 🇩🇪 [Deutsch](TERMS.de.md) · 🇮🇹 [Italiano](TERMS.it.md) · 🇪🇸 [Español](TERMS.es.md)

> **Version de référence.** En cas de divergence entre ces conditions et l'une de leurs traductions,
> c'est cette version française qui fait foi.

## Éditeur

App Manager Tech (`com.filestech.appmanager`) fait partie de la suite **Files Tech**, éditée par
**Patrice Haltaya**. Contact : **contact@files-tech.com**.

## Licence

App Manager Tech est un logiciel libre publié sous **licence Apache 2.0**. Vous pouvez l'utiliser,
le modifier et le redistribuer dans les conditions de cette licence. Le texte complet est dans le
fichier `LICENSE` du dépôt source (https://github.com/gitubpatrice/APP-MANAGER-TECH). Seul le texte
anglais de la licence fait foi.

## Usage

L'application est mise à disposition **gratuitement** et fournie **telle quelle, sans garantie
d'aucune sorte** (licence Apache 2.0, section 7). Elle vous aide à examiner les applications
installées sur votre téléphone et à agir sur elles ; les décisions que vous prenez sur cette base
— désinstaller, désactiver, mettre en quarantaine — restent les vôtres.

En l'utilisant, vous confirmez être propriétaire du téléphone ou autorisé à le gérer. Copier le
fichier d'installation (APK) d'une application ne vous donne aucun droit sur elle : ne le
redistribuez pas si sa licence l'interdit.

## Limitations

- **Les actions passent par Android.** App Manager Tech ne désinstalle, ne désactive et ne vide le
  cache d'aucune application elle-même : elle ouvre la fenêtre de désinstallation d'Android, ou la
  fiche de l'application dans ses réglages, et c'est Android qui demande confirmation et agit. Elle
  ne contourne jamais ces confirmations.
- **« Forcer l'arrêt » n'arrête que les processus d'arrière-plan.** Android ne permet pas à une
  application d'en arrêter une autre qui est visible ou qui fait tourner un service de premier plan.
- **La détection des pisteurs n'est pas un audit de sécurité.** Elle compare les composants que
  chaque application déclare à une liste figée à la date de la version : elle peut manquer un
  pisteur absent de cette liste, renommé ou intégré autrement, et en signaler un qui est présent
  mais jamais utilisé.
- **Le score de confidentialité est une estimation.** Il est calculé à partir des permissions
  déclarées, de certains accès spéciaux et de la source d'installation. Il mesure l'étendue des
  accès d'une application, pas ses intentions : un score bas ne désigne pas un logiciel malveillant,
  un score élevé ne garantit rien.
- **L'avertissement « application critique » n'est pas exhaustif.** Avant une action sur une
  application qu'elle reconnaît comme critique, ou que vous avez protégée, l'application exige un
  appui maintenu trois secondes. Elle ne les reconnaît pas toutes : désactiver ou désinstaller une application système
  peut rendre le téléphone instable.
- Sans l'« Accès aux données d'utilisation », les tailles s'affichent à 0 et les applications
  comme « Jamais utilisée ». Android peut retarder ou bloquer les notifications ; l'application ne peut pas en
  garantir la remise.

## Perte de données

- **Désinstaller une application, ou effacer ses données, est irréversible.** C'est Android qui le
  fait, après sa propre confirmation ; App Manager Tech n'a aucun moyen de l'annuler.
- **La corbeille ne désinstalle rien** tant que vous ne la videz pas : la vider lance la
  désinstallation de chaque application qu'elle contient.
- **Une quarantaine avec sauvegarde ne garde que l'APK.** Les données de l'application — comptes,
  préférences, fichiers — sont perdues à sa désinstallation, et la restaurer réinstalle une
  application vierge. La restauration passe par l'installateur d'Android, qui demande votre accord
  et, la première fois, l'autorisation d'installer des applications depuis App Manager Tech.
  La quarantaine avec sauvegarde n'est pas proposée pour une application installée en plusieurs
  fichiers APK (App Bundle) ni pour une application système : la sauvegarde de son seul fichier principal ne pourrait pas
  la réinstaller.
- **Une sauvegarde modifiée n'est jamais restaurée.** À la sauvegarde, App Manager Tech enregistre
  l'empreinte SHA-256 du fichier ; à la restauration, il en refait une copie dans son espace privé,
  vérifie qu'elle a exactement cette empreinte et installe cette copie vérifiée, sinon il refuse.
  Une sauvegarde faite avant la version 0.5.1, sans empreinte, ne peut pas être vérifiée et n'est
  donc pas restaurée par l'application : le fichier reste dans votre dossier.
- Désinstaller App Manager Tech efface ses historiques, sa corbeille et ses quarantaines. Les APK
  sauvegardés et les exports restent dans les dossiers où vous les avez mis.

## Données

Tout ce que l'application conserve reste **sur votre téléphone** — voir la
[politique de confidentialité](PRIVACY.fr.md). App Manager Tech n'envoie rien sur Internet et n'a
pas la permission technique de le faire (pas de permission `INTERNET` Android). Pour la même
raison, personne ne peut la désactiver ni la retirer de votre téléphone à distance.

## Mises à jour

Les mises à jour sont publiées sur le dépôt GitHub officiel, signées par la même clé que chaque
version précédente. L'application ne se met jamais à jour d'elle-même et ne vérifie jamais
l'existence d'une mise à jour : le bouton « Vérifier les mises à jour » ouvre la page des versions
dans votre navigateur, et installer une nouvelle version vous revient.

## Responsabilité

Dans la limite autorisée par la loi, l'éditeur ne pourra être tenu responsable d'aucun dommage
direct ou indirect résultant de l'utilisation de l'application (licence Apache 2.0, section 8). En
particulier, **toute perte de données consécutive à une désinstallation, à un effacement de
données, à une désactivation, à une quarantaine ou à une restauration que vous avez déclenchée est
de la seule responsabilité de l'utilisateur**. Rien dans ces conditions ne limite une
responsabilité que la loi interdit de limiter.

## Loi applicable

Ces conditions sont soumises au **droit français**, sans préjudice des règles impératives de
protection des consommateurs de votre pays de résidence. Lorsque la loi le permet, les tribunaux
français sont compétents en cas de litige.

## Modifications

Ces conditions pourront évoluer avec l'application ; la date en tête de document indique la
dernière révision, et l'historique est public dans ce dépôt. La version du 23 mai 2026 annonçait
une distribution par F-Droid : c'était faux, App Manager Tech n'y est pas publiée.

## Contact

**contact@files-tech.com** · [issues du dépôt](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
· [files-tech.com](https://files-tech.com/app-manager-tech.php)
