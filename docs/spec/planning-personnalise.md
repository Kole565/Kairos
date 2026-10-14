# Planning personnalisé : ergonomie, horaires par jour et par contexte, meilleures heures

_Rôle : rendre Kairos utilisable pour tous les usages (travail, maison, rythmes
variés) sans rien changer pour qui ne touche pas aux réglages. Fichiers
couverts (à venir) : `kmp/core/.../model/Settings.kt` (`schedule`),
`core/engine/Scheduling.kt` (fenêtres), nouveau `core/engine/Schedule.kt`,
`core/day/DayView.kt`, `core/stats/` (meilleures heures), `core/settings/SettingsForm.kt`,
`ui/day/EditTaskDialog.kt`, `ui/week/WeekScreen.kt`, `ui/settings/`. Tests :
`DifferentialTest`, `TeamIsolationTest`, tests nommés dans les critères._

État : **issue #64**, **spécifiée le 2026-10-13, non implémentée**. Étude et
conception seulement : aucun code n'a été écrit, aucune spec existante n'est
modifiée (voir « Impacts sur les specs existantes »).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une utilisatrice ou un utilisateur (issue #64) veut se servir de Kairos pour
toute sa vie, pas seulement pour un bureau de 9 h à 17 h :

1. des **horaires différents selon le jour** et selon la **catégorie** de
   tâche (travail le matin, maison le soir, week-end libre) ;
2. pouvoir **adapter le planning à son rythme** (« documents vers 12 h, mails
   vers 18 h ») ;
3. moins de **friction** au quotidien : dates à saisir en texte, déplacer une
   tâche, comprendre pourquoi une tâche est bloquée, réglages touffus.

Contrainte posée par le propriétaire : **tout doit fonctionner comme avant
pour qui ne vérifie jamais les réglages.** Les échelles de priorité et de taille
personnalisables (4e demande de l'issue) sont écartées pour l'instant.

### Comportement attendu (utilisateur)

**Lot A : ergonomie et réglages** (aucun effet sur les valeurs par défaut)

1. **Dates** : un sélecteur de date et des raccourcis (« Aujourd'hui »,
   « Demain », « Lundi prochain », « Dans une semaine », « Effacer ») pour
   l'échéance, la date programmée et le jour d'un créneau. La saisie au clavier
   accepte aussi `2026-9-30` et, en français, `30/09/2026`. Le libellé ne
   répète plus « (AAAA-MM-JJ) ».
2. **Déplacer** : une action de ligne « Déplacer… » (demain, lundi, dans une
   semaine, choisir une date) qui reporte la **date programmée** de la tâche
   (elle quitte « Aujourd'hui » jusqu'à ce jour), avec confirmation et
   possibilité d'annuler. « Décaler » garde son sens (échéance au jour ouvré
   suivant). La vue Semaine montre aussi les tâches « programmées pour » un
   jour et ouvre leur fiche.
3. **Blocage** : après avoir bloqué une tâche, un retour dit où elle est allée
   (« X attend Y », section « Bloquées » ouverte pour cette fois) ; l'aide de la
   fiche dit ce que fait un bloqueur ; les « Options avancées » de la fiche
   s'ouvrent d'office quand un bloqueur ou une date programmée existe ; le
   bloqueur porte un badge « bloque X ».
4. **Réglages** : libellés en langage clair (« WSJF » retiré des titres et des
   aides de l'agenda) ; langue et apparence en tête ; un groupe replié
   « Avancé : comment les tâches sont ordonnées » (ouvert d'office sur une
   erreur de saisie) ; un bouton **« Restaurer les valeurs par défaut »** qui
   remplit le formulaire, exige « Enregistrer » et ne touche jamais l'espace
   Équipe.
5. **Deux bugs probables, à vérifier avant tout** (annoncés comme *à
   vérifier*, pas comme avérés) : (a) une tâche « Décalée » reste dans
   « Aujourd'hui » (seule une date programmée future la masque) ; (b) une tâche
   bloquée non qualifiée serait listée à la fois dans « À traiter » et dans
   « Bloquées ». Un test qui échoue est écrit d'abord, puis la correction.
   Pour (a), le propriétaire tranche : « Décaler » masque-t-il la tâche jusqu'à
   la nouvelle échéance ?

**Lot B : horaires par jour et par contexte**

6. Chaque jour de la semaine peut être : « comme d'habitude » (la fenêtre
   actuelle), « personnalisé » (une ou plusieurs plages avec début, fin et
   contexte) ou « libre » (aucune plage).
7. Un **contexte** (« Travail », « Maison ») est un nom et un ensemble de
   **types de tâche** (le type existant, texte libre). Une tâche dont le type
   n'est dans aucun contexte est **sans contexte** : elle va dans n'importe
   quelle plage. Aucune migration : le type est déjà sur chaque tâche.
8. Une tâche d'un contexte n'est placée que dans une plage de ce contexte.
   Si ce contexte n'a aucune plage ce jour-là, elle va dans « Sans créneau »
   avec sa raison (« pas de plage Maison aujourd'hui »).
9. La frise et le temps réel du jour affichent les plages ; les trous entre
   plages sont grisés ; le temps disponible est la somme des plages.
10. Un type renommé ou supprimé est signalé « type inconnu » dans le contexte,
    jamais perdu en silence.

**Lot C : meilleures heures détectées automatiquement**

11. Kairos observe, par type de tâche, **quand tu travailles vraiment** (le
    chrono peut être lancé sur n'importe quelle tâche depuis n'importe quel
    classement : ces sessions sont la donnée). Il le montre d'abord
    (étape C1, aucun effet sur le planning) : carte Statistiques « Quand tu
    travailles chaque type », par tranche de 2 h, avec les effectifs et la
    mention « peu fiable » sous le seuil.
12. Ensuite, et **seulement si l'utilisateur l'active** (étape C2, désactivé
    par défaut), le planning préfère, à score égal, les créneaux où ce type est
    habituellement travaillé. Une **préférence manuelle** par type (« vers
    12 h ») prime sur la détection. « Pourquoi à cette place ? » le mentionne.

### Critères de succès

- Par défaut rien ne change : `DifferentialTest` (480 cas) et
  `TeamIsolationTest.soloExportIsByteIdentical` passent sans modification ;
  le fichier d'or d'export n'a aucun champ nouveau ; sans `schedule` les
  sorties du moteur sont identiques (test `ScheduleDefaultsTest`).
- Résolveur de fenêtres : un test par jour de semaine, jour libre, plages
  multiples, tâche sans contexte, contexte sans plage, type renommé, conservation
  « une seule liste » (`ScheduleWindowsTest`).
- Mesure des meilleures heures déterministe sur une base de sessions fixée,
  seuil de fiabilité, session à cheval sur deux tranches, fuseau explicite,
  boost nul quand l'option est désactivée (`BestHoursTest`).
- Ergonomie : les deux bugs ont chacun leur test (`DayViewTest`), l'analyse de
  dates souples a ses cas (`DatesTest`), « Restaurer » ne modifie pas `team`
  (`SettingsFormTest`).
- Interface : `--self-test` et captures des magasins regardés (360 dp sans
  débordement, cibles de 48 dp, textes FR/EN, conforme MD3).

### Hors périmètre / différé

- **Échelles libres** de priorité et de points (suggestion 2 de l'issue) :
  trop risquées (`FIBONACCI_SCALE` sur 11 sites, `PRIORITY_VALUES` sur 15,
  27 libellés par langue, calibrage, échanges d'équipe, exports). Seuls des
  **libellés personnalisés** des niveaux (jamais le calcul) seraient peu
  coûteux ; réévalués après retours.
- Glisser-déposer (décision d'accessibilité déjà consignée).
- Apprentissage entre appareils ou d'équipe, prédiction de durée (voir
  `durees-apprises.md`), notifications.
- Capacité d'équipe par plage (voir « Décisions »).

## 2. Solution technique

### Principe directeur : « nul par défaut »

Toute nouveauté de réglage passe par un objet **nullable**
`Settings.schedule: ScheduleSettings? = null`, sur le patron de
`Settings.team` (`explicitNulls = false`, l'objet reste nul tant que la valeur
écrite est celle par défaut). Nul ⇒ le résolveur renvoie l'ancienne fenêtre
unique ⇒ mêmes sorties, export et fichier d'or inchangés
(`solo-export-v1.json`). `ExportCodec.check` fait aller-retour les réglages via
`SettingsForm` : le formulaire doit donc porter `schedule` sans le créer.

### Lot A

- **Dates** : `DateChoice` (aujourd'hui dans `ui/team/forecast/ForecastPanels.kt`,
  `DatePicker` M3 déjà utilisé par l'espace Équipe) est remonté dans `ui/day`
  et partagé par `DateField` (`ui/day/EditTaskDialog.kt`), la fiche d'équipe et
  le champ du créneau. `Dates.parseDate` accepte `AAAA-M-J` et, selon la langue,
  `JJ/MM/AAAA`. Les raccourcis se calculent avec l'heure en paramètre (`core`
  reste pur).
- **Déplacer** : nouvelle action de `TaskRow` qui appelle le dépôt pour écrire
  `scheduledDate` ; message et annulation via `LocalMessages`. « Décaler »
  (`KairosRepository.snooze`) est inchangé. `WeekView` (`core/week`) ajoute les
  tâches dont `scheduledDate` tombe dans la semaine ; la carte ouvre la fiche
  (rouvre la mention « différé » de `vue-semaine.md`).
- **Blocage** : après `setBlockers`, un message ; `DayScreen` ouvre « Bloquées »
  pour ce coup ; `EditTaskDialog` ouvre « Options avancées » si
  `blockerIds` ou `scheduledDate` existent ; `TaskRow` affiche un badge « bloque
  X » calculé depuis le graphe de dépendances (`core/engine/Dependencies.kt`).
- **Réglages** : `SettingsScreen` / `SettingsFormCards` regroupent les champs
  avancés sous un groupe replié ; « Restaurer » remplit `SettingsForm` avec
  `Settings()` privé de `team`. Les tests qui cliquent sur les libellés
  (`SettingsLayoutUiTest`, `M5ScreensUiTest`, `ForecastUiTest`) sont mis à jour
  avec les nouveaux textes.
- **Bugs** : (a) le filtre « Aujourd'hui » de `DayView` ne masque que les
  tâches à `scheduledDate` future ; (b) `toProcess` est calculé avant le filtre
  des bloquées. Test d'abord, puis correction.

### Lot B

- **Modèle** (`core/model`, pur) : `ScheduleSettings(weekdays:
  Map<Weekday, DaySchedule>, contexts: List<Context>)` ; `DaySchedule` =
  `DEFAULT` | `FREE` | liste de `WorkWindow(startMinute, endMinute, context?)` ;
  `Context(name, types: Set<String>)`.
- **Résolveur pur** `Schedule.windowsFor(day, settings): List<Window>` dans
  `core/engine` ; sans `schedule`, renvoie `[workdayStartHour, workdayEndHour)`
  pour les sept jours (les week-ends restent planifiés comme la semaine, ce
  que figent les 480 scénarios).
- **Moteur** : `buildDaySchedule` (`Scheduling.kt`, après la construction des
  obstacles) boucle sur les plages dans l'ordre ; réunions, épinglées et deep
  work restent des obstacles communs. Le **filtre de contexte s'applique
  seulement au choix au créneau** (avant `minWith(selectionKey)`), jamais à
  `sortKey` ni au score WSJF affiché. Chemin à une plage = code actuel.
- **Frise et Jour** : `DayView` expose les plages ; la frise et le rail du
  temps réel utilisent ces plages à la place de `topMinutes` et des
  graduations fixes ; `available` = somme des plages.
- **Gardés tels quels** : capacité d'équipe (`Capacity`, `todayFraction`,
  `busyHours`, `defaultHoursPerDay`) lit toujours `workdayStartHour` et
  `workdayEndHour` ; le creux de l'après-midi reste en heures absolues.
- **Réglages** : nouvelle carte « Horaires » (par jour : comme d'habitude /
  personnalisé / libre ; plages ; contextes et leurs types).
- **Phases** : B1 = une plage par jour de semaine et jours libres ; B2 =
  plages multiples et contextes.

### Lot C

- **Mesure** (fonction pure dans `core/stats`, fuseau en paramètre comme
  `DayView`) : par type et par tranche de 2 h, part du temps chronométré sur
  `statsWindowWeeks` (8 par défaut) ; les minutes d'une session sont réparties
  entre les tranches traversées ; sessions < 5 min ignorées ; pondération
  récente. Comparaison à la distribution **globale** de l'utilisateur
  (« lift » = part du type ÷ part de toutes les tâches) pour ne pas confondre
  « je travaille à 10 h » et « les documents, c'est à 10 h ». Seuil de
  fiabilité comme le « peu fiable » des stats (minimum de sessions et
  d'heures par type), sinon rien n'est proposé.
- **C1 observer** : carte Statistiques, aucune écriture, aucun effet.
- **C2 appliquer** : réglage `Settings.schedule.bestHours` (force bornée,
  0 = neutre, désactivé par défaut) ; au créneau,
  `effort' = effort ÷ (1 + force × boost(type, heure))`, même patron que
  `dipAdjustedEffort` (le creux est le modèle d'un ajustement doux : il ne
  touche que le choix au créneau). Score affiché inchangé ; jamais sur les
  tâches en retard ni remontées par dépendance ; mention `affinityApplied`
  dans « Pourquoi à cette place ? » (`ui/day/Why.kt`). Préférence manuelle par
  type (« vers 12 h ») qui prime.
- **Anti-boucle** : le planning suggère un créneau, l'utilisateur l'honore, la
  mesure le confirme (auto-renforcement). Parades : poids borné, ajustement
  doux, exclusion des tâches en retard ; en v2, mesure d'**efficacité**
  (estimé ÷ réel par tranche) plutôt que la seule fréquence. La v1 reste la
  fréquence relative au global.

### Feuille de route

- **3.4.0** : lot A + B1 (avec les chantiers `retrait-kairos2.md`,
  `durees-apprises.md`, `visite-guidee.md` et `premiere-tache.md`).
- **3.5.0** : B2 + C1.
- **3.6.0** : C2. Échelles libres réévaluées après retours.

### Décisions et pièges tracés

- **Contexte = type de tâche existant** (écarté : un champ « contexte »
  dédié, qui exigerait migration, export et UI de qualification). Risque : un
  type est un libellé traduit, un renommage casse la liaison ; elle est
  signalée, jamais silencieuse. Un champ dédié reste possible plus tard.
- **Capacité d'équipe inchangée** (écarté : la faire suivre les plages) : elle
  reste sur les champs historiques ; à reconsidérer si les horaires par jour
  deviennent courants chez les managers.
- **Détection avant préférence** (écarté : seulement une préférence manuelle) :
  la donnée est déjà là grâce au chrono sur n'importe quelle tâche ; la
  préférence manuelle reste le recours.
- **Observer avant d'appliquer** : C1 prouve que la mesure est lisible avant
  qu'elle n'influence quoi que ce soit.
- **Échelles libres écartées** (voir Hors périmètre).

### Impacts sur les specs existantes (à reporter à l'implémentation)

`ordonnancement.md` (fenêtres, filtre de contexte, boost), `vue-jour.md`
(déplacer, blocage, bugs, frise), `vue-semaine.md` (tâches programmées),
`reglages.md` (groupe avancé, restaurer, carte « Horaires »),
`modele-donnees.md` (`Settings.schedule`), `statistiques.md` (carte meilleures
heures), `equipe-charge.md` (capacité non modifiée, à dire), `i18n.md` (dates
souples en français).
