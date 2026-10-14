# Récurrence (tâches et créneaux) et « Décaler »

_Rôle : les obligations qui se répètent et le report au jour ouvré suivant.
Fichiers couverts : `kmp/core/src/commonMain/.../core/engine/Recurrence.kt`
(calcul pur), son usage dans `data/KairosRepository.kt` (`toggleDone`,
`ensureCalendarOccurrences`, `snooze`, `updateTask`) et dans
`core/day/DayView.kt` (créneaux du jour). Tests :
`core/src/commonTest/.../engine/RecurrenceTest.kt`, partie calendaire de
`DifferentialTest`, `data/.../KairosRepositoryTest.kt`._

État : **jalon M2**. La partie calendaire est figée par les fichiers d'or de
`DifferentialTest` (`calendar.json`, source de vérité depuis
`retrait-kairos2.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Trois familles d'obligations répétées :

1. **Tâches recréées en se terminant** (« relever le courrier », « point
   hebdo ») : je la fais, la suivante apparaît.
2. **Tâches calées sur une date du mois** (« le 23 », note de frais) : une
   occurrence par mois, indépendamment du sort de la précédente.
3. **Créneaux qui se répètent** (déjeuner quotidien, deep work chaque
   mardi) : un créneau n'est jamais « fait ».

Et « Décaler » : repousser une tâche au prochain jour ouvré.

### Comportement attendu (utilisateur)

- Terminer une tâche quotidienne, jours ouvrés, hebdomadaire ou mensuelle
  crée la suivante, échéance avancée selon la règle, priorité, points, type,
  durée, projet, description et heure fixe (même heure, nouveau jour)
  repris ; sa date programmée est la nouvelle échéance (elle n'apparaît pas
  dans la journée avant). Une tâche « jours ouvrés » ne tombe jamais sur un
  week-end **ni sur un jour férié** des réglages (France, jours libres).
- Une hebdomadaire revient toujours sur son jour de semaine (celui de son
  échéance), même terminée en retard, sans dérive.
- Une récurrente terminée en retard repart d'aujourd'hui, pas de l'échéance
  manquée (pas de rattrapage en rafale).
- Fait, rouvert, refait : une seule occurrence suivante.
- Une série « le N du mois » voit son occurrence du mois apparaître dès
  l'affichage de la vue Jour, reculée au jour ouvré précédent si le jour
  tombe un week-end ou un férié (N borné à la fin du mois). Une occurrence
  du mois précédent restée ouverte n'est jamais touchée ; une tâche dont
  l'échéance tombe déjà ce mois-ci compte comme l'occurrence du mois.
  Une tâche enregistrée comme série sans échéance **est** l'occurrence du mois :
  `updateTask` lui pose l'échéance de ce mois (`calendarDeadline`) et la période,
  ou celles du mois suivant si le jour N est déjà passé (elle naîtrait sinon en retard ;
  une période postérieure ou égale au mois courant couvre ce mois, `calendarOccurrences`)
  — sinon l'affichage suivant en créait une seconde de même titre, l'originale
  restant « sans créneau ».
- « Décaler » (3.4) programme aussi la tâche **personnelle** pour ce jour (elle quitte « Aujourd'hui » jusque-là, `vue-jour.md` § Dates, déplacer, blocage). « Décaler » envoie l'échéance au jour ouvré qui suit (un vendredi → le
  lundi, ou le mardi si ce lundi est férié) ; une échéance passée repart
  d'aujourd'hui.
- Un créneau récurrent (quotidien, jours ouvrés, hebdomadaire) réapparaît à
  la même heure les jours concernés, jamais avant sa date d'origine ; le
  modifier ou le supprimer agit sur toutes ses occurrences (seul le modèle
  est stocké), ce que la confirmation de suppression dit.

### Critères de succès

- Les comportements décrits ci-dessus sont vérifiés par `RecurrenceTest` et
  `KairosRepositoryTest`.
- Échéances suivantes, jours ouvrés, décalage et créneaux récurrents
  identiques aux fichiers d'or sur le jeu calendaire
  (`DifferentialTest.calendar_matches_golden`).

### Hors périmètre / différé

- Créneaux mensuels, fériés dans la règle « jours ouvrés » des créneaux,
  modification d'une seule occurrence, rattrapage des mois manqués.

## 2. Solution technique

- `nextDeadline(règle, base, jourDeSemaine?, fériés = ∅)` : `DAILY` +1 jour ;
  `WEEKDAYS` jour suivant hors week-end et hors `fériés` ; `WEEKLY` prochain jour de semaine
  ancré (défaut : celui de la base), toujours strictement après ; `MONTHLY`
  +1 mois, jour borné à la fin du mois.
- `nextOccurrence(tâche, aujourd'hui, existantes, maintenant, fériés = ∅)` : `null` hors
  `COMPLETION_RULES` ou si une tâche à faire de même titre, règle et
  échéance existe ; sinon la copie (identifiant 0), base
  `max(échéance, aujourd'hui)`, ancre hebdomadaire = `recurrenceDayOfWeek`
  sinon jour de l'échéance, reportée sur la copie. La copie garde l'espace
  et l'assigné de la tâche (`equipe.md` : une tâche d'équipe récurrente ne
  fait jamais naître une tâche Perso) ; pour une tâche d'équipe, le dépôt
  lui donne un nouveau `teamUid`, un avancement vide et son événement
  `created` (`equipe-backlog-suivi.md`).
- `calendarOccurrences(tâches, aujourd'hui, fériés, maintenant)` : séries
  `(titre, jour du mois)` des tâches `MONTHLY_ON_DAY` (tous statuts) ; pour
  chaque série non couverte ce mois (période `AAAA-MM` ou échéance dans le
  mois), une occurrence héritée du membre le plus récent (plus grand id),
  espace et assigné compris, échéance `onOrBeforeBusinessDay`,
  `recurrencePeriod` posée. Clé de série : (titre, jour du mois,
  **espace**) — une série Perso et une série d'équipe de même titre sont
  distinctes ; l'assigné n'y entre pas, pour qu'une occurrence réaffectée
  reste dans sa série (`equipe.md` § Questions ouvertes).
- **Série stable** (3.4, `durees-apprises.md`) : `nextOccurrence` et `calendarOccurrences` posent sur la copie
  `seriesId = seriesId de la mère, sinon son id` (une tâche reçue d'un manager, `origin != null`, n'ouvre jamais de série). Les
  occurrences d'une même série partagent ainsi un identifiant qui survit aux renommages ; il alimente la durée apprise.
- `nextSnoozeDate(échéance, aujourd'hui, fériés)` : jour ouvré suivant
  l'échéance si elle est à venir, sinon aujourd'hui.
- `expandRecurringBlocks(modèles, début, fin)` : pour chaque jour de
  `[max(début, origine), fin]` où la règle s'applique, une copie à la même
  heure et de même durée, qui garde l'identifiant du modèle.
- Dépôt : `toggleDone` insère `nextOccurrence` dans la transaction qui
  termine la tâche ; `ensureCalendarOccurrences` recalcule dans sa
  transaction et n'écrit que s'il y a lieu ; `snooze` et les fériés viennent
  des réglages (`Workdays.holidaysFor`). `updateTask` pose l'ancre
  hebdomadaire (jour de l'échéance) et ne garde le jour du mois que pour
  « le … du mois », dans 1-31.
- La vue Jour appelle `ensureCalendarOccurrences` quand le jour ou les
  tâches changent.

### Décisions et pièges tracés

- **« Jours ouvrés » saute les fériés** (2026-10-12, demande explicite après le test
  exploratoire : l'occurrence suivant le mardi 10 nov. tombait le mercredi 11, férié).
  `nextDeadline` reçoit les
  fériés des réglages (`Workdays.holidaysFor`), passés par `nextOccurrence`, les deux
  chemins de complétion du dépôt (`toggleDone`, intégration d'un rapport) et la
  projection des séries d'équipe (`TeamWork.seriesDates`). Les fichiers d'or
  n'en passent pas (ensemble vide par défaut) et restent identiques.
- **Jour du mois limité à 1-31** : un 0 ferait échouer le calcul de la date ;
  le dialogue refuse
  l'enregistrement et le dépôt vide une valeur hors bornes. Choisir
  « le … du mois » avec un champ vide le pré-remplit du jour de l'échéance
  (sinon du jour affiché) : pas de champ en erreur avant toute saisie.
- **L'origine d'un créneau est la date de son début** : un créneau
  récurrent modifié avec une autre date change donc aussi son point de
  départ.
