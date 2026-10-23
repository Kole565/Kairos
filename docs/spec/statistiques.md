# Statistiques

_Rôle : un retour empirique sur la façon dont on estime, où va le temps et
où s'accumulent les retards, pour calibrer ses estimations et le score.
Fichiers couverts : `kmp/core/src/commonMain/.../core/stats/TaskStats.kt`
(calcul pur), `core/.../stats/StatsQuery.kt` (filtres, pur), `core/.../stats/StatsCsv.kt` (export CSV, pur),
`kmp/ui/.../stats/StatsScreen.kt`, `ui/.../stats/StatsCards.kt` (les cartes), `ui/.../stats/StatsExport.kt` (export). Les repères du guide des
points et les durées suggérées qui en découlent sont affichés par la vue
Jour (`vue-jour.md`). Tests : `core/.../stats/StatsDifferentialTest.kt`
(200 cas contre les fichiers d'or `stats.json`), `core/.../stats/StatsQueryTest.kt`,
`M4ScreensUiTest.statsShowWhatWasDoneAndTimed`, `calibratedPointsSuggestTheDurationAndFeedTheGuide`, `StatsFilterUiTest`,
`core/.../stats/StatsCsvTest.kt` (commun), `core/.../stats/StatsCsvGoldenTest.kt` (fichier d'or `stats-export.csv`) et
`StatsExportUiTest`._

État : **jalon M4** ; filtres interactifs (issue #114) et export CSV et image (issue #113) en 3.7.0. Les sorties sont figées par les fichiers d'or de
`StatsDifferentialTest` (`stats.json`, source de vérité depuis
`retrait-kairos2.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos collecte des tâches qualifiées (priorité, points, durée, type) et du
temps réel chronométré. Sans retour, on ne sait pas si l'on estime juste,
où passe le temps, ni si le backlog grossit.

### Comportement attendu (utilisateur)

Destination « Stats », en lecture seule. En tête (3.7.0, issue #114) :

- **Période** : boutons segmentés « 4 sem. », « 8 sem. », « 12 sem. », « Tout » ; la fenêtre des Réglages est choisie
  d'office (si elle vaut autre chose, « 6 sem. » par exemple, elle s'ajoute aux options). « Tout » remonte à la première
  activité (création d'une tâche, début d'une session) ; le débit hebdomadaire n'y montre que les **26 dernières
  semaines**, pour que la liste ne s'allonge pas sans fin.
- **« Rechercher / filtrer »** : la même carte que la vue Jour et la vue Semaine (texte, priorité, projet, type, points,
  « Réinitialiser »). Toutes les cartes se recalculent sur les tâches retenues et leurs sessions ; une ligne dit
  l'effectif retenu (« Filtré : 12 sur 40 tâches »). La **calibration** reste sur tout l'historique des tâches retenues :
  la période ne la change pas (comme avant).
- Le filtre et la période ne vivent qu'à l'écran : jamais écrits dans les réglages ni dans l'export de données ; quitter
  les Stats et y revenir rend la vue par défaut, identique à celle d'avant les filtres.
- **Exporter** (3.7.0, issue #113) : au bout de la ligne de la période, un bouton à icône « Exporter… » (info-bulle),
  présent seulement quand l'écran montre des chiffres. Il ouvre un menu :
  - « Tableaux (CSV) » : **un seul fichier** `kairos-stats-AAAAMMJJ-HHMM.csv`, une section par carte, exactement ce que
    l'écran montre pour la période et le filtre choisis ;
  - « Image : <titre de la carte> », pour chacune des sept cartes (chiffres clés, débit, calibration, où va le temps,
    quand tu travailles chaque type, flux et backlog, complétude) : un fichier
    `kairos-stats-<carte>-AAAAMMJJ-HHMM.png` de la carte seule, rendue hors écran à **600 dp de large, densité ×2**,
    quel que soit l'appareil, toujours en thème **clair** de la même graine (même quand l'écran est sombre,
    `apparence.md` § Thème sombre), avec en pied une légende « Kairos · vendredi 23 octobre 2026 ·
    8 sem. · Filtré : 12 sur 40 tâches » (le filtre seulement s'il est actif).

  Le fichier passe par la boîte d'enregistrement du système (téléchargement dans le navigateur), comme l'export de
  données ; message « Statistiques exportées. » ou « Image enregistrée. », « Le fichier n'a pas pu être lu ou écrit. »
  en cas d'échec ; rien n'est écrit dans la base. Seul l'espace Perso est exporté (l'écran ne montre que lui).

Sans aucune donnée : un état vide qui explique comment les chiffres se construisent (avec un filtre actif : « Rien pour
ce filtre. Change-le ou réinitialise-le plus haut. », le filtre restant visible pour le changer). Sinon, de haut en bas :

- **Quatre chiffres clés** sur la fenêtre récente (8 semaines par défaut,
  réglable dans Réglages → Statistiques, `reglages.md`) :
  tâches terminées, temps réel chronométré, délai médian de complétion (en
  jours ; alerte au-delà de 7), part des échéances tenues (alerte sous
  70 %).
- **Débit hebdomadaire** : par semaine de la fenêtre (semaines vides
  comprises), tâches terminées et points cumulés (vélocité).
- **Calibration de l'estimation** : temps réel médian par palier de points,
  sur tout l'historique (tâches terminées et chronométrées) ; si deux
  paliers se rejoignent, l'échelle ne discrimine pas. Puis le biais
  « ×1.32 : 7 h réel pour 5 h 20 estimé (n=12) » (point décimal, comme le
  score) et le verdict (« Tu
  sous-estimes » au-delà de ×1.1, « Tu surestimes » sous ×0.9, sinon
  « Estimations justes »), avec une alerte hors de ×0.8–×1.25.
- **Où va le temps réel** : part du temps chronométré de la fenêtre par
  type (« Sans type » compris), du plus au moins chronophage ; puis le
  focus : nombre de sessions et durée moyenne (beaucoup de sessions
  courtes = attention morcelée).
- **Flux et backlog** (état courant) : tâches à faire, âge médian, en
  retard, qui traînent (ces deux-là en alerte s'il y en a).
- **Complétude des métadonnées** : part des tâches à faire ayant des
  points, une durée estimée, un type (« 90 % · 9/10 »).
- **Quand tu travailles chaque type** (3.5, étape C1 de `planning-personnalise.md`) : pour chaque type (« Sans type » compris), du plus au
  moins chronométré, ses effectifs (« n=5 · 5 h », « peu fiable » avec l'icône d'alerte sous le seuil), la **tranche de 2 h qui le
  distingue** (« Se distingue à 14:00–16:00 : 80 % de son temps (×2.2 ton rythme habituel) », ou « Réparti dans la journée, sans plage
  marquée »), les trois plus grandes parts écrites et douze barres (une par tranche, de minuit à minuit) qui les illustrent. Observation
  seulement : rien ne change dans le planning.

Honnêteté statistique : chaque agrégat montre son effectif ; sous 3, il est
marqué « peu fiable » (icône d'alerte), mais reste affiché.

### Critères de succès

- Sorties identiques aux fichiers d'or (tableau de bord, repères du guide,
  calibration par type) sur 200 historiques aléatoires
  (`StatsDifferentialTest`).
- Une tâche terminée et chronométrée apparaît dans le débit et dans la
  calibration, « n=1, peu fiable » (`M4ScreensUiTest`).
- Aucune barre sans sa valeur écrite ; aucune information portée par la
  couleur seule.
- Sans filtre ni changement de période, le tableau de bord est **identique** à celui d'avant (mêmes entrées que les
  fichiers d'or) ; un filtre de type ne retient que ses tâches et leurs sessions ; « Tout » remonte à la première activité
  et borne le débit à 26 semaines ; une session d'une tâche disparue ne compte que sans filtre (`StatsQueryTest`) ; la vue
  par défaut passée par les filtres égale le tableau de bord sur les 200 cas des fichiers d'or
  (`StatsDifferentialTest.dashboard_matches_golden`).
- Choisir « 4 sem. » change le libellé des chiffres clés ; filtrer sur un type réduit l'effectif (« Filtré : 1 sur 2
  tâches ») ; un filtre sans résultat le dit et se réinitialise ; la ligne tient à 360 dp (`StatsFilterUiTest`).
- Le CSV d'un instantané fixé est identique, octet pour octet, au fichier d'or `stats-export.csv`
  (`StatsCsvGoldenTest`) ; un texte qui contient une virgule, un guillemet ou un saut de ligne est entre guillemets, un
  texte qui commence par `=`, `+`, `-` ou `@` est neutralisé, les nombres ont un point décimal quelle que soit la langue
  (`StatsCsvTest`).
- « Tableaux (CSV) » enregistre le CSV de la vue filtrée (le contexte dit la période et le filtre) ; « Image : Débit
  hebdomadaire » enregistre un PNG de 1 200 px de large ; rien n'est écrit dans la base ; le bouton a une aire tactile
  de 48 dp, il est absent sans données et le menu tient dans une fenêtre de 360 dp (`StatsExportUiTest`). Vérifié aussi
  dans Chromium sur la sortie web compilée (téléchargement du CSV et du PNG).

### Hors périmètre / différé

- Graphiques temporels fins, estimation par apprentissage ; un débit par mois pour « Tout ».
- Image de l'écran entier, PDF, export des prévisions de l'espace Équipe (`equipe-simulation.md`).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, l'écran Stats lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

### Calcul (`TaskStats`, `core`)

Pur : tâches, sessions, jour, instant, fuseau et réglages en paramètres.
`dashboard(tâches, sessions, aujourd'hui, réglages, maintenant, fuseau)`
reçoit **toutes** les tâches, archivées comprises.

- Fenêtre : `weeks = max(1, statsWindowWeeks)`, début = lundi de la semaine
  courante moins `weeks − 1` semaines. Sessions de la fenêtre :
  `TimeTracking.sessionsInRange` (date locale du début).
- Date de complétion d'une tâche faite = date locale de `updatedAt` (pas
  d'horodatage dédié).
- `throughputByWeek` : `WeekThroughput(lundi, terminées, points)` pour
  chaque semaine de la fenêtre, zéro compris.
- `fibonacciCalibration` : tâches faites avec points (≠ 0) et temps passé
  > 0 (sessions + saisie manuelle + temps rapporté,
  `spentMinutesByTask`), médiane par
  palier. `calibrationByType` : idem par type non vide. `Calibration(clé,
  n, médiane)`, `reliable` = n ≥ `MIN_SAMPLE` (3).
- `estimationBias` : tâches faites avec estimé (≠ 0) et temps passé > 0 ;
  ratio réel total ÷ estimé total.
- `timeByType` (sessions de la fenêtre, clé `""` = sans type,
  pourcentages arrondis), `focus` (sessions de durée > 0, moyenne arrondie).
- `backlogFlow` : à faire, âge médian (jours depuis la création), en retard
  (`Scheduling.urgencyBucket == 0`), qui traînent
  (`Staleness.daysStale`), délai médian de complétion et échéances tenues
  (faites dans la fenêtre, `done ≤ deadline`).
- `completeness` : sur les tâches à faire.
- `fibonacciReferences` : par palier ayant des tâches faites, la
  calibration du palier et les titres des deux plus récentes (par
  `updatedAt`), chronométrées ou non.
- Arrondis : `pyRound` (`kotlin.math.round`, au pair le plus proche : comportement propre de
  Kairos, figé par les fichiers d'or), médiane d'un effectif pair = moyenne des deux
  valeurs centrales.

### Filtres (`StatsQuery`, `core`, 3.7.0)

- `StatsQuery(filter: DayFilter, weeks: Int?)` : `weeks` nul = fenêtre des réglages, `ALL` (0) = tout. `StatsQuery.apply`
  rend un `StatsQuery.Input(tasks, sessions, settings, all, total)` :
  - sans filtre actif : toutes les tâches et **toutes** les sessions (y compris celles d'une tâche disparue), inchangées ;
  - filtre actif : `tasks.filter(filter::matches)` et les sessions de ces tâches seulement ;
  - `settings.statsWindowWeeks` remplacé par `weeks`, ou, pour `ALL`, par le nombre de semaines du lundi de la première
    activité (date locale de création d'une tâche retenue ou de début d'une session retenue) à aujourd'hui, au moins 1.
- `StatsQuery.dashboard(...)` = `TaskStats.dashboard` sur cette entrée, débit borné aux `ALL_THROUGHPUT_WEEKS` (26)
  dernières semaines pour `ALL` ; `StatsQuery.hours(...)` = `BestHours.measure` sur la même entrée. Les fichiers d'or ne
  passent pas par `StatsQuery` : la vue par défaut leur est identique par construction (test).

### Export CSV (`StatsCsv`, `core`, 3.7.0)

`StatsCsv.encode(query, input, dashboard, hours, today, now)` : pur, rend le texte du fichier à partir des mêmes
objets que l'écran (`StatsQuery.Input`, `TaskStats.Dashboard`, `BestHours.Result`) ; rien n'est recalculé.

- UTF-8 avec BOM, fin de ligne CRLF (RFC 4180), séparateur virgule. Une section = une ligne titre (son identifiant
  seul), une ligne d'en-têtes, ses lignes (zéro possible : l'en-tête reste), une ligne vide. Identifiants et en-têtes
  en anglais, stables quelle que soit la langue (comme les clés du JSON d'export).
- Nombres au point décimal (`StatsCsv.decimal`, arrondi au pair comme `pyRound`), dates ISO (`2026-10-23`), instant ISO
  UTC ; valeur absente (`null`) = cellule vide ; booléens `true` / `false` ; « Sans type » = cellule vide (clé `""`).
- Textes (types, filtre) : entre guillemets s'ils contiennent `,` `"` CR ou LF (guillemets doublés) ; préfixés d'une
  apostrophe `'` s'ils commencent par `=`, `+`, `-`, `@`, tabulation ou CR (un tableur les lirait comme formule).
- Sections, dans l'ordre de l'écran :

| Section | Colonnes | Lignes |
|---|---|---|
| `kairos_stats` | `key,value` | `format` (`kairos-stats`), `format_version` (1), `generated_at`, `today`, `period` (semaines, ou `all`), `window_weeks`, `window_start` (lundi), `window_end` (aujourd'hui), `filter_query`, `filter_priority`, `filter_project`, `filter_type`, `filter_points`, `tasks_kept`, `tasks_total` |
| `summary` | `metric,value` | `completed`, `tracked_minutes`, `median_completion_days`, `deadlines_met_pct`, `deadlines_total` |
| `throughput` | `week_start,completed,points` | une par semaine affichée (26 au plus pour « Tout ») |
| `calibration` | `points,tasks,median_minutes,reliable` | une par palier |
| `estimation_bias` | `ratio,real_minutes,estimated_minutes,tasks,reliable` | une, ou aucune sans biais ; ratio à deux décimales |
| `time_by_type` | `type,minutes,pct` | une par type |
| `focus` | `sessions,total_minutes,avg_session_minutes` | une |
| `hours_by_type` | `type,sessions,minutes,reliable,peak_start,peak_share_pct,peak_lift,share_00` … `share_22` | une par profil ; parts en % à une décimale, lift à deux décimales, pointe vide sans pointe |
| `backlog` | `metric,value` | `open`, `median_age_days`, `overdue`, `stale` |
| `completeness` | `field,with,total,pct` | `points`, `estimate`, `type` |

### Export à l'écran (`StatsExport`, `StatsCards`, `ui`, 3.7.0)

- Les sept cartes sont l'énumération `StatsCard` (`title`, `fileKey` : `key-figures`, `throughput`, `calibration`,
  `time-by-type`, `hours`, `backlog`, `completeness`) et `StatsCardView(carte, …)` les dessine : l'écran et l'image
  partagent le même code.
- `StatsExportButton` : `IconButton` (icône `Download`, 40 dp dessinés, aire tactile de 48 dp) dans un `TooltipBox` (`PlainTooltip` « Exporter… »),
  `DropdownMenu` : « Tableaux (CSV) » (icône `TableView`), puis « Image : <titre> » (icône `Image`) par carte.
- CSV : `StatsCsv.encode` puis `FileService.saveText`. Image : la carte est composée **hors écran** par
  `OffscreenCapture` : un `Layout` de taille nulle mesure son contenu (`StatsCardImage`, 600 dp de large) sans
  contrainte sous une densité forcée à 2 (police ×1) et le dessine **seulement** dans un `GraphicsLayer` (`drawWithContent` + `record`, jamais `drawLayer`) : rien
  n'apparaît à l'écran ; `StatsCardImage` se redessine dans `KairosTheme(LocalLightColorScheme)`. Après le premier dessin et deux images de plus (ressources chargées), `toImageBitmap()`, puis
  `FileService.saveImage`, puis le rendu caché disparaît. Fond `surface`, marge de 16 dp, légende en `bodySmall`
  `onSurfaceVariant` : « Kairos », date longue (`Dates.long`), période, `stats_filtered` si un filtre est actif.
- `FileService.saveImage(nom, ImageBitmap)` encode en PNG et enregistre ; par défaut (doublures de test), il lève
  `UnsupportedOperationException`, reçue comme une erreur de fichier. Bureau : `asSkiaBitmap` → `Image.encodeToData(PNG)`
  → `FileDialog`. Web : même encodage Skia, puis téléchargement (`KairosWeb.downloadBase64`). Android :
  `asAndroidBitmap` (copié en `ARGB_8888` s'il est matériel) → `Bitmap.compress(PNG)` → `CreateDocument("image/png")`.
- `saveText` choisit le type du fichier d'après l'extension du nom : `.csv` → `text/csv`, sinon `application/json`
  (Android : un `CreateDocument` par type ; web : type du `Blob`) ; sans cela, Android ajoutait `.json` au nom.

### Meilleures heures (`BestHours`, `core`, 3.5)

Pur : tâches, sessions, instant, fuseau et réglages en paramètres (`BestHours.measure`). Sessions de la même fenêtre que le tableau de bord
(`statsWindowWeeks`) ; **sessions de moins de 5 minutes ignorées** (démarrage-arrêt accidentel) ; une session ouverte court jusqu'à
maintenant. Les minutes d'une session sont **réparties entre les tranches de 2 h** qu'elle traverse, sur l'**heure locale** du fuseau passé
(minuit compris). **Pondération récente** : de 1 (aujourd'hui) à 1/2 (premier jour de la fenêtre). Par type (clé `""` = sans type ; une tâche
disparue y tombe) : `Profile(type, sessions, minutes, share, lift, peak, reliable)`.

- `share` : part du temps pondéré du type par tranche (somme 1). `lift` : cette part ÷ la part de **toutes** les tâches dans la même tranche
  (« je travaille à 10 h » n'est pas « les documents, c'est à 10 h »).
- `peak` : la tranche au plus fort lift parmi celles qui portent au moins 20 % du temps du type ; `null` quand le type se répartit sans pointe.
- `reliable` : au moins 5 sessions retenues **et** 120 minutes réelles ; sinon le profil reste affiché, marqué « peu fiable ».
- **Affinité (3.6, étape C2)** : `BestHours.affinity(settings, result)` rend `null` si `schedule.bestHours` n'est pas actif, sinon l'`Affinity` du moteur : pour chaque type
  **fiable** et non vide, le gain de chaque tranche est `lift − 1` borné à [0, 1] si elle porte au moins 10 % du temps du type (`AFFINITY_MIN_SHARE`), sinon 0 ; les
  heures préférées des réglages l'accompagnent et priment. `BestHours.strengthOf(niveau)` = 0,15 / 0,30 / 0,50. La carte Statistiques reste une observation : elle n'est pas
  filtrée par le réglage. Voir `ordonnancement.md` § Meilleures heures au créneau.
- Tri par temps réel décroissant, puis par nom. Tests : `BestHoursTest` (déterministe sur une base de sessions fixée), `BestHoursUiTest`.

### Écran (`StatsScreen`, `ui`)

- Recalculé quand la base, le jour, le filtre ou la période changent ; colonne défilante de
  960 dp au plus.
- En tête : `KairosSegmentedRow` de la période (options `periodOptions(réglages)` : 4, 8, 12, la fenêtre des réglages si
  elle n'y est pas, triées, puis « Tout » ; libellé `stats_period_weeks` / `stats_period_all`, `contentDescription`
  « Période »), puis `FilterCard` de la vue Jour (`ui/day/Filters.kt`), et, filtre actif, `stats_filtered` (« Filtré : n sur
  m tâches »). État local (`remember`) : rien n'est enregistré.
- `StatTile` : `surfaceContainerLow`, valeur en `headlineSmall` ; seuil
  franchi = contour `outline` et icône `Warning`. Les quatre tuiles d'une carte
  sont rangées par `TileFlow` (`StatsCards.kt`) : une tuile prend `weight(1f)` et
  au moins 150 dp ; **quatre par ligne si elles tiennent (636 dp), sinon deux**
  (`maxItemsInEachRow`), une seule si deux ne tiennent pas (à largeur fixe de
  200 dp, un téléphone n'en plaçait qu'une par ligne, écran trois fois trop long ;
  défaut vu sur les captures des magasins, `publication.md` ; entre 600 et 670 dp
  environ, un rangement 3 + 1 étirait la dernière, vu sur l'image d'une carte en
  3.7.0). `fillMaxRowHeight()` : les tuiles d'une même ligne ont la même hauteur,
  même quand un libellé passe à la ligne.
- `Panel` : `OutlinedCard`, icône, titre, phrase de rôle.
- `BarRow` : libellé (96 dp), piste neutre `surfaceContainerHighest`,
  remplissage **primaire** à bouts arrondis de 4 dp, valeur écrite à
  droite, entre 110 et 200 dp (icône `Warning` si peu fiable) : la piste
  garde de la place sur un téléphone. Débit et calibration : longueur
  relative au maximum ; types et complétude : pourcentage.
- Ratio du biais à deux décimales ; durées par `duration()`.

- Carte « Quand tu travailles chaque type » (`HoursProfile`, 3.5) : par type, nom et effectifs, phrase de pointe, douze barres d'une seule teinte
  (`primary`, hauteur relative à la plus grande part du type, jamais sans valeur écrite : les trois plus grandes parts sont écrites dessous), la
  frise est masquée aux lecteurs d'écran (le texte porte l'information).

- Briques partagées avec l'espace Équipe (jalons E3 et E4,
  `equipe-charge.md`) : `StatTile` et `Panel` sont `internal` ; `StatTile`
  accepte une `note` (pastille en contour + icône) ; `BarRow` accepte
  `overflow` (part au-delà de 100 % en `error`), `mark`, `detail` et
  `stackedWhenNarrow`, et partage son tracé (`BarTrack`). Sans ces options,
  le rendu des statistiques est inchangé.

### Décisions et pièges tracés

- **Filtres : la carte de la vue Jour** (choix du propriétaire, issue #114) : les mêmes facettes partout, plutôt que deux
  menus propres aux Stats ; la priorité et les points viennent avec.
- **La période ne touche pas la calibration** (choix du propriétaire) : elle porte sur tout l'historique depuis le
  jalon M4 ; le filtre, lui, la restreint (« mes estimations sur les tâches Dev »).
- **« Tout » borne le débit à 26 semaines** : une ligne par semaine sur deux ans faisait une liste de cent lignes ; un
  débit par mois est différé.
- **Sessions d'une tâche disparue** : comptées sans filtre (le tableau de bord d'avant les comptait), écartées dès qu'un
  filtre est actif (elles ne correspondent à aucune tâche retenue).
- **`remember` et non `rememberSaveable`** : `DayFilter` n'a pas de sauvegarde d'état Android ; comme la vue Jour et la
  vue Semaine, le filtre ne survit pas à la recréation de l'écran, et la vue par défaut revient en quittant les Stats.

- **Un seul CSV par sections** (choix du propriétaire, issue #113) : un seul sélecteur de fichier sur Android et le
  web ; plusieurs fichiers en auraient demandé un par tableau. Un tableur l'ouvre tel quel ; une section se copie.
- **Virgule et point décimal** (choix du propriétaire) : CSV standard, lisible par un script ; le point-virgule
  d'Excel en français est écarté (il n'est pas standard), le BOM permet à Excel de lire les accents.
- **En-têtes en anglais, identifiants stables** : indépendants de la langue comme les nombres ; un en-tête traduit
  changerait le fichier selon la langue de l'interface.
- **Neutraliser les formules** : un type ou un texte de filtre « =… » deviendrait une formule dans un tableur ;
  l'apostrophe en tête est la parade usuelle (OWASP « CSV injection »), au prix d'un caractère.
- **Image d'une carte, 600 dp ×2, avec légende** (choix du propriétaire) : une image nette et de même taille sur tous
  les appareils, que la légende replace dans son contexte (date, période, filtre) une fois collée dans un rapport ;
  l'image de l'écran entier est écartée (trop haute pour un rapport).
- **Rendu hors écran par `GraphicsLayer`** et non par `ImageComposeScene` (celle des captures des magasins) : elle
  n'existe que sur le bureau ; le calque fonctionne sur les trois plateformes et ne dessine rien à l'écran.
- **Encodage PNG par la plateforme** (`saveImage`) : Skia sur le bureau et le web, `Bitmap.compress` sur Android ;
  un encodeur PNG maison sans compression aurait produit des fichiers de plusieurs mégaoctets.
- **Tests différentiels en UTC, écran en fuseau local** : les fichiers d'or
  (`stats.json`) portent des horodatages UTC, lus comme tels par
  `StatsDifferentialTest` ; l'application passe le fuseau du
  système, comme partout (règle tracée dans `vue-jour.md`).
- **Barres d'une seule teinte, pas de légende** : une série par graphique ;
  la couleur n'encode rien que le texte ne dise déjà (charte : pas d'ambre,
  alertes par la forme).
- **Alertes par contour et icône**, y compris pour « en retard » et les
  échéances tenues : un chiffre
  statistique n'est pas une erreur ; le rouge reste aux lignes de tâches
  en retard.
- **Pas de bibliothèque de graphiques** : des `Box` suffisent (dépendances
  libres et minimales, `architecture.md`).
- **Guide et durées suggérées sur tout l'historique**, archivées comprises :
  une tâche archivée a été réellement faite ou
  chronométrée.
