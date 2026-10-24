# Bureau : dossier des données, journal de diagnostic et proxy

_Rôle : trois réglages avancés de la version de bureau, pour ceux qui en ont
besoin, sans rien changer pour les autres. Fichiers couverts :
`kmp/desktopApp/.../DataDirectory.kt`, `DataFolder.kt`, `Diagnostics.kt`,
`CrashLog.kt`, `DesktopUpdates.kt`, `DesktopServices.kt`, `Main.kt` ;
`kmp/ui/.../app/DesktopAdvanced.kt` (`DataFolderService`, `Diagnostics`),
`ui/app/AppServices.kt`, `ui/app/Replace.kt`, `ui/settings/DesktopCards.kt`,
`ui/settings/SettingsScreen.kt`, `ui/team/ExchangeDialogs.kt`. Tests :
`DesktopTest` (dossier par défaut), `DataFolderTest`, `DiagnosticsTest`,
`DesktopUpdatesTest`, `DesktopSettingsUiTest`._

État : **issue #115, implémentée en 3.9.0.** Les trois points ont été
tranchés par le propriétaire le 2026-10-24 : dossier des données (bureau),
journal de diagnostic, proxy du système.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

- Qui garde ses données dans un dossier synchronisé ou chiffré (condition de
  la synchronisation par un service tiers, #88) ne peut pas choisir où vit la
  base : le dossier du bureau est fixé par le système.
- Pour un rapport de bug (plantage, #65), décrire ce qui s'est passé est
  difficile ; un journal local, que l'on joint volontairement, aiderait.
- Derrière un proxy d'entreprise, la vérification des mises à jour du bureau
  échoue si Kairos ignore le proxy du système.

### Comportement attendu (utilisateur)

#### Dossier des données (bureau)

- Réglages → Données montre le dossier utilisé (« Emplacement : … ») et
  propose **« Changer de dossier… »** : le sélecteur de dossiers du système
  s'ouvre.
- Kairos dit ce qui va se passer **avant** de le faire, puis attend la
  confirmation :
  - le dossier contient déjà des données Kairos : « Kairos ouvrira les
    données de ce dossier au prochain démarrage. Les données actuelles restent
    dans … » ;
  - sinon : « Au prochain démarrage, Kairos copiera tes données dans ce
    dossier et les y utilisera. L'ancien dossier reste intact. »
- Le changement prend effet **au prochain démarrage** (la base ouverte n'est
  jamais déplacée) ; la carte le rappelle (« Redémarre Kairos pour utiliser
  … »). Rien n'est jamais déplacé ni supprimé : la copie laisse l'ancien
  dossier tel quel.
- **« Revenir au dossier par défaut »** suit les mêmes règles.
- Si le dossier choisi ne peut pas servir au démarrage (disparu, en lecture
  seule, copie impossible), Kairos ouvre le dossier précédent et la carte le
  dit (« Le dossier … n'a pas pu être utilisé ; Kairos est resté sur … »).
- Option de lancement **`--data-dir=<dossier>`** (documentée dans le README),
  pour un lanceur ou un script ; avec elle, ou avec `KAIROS_DATA_DIR`, la
  carte dit que le dossier est fixé au lancement et ne propose pas de le
  changer.
- Un dossier synchronisé (#88) est permis ; Kairos ne doit pas être ouvert
  sur deux appareils à la fois sur le même dossier : le verrou d'instance
  unique ne protège que la machine locale (la carte le rappelle).

#### Journal de diagnostic (bureau)

- Réglages → À propos, carte **« Diagnostic »** : interrupteur **« Tenir un
  journal de diagnostic »**, désactivé par défaut. Propre à cet ordinateur
  (pas dans l'export, pas dans les réglages partagés).
- Le journal est **local**, jamais envoyé, **plafonné** (256 Ko, plus le
  fichier précédent), et ne contient **aucun contenu de tâche** : dates,
  version, système, et le résultat des opérations (ouverture et migration de
  la base, sauvegardes, exports et imports, échanges d'équipe, vérification
  des mises à jour), en vocabulaire fixe et en nombres.
- **« Copier le diagnostic »** (toujours proposé) copie dans le
  presse-papiers : version, système, Java, la fin du journal s'il existe et
  la fin des rapports de plantage (`crash.log`, déjà tenus depuis la 3.3) ;
  un message dit de le coller dans un rapport de bug.

#### Proxy (bureau)

- Kairos suit **le proxy du système** (réglages du système d'exploitation, ou
  propriétés `https.proxyHost` de Java) pour la vérification des mises à
  jour. Aucun réglage dans Kairos.

### Critères de succès

- Sans rien toucher, rien ne change : même dossier, aucun fichier de plus,
  aucune clé de plus dans l'export (`DataFolderTest`, `DiagnosticsTest`).
- `--data-dir=` prime sur `KAIROS_DATA_DIR`, qui prime sur le choix des
  Réglages, qui prime sur le dossier par défaut (`DataFolderTest`).
- Choisir un dossier vide puis redémarrer y copie la base, l'ancien dossier
  reste identique ; un dossier qui a déjà une base est ouvert sans copie ; un
  dossier inutilisable laisse Kairos sur le précédent, avec le message
  (`DataFolderTest`).
- Le journal n'est écrit que s'il est activé, reste sous son plafond, et ne
  contient aucun titre de tâche ; « Copier le diagnostic » rassemble système,
  journal et plantages (`DiagnosticsTest`, `DesktopSettingsUiTest`).
- La vérification des mises à jour passe par le sélecteur de proxy du
  système, et son résultat entre au journal (`DesktopUpdatesTest`).

### Hors périmètre / différé

- Android et web : pas de dossier à choisir (stockage du système, fichier lié
  du web), pas de journal (le rapport de plantage d'Android existe,
  `demarrage.md`) ; aucune permission réseau sur Android, donc pas de proxy.
- Proxy saisi dans Kairos (hôte, port, identifiants) : écarté, le proxy du
  système suffit pour une seule requête GET.
- Envoi automatique du diagnostic : jamais (pas de réseau, pas de compte).
- Synchronisation entre appareils (#88) : ce choix de dossier en est une
  condition, pas la synchronisation elle-même.

## 2. Solution technique

### Dossier des données (`DataDirectory`, `DataFolder`, `desktopApp`)

- `DataDirectory.resolveAll(args, env, osName, home)` rend un `Resolved(dir,
  source, default, failed)` (`resolve(env, osName, home)`, sans argument ni
  dossier choisi, reste celui des tests et de l'existant) : `source` = `ARGUMENT` (`--data-dir=`), `ENVIRONMENT`
  (`KAIROS_DATA_DIR`), `CHOSEN` (fichier `data-location.txt` de la racine
  Kairos de platformdirs, hors de `v3`) ou `DEFAULT` (racine + `v3`, inchangé).
- `data-location.txt` : première ligne = dossier choisi ; deuxième ligne
  facultative `copy-from=<dossier>` = copie à faire au prochain démarrage.
  Lu au démarrage par `DataFolder.applyPending` : si le dossier choisi n'a pas
  de `kairos.db` et que la source en a une, `kairos.db` (et ses fichiers
  `-wal`/`-shm` s'ils existent) y est copié **avant** toute ouverture de base,
  puis la ligne `copy-from` est retirée. Échec (dossier non créable, copie
  impossible, base absente des deux côtés quand une copie était demandée) :
  le fichier est supprimé, Kairos ouvre le dossier source et `failed` porte le
  dossier écarté.
- `DesktopDataFolder` (`DataFolderService` de `AppServices`) : `current`,
  `defaultPath`, `fixedAtLaunch` (source `ARGUMENT` ou `ENVIRONMENT`), `pending` (dossier choisi pour le prochain
  démarrage), `failed`, `hasData(dossier)`, `pick()` (`JFileChooser`,
  dossiers seulement, hors fil d'interface), `choose(dossier)` (écrit
  `data-location.txt` ; vers le dossier par défaut, le fichier est supprimé
  et une copie n'est demandée que s'il n'a pas de base ; le dossier déjà
  ouvert : le choix en attente est annulé) ; aucune écriture quand `fixedAtLaunch`.
- `main` passe les arguments à `DataDirectory.resolveAll` ; l'instance unique,
  le journal de plantage et le journal de diagnostic suivent le dossier
  résolu.

### Journal de diagnostic (`Diagnostics`, `desktopApp` et `ui`)

- Interface `Diagnostics` (`ui/app`) : `enabled` (`StateFlow`),
  `setEnabled`, `log(event)`, `report()` ; `AppServices.diagnostics` (bureau
  seulement, `null` ailleurs).
- `FileDiagnostics(dossier)` : interrupteur = présence de `diagnostics.on`
  dans le dossier des données ; journal `diagnostics.log`, une ligne par
  événement « horodatage ISO, événement » ; au-delà de 256 Ko
  (`MAX_BYTES`), le fichier devient `diagnostics.1.log` (l'ancien est
  remplacé). `log` n'écrit rien si l'interrupteur est éteint et n'échoue
  jamais.
- Événements (vocabulaire fixe, sans texte saisi) : `start` (version, OS,
  Java, source du dossier), `db.open` (création ou version du schéma, nombre
  de tâches), `backup ok|failed`, `export ok|failed`, `import <genre>
  ok|<code d'erreur>`, `exchange pack|report applied|refused`, `update
  <résultat> <code HTTP> proxy=<oui|non>`.
- `report()` : en-tête (version, OS, architecture, Java, langue, source du
  dossier), puis les 200 dernières lignes du journal et les 200 dernières de
  `crash.log` ; `DiagnosticsCard` le met dans le presse-papiers
  (`LocalClipboardManager` ; presse-papiers indisponible : message d'erreur).
- Interface : `DiagnosticsCard` (catégorie À propos), `SwitchRow` et bouton
  « Copier le diagnostic ».

### Proxy (`Main`, `DesktopUpdates`)

- `main` pose `java.net.useSystemProxies=true` **avant toute connexion**, sauf
  si la propriété est déjà donnée. `HttpClient` utilise le sélecteur par
  défaut (`ProxySelector.getDefault()`), passé explicitement ;
  `DesktopUpdates.usesProxy(url)` dit si un proxy s'applique (journal). Le
  paramètre `log` de `DesktopUpdates` précède `fetch`, qui reste le dernier
  (les tests le passent en lambda finale).

### Décisions et alternatives écartées

- **Effet au prochain démarrage** (écarté : rouvrir la base à chaud) : la base
  ouverte, le verrou d'instance et le chrono restent cohérents.
- **Copie, jamais déplacement** : l'ancien dossier reste une sauvegarde ; le
  dire avant de le faire satisfait « jamais en silence ».
- **Interrupteur du journal propre à l'appareil** (fichier `diagnostics.on`),
  hors de `Settings` : un niveau de journal n'a pas à suivre un export vers un
  autre appareil ; aucune clé nouvelle dans l'export.
- **Pas de chemin dans le journal** : le chemin d'un dossier contient souvent
  le nom de la personne ; seule sa source (défaut, choisi…) est notée.
- **Proxy du système seulement** (choix du propriétaire) : une seule requête
  GET, facultative.
