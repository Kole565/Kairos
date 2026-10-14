# Durées apprises des tâches récurrentes

_Rôle : une tâche sans estimation explicite récupère une durée à partir de ses
réalisations passées, pour mieux remplir la journée. Fichiers couverts (à
venir) : nouveau `kmp/core/.../engine/DurationEstimator.kt`,
`core/engine/Scheduling.kt` (`durationMinutes`, `buildDaySchedule`),
`core/engine/Recurrence.kt` (`nextOccurrence`), `core/model/Task.kt`
(`seriesId`), `core/model/Settings.kt` (`learning`), `core/alerts/ChronoAlerts.kt`,
`core/team/TeamWork.kt`, `core/team/Effort.kt`, `data/` (migration),
`ui/day/TaskRow.kt`, `ui/day/Why.kt`, `ui/day/EditTaskDialog.kt`. Tests : listés
dans les critères._

État : **issue #64 (suggestion 3, prolongement)**, **spécifiée le 2026-10-13,
non implémentée**. Livraison visée : 3.4.0.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une tâche qui revient (« relever le courrier », « point hebdomadaire »,
« rapport du lundi ») prend à peu près le même temps à chaque fois, et Kairos
le sait : le chrono et le temps manuel enregistrent le temps passé. Pourtant
l'estimation reste à saisir à la main, ou retombe sur la durée par défaut, ce qui
fausse le remplissage de la journée. L'idée : **apprendre des réalisations
passées** pour affiner les estimations et le calendrier des tâches qui se
ressemblent.

À long terme (une future version **majeure**), le propriétaire veut un **modèle
de machine learning** pour prédire les temps. Cette spec prépare cette évolution
sans la réaliser.

### Comportement attendu (utilisateur)

1. **Pour une tâche sans estimation explicite**, Kairos utilise une durée
   apprise quand il en connaît une **fiable** : celle de la série (« les 5
   dernières fois, médiane 45 min »). Sans durée fiable, rien ne change (durée
   par défaut des réglages).
2. **Une estimation explicite l'emporte toujours** (minutes saisies, ou points
   calibrés quand l'utilisateur les a choisis). La durée apprise n'est jamais
   écrite dans la tâche : elle est calculée, donc toujours cohérente avec
   l'historique.
3. **Repli** : à défaut de série fiable, Kairos descend : même titre ; même
   type (calibrage par type, qui existe) ; mêmes points (calibrage par points,
   qui existe) ; durée par défaut.
4. **Provenance toujours visible** : un badge `~45 min` avec icône sur la ligne
   de la tâche, une explication (« d'après tes 5 dernières occurrences,
   médiane ») et, sous le seuil, « peu fiable (n = 2) » sans l'appliquer. Une
   ligne dans « Pourquoi à cette place ? ». Jamais la couleur seule.
5. **Suggestion à l'édition** : dans la fiche, une puce « Utiliser 45 min »
   remplit le champ de minutes **sur action** de l'utilisateur seulement
   (règle déjà en vigueur : on ne remplit qu'un champ vide).
6. **Portée** (choix du propriétaire) : la durée apprise sert au **placement**
   dans la journée, à l'**alerte de dépassement** du chrono et aux
   **projections d'équipe** (charge « moi », occurrences projetées).
7. **Par défaut, actif** pour toute tâche sans estimation explicite
   (choix du propriétaire). Un réglage permet de le couper, de fixer la taille
   de l'historique et le seuil minimal.
8. **Isolation** : les tâches reçues d'une équipe n'entrent jamais dans une
   série personnelle ; une tâche d'un espace n'apprend que de son espace.

### Critères de succès

- Sans historique chronométré, rien ne change : `DifferentialTest` (480
  scénarios, aucun historique) passe inchangé, et un test dédié rejoue les 480
  avec un estimateur qui répond, puis sans (`LearnedDefaultsTest`).
- Une durée explicite n'est jamais écrasée (`DurationEstimatorTest`).
- Limites : n = 2 non appliquée / n = 3 appliquée ; écrêtage des aberrants
  (> 3 × la médiane) ; arrondi à 5 min au demi-supérieur ; bornes 5 min à la
  fenêtre de travail ; séances à 0 minute ignorées (`DurationEstimatorTest`).
- Invariance à l'ordre : mélanger l'historique donne le même résultat
  (`DurationEstimatorTest`).
- Isolation d'espace : une tâche reçue n'alimente pas une série personnelle
  (`TeamIsolationTest`).
- Série renommée : repli sur titre / type / points (`DurationEstimatorTest`).
- Export : le fichier d'or est inchangé et `seriesId` est absent du JSON solo
  tant qu'il est nul (`TeamIsolationTest.soloExportIsByteIdentical`) ;
  migration et marqueur (`ExchangeMigrationTest`).
- Alerte de dépassement et projection d'équipe lisent la même fonction
  (`ChronoAlertsTest`, `TeamWorkTest`).

### Hors périmètre / différé

- **Le modèle d'apprentissage automatique** (version majeure à venir) : voir
  « Évolution ».
- Apprentissage entre appareils ou entre membres d'une équipe.
- Modifier rétroactivement l'estimation des tâches terminées.

## 2. Solution technique

### Port pur dans `core`

```kotlin
interface DurationEstimator {
    fun estimate(target: DurationTarget, history: List<DurationSample>): DurationEstimate?
}
data class DurationTarget(val title: String, val type: String, val points: Int?, val seriesId: Long?, val space: TaskSpace)
data class DurationSample(val taskId: Long, val seriesId: Long?, val title: String, val type: String, val points: Int?,
                          val space: TaskSpace, val spentMinutes: Int)   // ordonnés par id, jamais par horloge
data class DurationEstimate(val minutes: Int, val source: Source, val samples: Int, val low: Int? = null, val high: Int? = null)
enum class Source { SERIES, TITLE, TYPE, POINTS, MODEL }
```

`core` reste pur (`architecture.md` : ni horloge, ni I/O) : l'heure et le fuseau
sont des paramètres. Les échantillons sont les tâches **terminées** dont le
temps passé est > 0, calculé par `TimeTracking.spentMinutesByTask` (temps
manuel compris).

### Choix de calcul

- **Hiérarchie de repli** (la première fiable gagne) : série (`seriesId`) →
  même titre et même espace → même type (`TaskStats.calibrationByType`) →
  mêmes points (`fibonacciCalibration`) → `defaultTaskDurationMinutes`. Les
  niveaux 3 et 4 existent déjà.
- **Statistique** : médiane des N derniers échantillons par `id` décroissant
  (N = 5 par défaut, réglable ; **jamais `updatedAt`**, modifiable après coup),
  `TaskStats.median` réutilisée (déjà figée par `StatsDifferentialTest`).
  Seuil `MIN_SAMPLE` = 3 : en dessous, valeur exposée « peu fiable » et non
  appliquée.
- **Aberrants** : échantillons > 3 × la médiane de la série écartés (chrono
  oublié) ; échantillons de 1 minute ou moins ignorés (démarrage/arrêt
  accidentel).
- **Arrondi** : à 5 minutes au demi-supérieur en arithmétique entière (**pas**
  `pyRound`, demi-pair) ; minimum 5 min ; maximum la fenêtre de travail et la
  limite de saisie à 4 chiffres.

### Point d'injection unique

`Scheduling.durationMinutes` est déjà l'unique point de calcul de la durée d'une
tâche. `buildDaySchedule` reçoit une table `learned: Map<Long, DurationEstimate>`
optionnelle (construite par `DayView` à partir du dépôt) ; `durationMinutes`
la consulte **après** les minutes explicites et les points, avant la durée par
défaut ; l'énumération imbriquée `Scheduling.EffortSource` (`POINTS`, `MINUTES`,
`DEFAULT`) gagne `LEARNED`. Table absente ou vide = code actuel, octet pour octet. Les autres
consommateurs lisent la **même fonction** : `ChronoAlerts` (alerte OVER),
`TeamWork.project` et la charge « moi » (`core/team/Effort.kt`,
dont l'énumération `EffortSource` gagne `LEARNED`, avec une branche dans `TeamTaskSheet`).

### Série stable

- `Task.seriesId: Long?`, nul par défaut ; `Recurrence.nextOccurrence`
  transmet le `seriesId` de la mère ou, si elle n'en a pas, son propre `id`.
- **Pas de rétro-remplissage** : une migration `.sqm` ajoute la colonne nulle,
  sans réécrire les lignes ; les tâches existantes retombent sur
  (titre, espace) jusqu'à leur prochaine occurrence. Ainsi l'export et le
  fichier d'or ne changent pas (`explicitNulls = false`).
- Le marqueur de la nouvelle migration s'ajoute à `KairosStore.MARKERS`
  (`ExchangeMigrationTest.everyMigrationHasAMarker`) : la version est lue du
  schéma réel, pas du `PRAGMA user_version` (voir `demarrage.md`).
- Les tâches reçues (`origin != null`) ont un `seriesId` nul et sont exclues
  des échantillons personnels.

### Réglages

`Settings.learning: LearningSettings? = null`. Nul = comportement par défaut
(**actif** pour les tâches sans estimation). Champs : `enabled` (vrai),
`history` (5, ≥ 3), `minSamples` (3, ≥ 2). `SettingsForm` valide et ne crée
l'objet qu'en cas d'écart ; `team` n'est jamais touché.

### Interface

Badge `~45 min` avec icône à côté du badge d'estimation explicite
(`ui/day/TaskRow.kt`) ; ligne de provenance dans `ui/day/Why.kt` ; « peu
fiable (n = 2) » en icône + texte (convention de `statistiques.md`) ; puce de
suggestion dans `ui/day/EditTaskDialog.kt` (règle « remplir seulement si vide ») ;
interrupteur et bornes dans Réglages. Textes FR/EN.

### Évolution : modèle d'apprentissage automatique (version majeure)

Un `ModelEstimator(params)` se branche derrière le **même port** et renvoie
`Source.MODEL` avec un intervalle `low`/`high` qui alimente le modèle d'effort
de la simulation (`core/team/forecast/EffortModel.kt`). Contraintes déjà
connues :

- Kotlin pur exécutable sur wasm, JVM et Android ; aucune dépendance native ni
  non libre (F-Droid, `architecture.md`).
- Les poids du modèle sont des **données** passées en paramètre (JSON) ;
  l'entraînement est hors ligne ou une fonction pure à graine fixe
  (reproductibilité, skill `kairos-monte-carlo`).
- Caractéristiques : ce que porte déjà `DurationSample` (jetons du titre,
  type, points, priorité, récurrence, jour de semaine).
- Le planificateur, le badge et les specs ne changent pas : seuls `Source` et
  `samples` traversent la frontière.

### Décisions et pièges tracés

- **Dérivée, jamais écrite** (écarté : remplir `estimatedMinutes` à la
  complétion) : l'historique reste corrigeable, rien à défaire.
- **Actif par défaut** (choix du propriétaire ; écartés : suggestion seule,
  désactivé). **Tension assumée** avec « rien ne change sans réglage » : un
  planning existant change pour qui a des récurrentes sans estimation et du
  temps chronométré. Atténuations : jamais sur une durée explicite ; au moins 3
  occurrences chronométrées ; provenance visible partout ; annoncé par la visite
  (« Nouveau dans cette version ») ; interrupteur dans Réglages.
- **Identifiant de série stable** (choix du propriétaire ; écarté : clé
  (titre, espace), qui casse au renommage). Coût : colonne + migration, sans
  rétro-remplissage.
- **Médiane et non moyenne** : robuste aux chronos oubliés ; la moyenne
  tronquée reste possible pour n ≥ 6.
- **Ordre par `id`** : l'`updatedAt` change à chaque édition.
- **Séances à zéro ignorées** : jamais comptées comme 0 minute.

### Impacts sur les specs existantes (à reporter à l'implémentation)

`ordonnancement.md` (durée apprise dans `durationMinutes`), `recurrence.md`
(`seriesId`), `vue-jour.md` (badge, puce, « Durée suggérée »),
`temps-reel-chrono.md` (alerte OVER), `equipe-charge.md` et `equipe-simulation.md`
(`EffortSource.LEARNED`), `modele-donnees.md` (colonne, `learning`),
`reglages.md`, `statistiques.md` (calibrage partagé), `export-import.md`
(`seriesId` absent tant que nul).
