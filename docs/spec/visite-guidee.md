# Visite guidée enrichie et catalogue des fonctionnalités

_Rôle : faire connaître à l'utilisateur toutes les fonctionnalités de Kairos,
au bon moment, depuis une source unique. Fichiers couverts :
`kmp/ui/.../ui/guide/FeatureCatalog.kt`, `GuideContent.kt`, `GuideSpot.kt`, `GuideDialog.kt`,
`DiscoverDialog.kt`, `HelpDialog.kt`, `ui/screens/HomeScreen.kt`,
`ui/settings/SettingsScreen.kt`, `ui/navigation/AppShell.kt`, et la mise en valeur des sections dans `DayScreen.kt`,
`StatsScreen.kt` et les cartes des Réglages, ressources `values/`
et `values-fr/`. Tests : `FeatureCatalogTest`, `GuideStepsTest`, `M6GuideUiTest`, `GuideSpotUiTest`,
`StringsParityTest`._

État : **issue #64 (retour du propriétaire)**, spécifiée le 2026-10-13,
**implémentée en 3.4.0** (voir « Écarts avec la spécification initiale ») ; cartes qui ouvrent la section précise d'un
écran en 3.7.0 (issue #95).
**Rouvre explicitement** les décisions de `accueil.md` § La visite guidée (deux
listes de 6 étapes écrites en dur, aucune persistance) ; le reste de `accueil.md`
est inchangé.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La visite actuelle compte deux listes de six étapes fixes. Elle ne parle ni de
l'équipe, ni des sous-tâches, ni de la frise et du temps réel, ni des
statistiques, ni des réglages, et rien ne dit ce qui est nouveau d'une version à
l'autre. Les chantiers de la 3.4 (horaires par jour, durées apprises, déplacer
une tâche…) ajoutent des fonctionnalités qu'il faut faire
découvrir sans que l'utilisateur ait à lire une documentation.

### Comportement attendu (utilisateur)

1. **Une visite initiale courte** (six étapes environ) reste proposée à
   l'accueil du premier lancement.
2. **Un hub « Découvrir Kairos »**, accessible depuis Réglages et l'aide, liste
   des **chapitres** et permet d'en rouvrir un à tout moment : Premiers pas ;
   Planifier (priorité, points, score, horaires par jour, heures préférées) ;
   Suivre (chrono, durées apprises, statistiques) ; Organiser (dépendances,
   sous-tâches, récurrences, déplacer) ; Équipe (si l'espace est activé) ;
   Réglages.
3. **Chaque carte pointe vers l'écran** qu'elle décrit (« Voir l'écran »), vers la
   section précise quand elle existe (3.7.0, issue #95) : l'écran s'ouvre, **défile** jusqu'à la section et la
   **met en valeur** environ 3 s (contour primaire de 2 dp et étiquette « Ici » avec icône, qui apparaissent et
   disparaissent sans animation ; le lecteur d'écran annonce « Ici »). Correspondances :
   - Réglages : « Plusieurs plages et contextes » → carte « Contextes » ; « Horaires par jour » → « Horaires par jour de la
     semaine » (catégorie « Journée et horaires ») ; « Planifier autour de tes meilleures heures » → carte « Meilleures heures » ;
     « Durées apprises » → carte « Durées apprises » (catégorie « Planification ») ; « Tes données et ton manager » → carte « Données »
     (catégorie « Données »). La catégorie s'ouvre d'abord (`reglages.md`). « Des réglages clairs… » ouvre la liste des
     catégories, sans section.
   - Vue Jour : « Bloqueurs » → section « Bloquées », ouverte ; « Sous-tâches » → « Tâches mères en cours », ouverte ;
     « Qualifier en un clic » et « Priorité, points et estimation » → « À traiter » ; « Faire dans l'ordre » et « Score et placement » →
     « Aujourd'hui, dans l'ordre » ; « Suivre le temps » → carte « Maintenant ».
   - Statistiques : « Quand tu travailles chaque type » → son panneau.
   - Les autres cartes ouvrent l'écran en haut, comme avant.
   Section absente (vide, par exemple aucune tâche bloquée, ou des statistiques sans données) : l'écran s'ouvre en
   haut et un message bref le dit (« Aucune tâche bloquée pour l'instant. »).
4. **« Nouveau dans cette version »** : sur l'accueil, une section liste les
   cartes ajoutées par la version courante, masquable pour la session. Rien
   n'est mémorisé d'une version à l'autre.
5. **L'aide d'un écran** (bouton « ? ») et la visite lisent les **mêmes
   textes** ; aucun texte n'est écrit à deux endroits.
6. L'accompagnement de la **première tâche** (`premiere-tache.md`) fait partie
   des chapitres et réutilise ces cartes.

### Critères de succès

- Chaque carte du catalogue a ses textes FR et EN et une cible qui existe
  (`FeatureCatalogTest`).
- Chaque fonctionnalité listée dans `README.md` correspond à au moins une carte
  (`FeatureCatalogTest.readmeFeaturesAreCovered`, sur une liste d'identifiants
  maintenue à côté du catalogue).
- La visite initiale, l'aide et la section « Nouveau » lisent le catalogue
  (`GuideStepsTest`, `M6GuideUiTest` mis à jour).
- Les cartes « Équipe » n'apparaissent que si l'espace est activé.
- Textes FR/EN (`StringsParityTest`), 360 dp sans débordement, cibles de 48 dp,
  captures `--self-test` regardées.
- Une section visée appartient à l'écran de sa carte, et une section des Réglages à une catégorie
  (`FeatureCatalogTest.sectionsBelongToTheScreenTheyOpen`).
- « Durées apprises » ouvre la catégorie « Planification » et met sa carte en valeur ; « Bloqueurs » ouvre la section
  « Bloquées » et la met en valeur ; sans tâche bloquée, le message le dit ; la mise en valeur s'efface d'elle-même
  (`GuideSpotUiTest`).

### Hors périmètre / différé

- Mémoriser ce que l'utilisateur a « vu » (option ultérieure : `Settings.guide`
  nullable, qui resterait nul tant que rien n'est écrit).
- Visite interactive qui manipule l'écran à la place de l'utilisateur.
- Vidéos ou animations.

## 2. Solution technique

### Catalogue piloté par les données

`FeatureCatalog.cards` (`ui/guide/FeatureCatalog.kt`) : une liste de `FeatureCard(id, chapter, icon, title, body, target,
since, tour)`, **dans l'ordre de la visite**.

- **Chapitres** (`GuideChapter`) : `FIRST_STEPS` (capture, qualification, « faire dans l'ordre », « Découvrir Kairos »,
  première tâche), `PLAN` (priorité, points et estimation ; score et placement ; plages et contextes ; meilleures heures, 3.6 ; horaires par jour), `FOLLOW` (chrono ;
  durées apprises ; semaine ; statistiques), `ORGANIZE` (sous-tâches ; récurrence et déplacer ; bloqueurs ; créneaux et
  deep work), `TEAM` (les six cartes d'équipe), `SETTINGS` (réglages clairs ; données). `FeatureCatalog.chapters(teamEnabled)`
  n'offre `TEAM` que si l'espace Équipe est activé.
- **Visites** : `GuideSteps.of(GuideKind.PERSONAL)` = les cartes `tour = true` (six, comme avant) ;
  `GuideSteps.of(GuideKind.TEAM)` = le chapitre `TEAM` (six). Les textes des douze cartes d'avant gardent leurs
  chaînes `guide_you_*` et `guide_team_*` ; les autres sont `card_<id>_title` / `card_<id>_body`.
- **Cibles** (`GuideTarget`) : `Personal(destination, section?)`, `Team(destination, exchanges)`, `Settings(section?)` et
  `FirstTask` (lance l'accompagnement de `premiere-tache.md`, sans changer d'écran ; le bouton devient « Créer ma première
  tâche »).
- **Sections** (`GuideSection`, issue #95) : `DAY_INBOX`, `DAY_AGENDA`, `DAY_NOW`, `DAY_BLOCKED`, `DAY_PARENTS`,
  `SETTINGS_CONTEXTS`, `SETTINGS_HOURS`, `SETTINGS_BEST_HOURS`, `SETTINGS_LEARNED`, `SETTINGS_DATA`, `STATS_HOURS` ; chacune
  connaît son écran (`screen`), sa catégorie des Réglages (`settingsCategory`) et son message d'absence (`missing`, pour
  les sections qui peuvent manquer). `AppShell.openTarget` pose la demande dans `NavState.guideSpot`
  (`GuideSpotState.pending`) après avoir ouvert l'écran ; pour les Réglages, `onOpenSettings()` puis
  `nav.settings.category = section.settingsCategory`. Naviguer à la main (destination, espace, roue crantée) efface une
  demande en attente (`KairosApp`).
- **Mise en valeur** (`GuideSpot.kt`) : `LocalGuideSpot` fournit l'état aux écrans (`AppShell`). `GuideMark(section)`
  dessine, tant que `shown == section`, un contour `BorderStroke(2.dp, primary)` aux coins de la carte
  (`shapes.medium`) et l'étiquette « Ici » (`primary` / `onPrimary`, icône `Spot`, `liveRegion` poli) en haut à droite ;
  `GuideSpotState.show(section)` la pose 3 s (`SPOT_MILLIS`) puis l'efface si rien ne l'a remplacée.
  `GuideSpot(section)` = `GuideMark` + `BringIntoViewRequester` : quand la demande en attente est la sienne, il la
  consomme, attend deux images (l'écran doit être mesuré) et se fait défiler à l'écran. Écrans à colonne défilante :
  les cartes des Réglages (`ContextsCard`, `ScheduleCard`, `BestHoursCard`, carte « Durées apprises » de
  `SettingsFormCards`, `DataCard`) et le panneau « Quand tu travailles chaque type » de `StatsScreen`.
- **Vue Jour** (liste paresseuse : une ligne hors de l'écran n'est pas composée, `bringIntoView` n'y peut rien) :
  `DayScreen` consomme la demande lui-même : ouvre la section repliable (`sectionOpen`), attend la composition, puis
  `listState.scrollToItem(positions[clé])` et `show(section)` ; `GuideMark` entoure l'élément de tête (« inbox-title »,
  « agenda-title », « now », « section-blocked », « section-parents »). Clé absente de `positions` (section vide) : le
  message `missing` par `LocalMessages`, et l'écran reste en haut. Statistiques sans aucune donnée (panneau absent) :
  même message.
- **Captures de l'auto-test** : `desktop-guide-spot.png` (900 dp) et `desktop-guide-spot-narrow.png` (360 dp), la carte
  « Durées apprises » mise en valeur et figée (`GuideSpotState.shown` posé d'avance).
- **`since`** : version de livraison ; `FeatureCatalog.newIn(version)` rend les cartes de **mêmes numéros majeur et mineur**
  (`3.4.1` ou `3.4.0-beta.2` gardent la section de la `3.4.0` ; une version illisible ou sans carte rend une liste vide).
- **Lien avec le README** : `FeatureCatalog.README_FEATURES` associe chaque fonctionnalité en gras de la section
  « Features » de `README.md` à une carte ; `FeatureCatalogTest.readmeFeaturesAreCovered` lit le README et compare.
- **`GuideDialog`** accepte n'importe quelle sélection (`GuideDialog(steps, key, …)`, `GuideDialog(kind, …)` en est la
  forme courte) et garde la mesure du plus long titre et du plus long texte.
- **Hub « Découvrir Kairos »** (`DiscoverDialog`) : « Créer ma première tâche » puis un chapitre par ligne avec son nombre
  de cartes ; choisir un chapitre ferme le hub et ouvre ses cartes dans `GuideDialog` ; accessible depuis l'Accueil (bouton),
  l'aide (entrée « Découvrir Kairos ») et les Réglages (carte « Découvrir Kairos » de la catégorie « À propos », `reglages.md`) ; états `discover` et `chapter` de
  `AppShell` (`rememberSaveable`, rien n'est mémorisé au-delà).
- **« Nouveau dans cette version »** : `HomeScreen(version = KairosBuild.VERSION_NAME)` affiche, sous le bloc teinté, les
  cartes de `newIn(version)` (toute la carte ouvre sa cible) avec un bouton « Masquer » valable pour la session
  (`rememberSaveable`).

### Persistance

Aucune : la section « Nouveau » dépend de la version de l'application, pas d'un
« vu ». Un éventuel `Settings.guide` suivrait le patron « nul par défaut » du
reste de la 3.4 (`planning-personnalise.md`).

### Règle de livraison

Toute fonctionnalité visible par l'utilisateur (donc dans le README) ajoute ou
met à jour une carte dans le même changement ; `FeatureCatalogTest` casse sinon (règle consignée dans la skill `kairos-spec`, § 4).

### Décisions et pièges tracés

- **Sections : une énumération plutôt qu'une clé texte** (issue #95) : le compilateur garantit qu'une section citée
  existe ; le test vérifie qu'elle appartient à l'écran de sa carte. Écarté : des chaînes libres (« Day.Blocked »), qu'une
  faute de frappe rendait silencieuses.
- **Mise en valeur : contour et étiquette « Ici », pas la couleur seule** (choix du propriétaire, 2026-10-22) : même
  langage que la ligne mise en évidence par l'accompagnement de la première tâche (contour primaire de 2 dp). Sans
  animation de mouvement (respecte « réduire les animations ») ; 3 s, puis elle s'efface d'elle-même.
- **Section vide : le dire** (choix du propriétaire) : l'écran reste en haut et un message bref évite de chercher une
  section qui n'existe pas. Écarté : afficher la section vide le temps de la visite (une section fantôme dans la liste).
- **La vue Jour défile elle-même** : sa liste est paresseuse ; `bringIntoView` ne vaut que pour un élément composé.

- **Catalogue unique** (écarté : garder des listes par écran, qui divergent) : la
  dérive entre visite, aide et README est le défaut à éviter.
- **Pas de « vu » persistant** (écarté pour la 3.4) : évite un réglage de plus ;
  la section « Nouveau » est peu intrusive.
- **Visite initiale courte** (écarté : une visite exhaustive d'emblée) : un
  nouveau venu abandonne une visite de vingt étapes ; le hub porte la profondeur.
- **Titres de l'étape** : le dialogue mesure déjà le plus long titre et le plus
  long texte pour que les boutons ne sautent pas ; le catalogue conserve ce
  comportement (`GuideDialog`).

### Écarts avec la spécification initiale (décidés à l'implémentation)

- **Pas de clé de section dans `GuideTarget`** (ex. `Day.Blocked`) en 3.4 : une carte ouvrait l'écran, pas un endroit
  précis. **Repris en 3.7.0** (issue #95) avec `GuideSection`, une fois le défilement ciblé disponible (enregistrement
  des positions de la vue Jour, `ErrorReveal` et catégories des Réglages).
- **L'aide d'un écran garde ses textes `help_*`** (mode d'emploi de l'écran) ; elle renvoie au hub par une entrée. Le catalogue
  décrit les fonctionnalités, pas le mode d'emploi de chaque écran : les deux ne se recouvrent pas, donc rien n'est écrit
  deux fois.
- **Chapitres de six cartes ou moins** : « Premiers pas » en compte cinq (dont « Découvrir Kairos » et la première tâche).
