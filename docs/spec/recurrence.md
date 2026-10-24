# Récurrence (tâches et créneaux) et « Décaler »

_Rôle : les obligations qui se répètent et le report au jour ouvré suivant.
Fichiers couverts : `kmp/core/src/commonMain/.../core/engine/Recurrence.kt`
(calcul pur), son usage dans `data/KairosRepository.kt` (`toggleDone`,
`ensureCalendarOccurrences`, `snooze`, `updateTask`) et dans
`core/day/DayView.kt` (créneaux du jour), `core/week/WeekView.kt`, `core/team/TeamWork.kt` (charge de « moi »). Tests :
`core/src/commonTest/.../engine/RecurrenceTest.kt`, partie calendaire de
`DifferentialTest`, `data/.../KairosRepositoryTest.kt`, `BlockExceptionMigrationTest`,
`desktopApp/.../BlockOccurrenceUiTest.kt`. Interface des créneaux : `ui/.../day/Blocks.kt`._

État : **jalon M2** ; fériés des créneaux « jours ouvrés » implémentés en 3.7.0 (issue #109) ; créneaux mensuels
(issue #108) et une seule occurrence d'un créneau (issue #110) implémentés en 3.8.0. La partie calendaire est figée par les fichiers d'or de
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
   mardi, réunion d'équipe le premier lundi du mois, rendez-vous le 15) :
   un créneau n'est jamais « fait ». Une occurrence peut faire exception
   (« pas de déjeuner ce mardi », « la revue à 16 h cette semaine
   seulement »).

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
- Un créneau récurrent (quotidien, jours ouvrés, hebdomadaire, mensuel) réapparaît à
  la même heure les jours concernés, jamais avant sa date d'origine ; le
  supprimer, ou le modifier par « Toutes les occurrences », agit sur toute la série (seul le modèle
  est stocké), ce que la confirmation de suppression dit.
- **Créneaux mensuels** (3.8.0, issue #108). Le menu « Récurrence » du créneau propose deux règles de plus, dont le
  libellé reprend la date saisie : « mensuelle (le 15) » (« le 1er » le premier du mois) et « mensuelle (3e mardi) »
  (« 1er », « 2e », « 3e », « 4e » ou « dernier » ; anglais « monthly (day 15) », « monthly (3rd Tuesday) »). Le badge
  de la liste dit « mensuel ».
  - « Le N du mois » : N est le jour de la date d'origine, borné à la fin du mois (le 31 tombe le 30 avril, le 28 ou le
    29 février) et, comme une tâche « le N du mois », reculé au jour ouvré précédent s'il tombe un week-end ou un férié
    des réglages. Le mois de l'origine a pour occurrence l'origine elle-même, même un week-end : le créneau reste là où
    l'utilisateur l'a posé. Reculer peut faire passer l'occurrence d'un mois dans le mois d'avant (le 1er août 2026,
    samedi → vendredi 31 juillet).
  - « Le Ne jour de semaine » : le rang de l'origine parmi les mêmes jours de semaine de son mois (le mardi 17 novembre
    2026 est le 3e) ; une origine dans les jours 29 à 31 (un 5e) devient « dernier ». Les fériés sont ignorés, comme en
    hebdomadaire : la réunion du premier lundi reste au premier lundi.
- **Une seule occurrence** (3.8.0, issue #110). Le dialogue d'un créneau récurrent s'ouvre sur un choix « Seulement
  celle-ci » / « Toutes les occurrences » (« Only this one » / « All occurrences »), « Seulement celle-ci » par défaut :
  - « Seulement celle-ci » : « Change le mardi 29 septembre 2026 seulement : les autres jours de la série ne bougent
    pas. », titre, début et fin de ce jour-là, puis « Sauter ce jour », « Annuler », « Enregistrer ». « Sauter ce jour »
    retire l'occurrence du jour ; le message « « Déjeuner » sauté le 29 sept. » propose « Annuler », qui remet le jour
    tel qu'il était. Une occurrence déjà sautée ou modifiée offre aussi « Rétablir » (l'occurrence de la série revient) ;
    sautée, le dialogue dit « Ce jour est sauté : le créneau n'y apparaît pas. » et n'a plus de champs.
  - « Toutes les occurrences » : le formulaire complet d'avant (jour, heures, deep work, récurrence, « Supprimer »).
  - La liste des créneaux du jour montre l'occurrence du jour (heures de l'exception comprises) ; une occurrence sautée
    y reste, barrée, avec le badge « sauté ce jour », une occurrence modifiée porte « modifié ce jour » (jamais la
    couleur seule). Son en-tête dit « Créneaux du jour (un créneau récurrent se modifie pour ce jour seulement ou pour
    toutes ses occurrences) : ».
  - L'exception vaut partout où le créneau compte : frise et planning de la vue Jour, vue Semaine, charge de « moi » de
    l'espace Équipe. Les autres jours de la série ne changent pas.
- Un créneau « jours ouvrés » saute, comme une tâche « jours ouvrés », les week-ends **et les fériés** des réglages
  (calendrier français s'il est activé, jours fériés supplémentaires) : le stand-up quotidien n'apparaît ni le 1er mai
  ni un jour de pont déclaré (3.7.0, issue #109). Ni dans la vue Jour, ni dans la vue Semaine, ni dans la charge de
  « moi » de l'espace Équipe. Quotidien et hebdomadaire ignorent les fériés, comme avant. L'aide de la carte
  « Jours fériés » des Réglages le dit (« … tâches et créneaux « jours ouvrés »… »).

### Critères de succès

- Les comportements décrits ci-dessus sont vérifiés par `RecurrenceTest` et
  `KairosRepositoryTest`.
- Échéances suivantes, jours ouvrés, décalage et créneaux récurrents
  identiques aux fichiers d'or sur le jeu calendaire
  (`DifferentialTest.calendar_matches_golden`).
- Un créneau « jours ouvrés » saute un férié tombé en semaine et un jour supplémentaire déclaré, garde les autres
  jours d'une semaine sans férié, et quotidien et hebdomadaire ne changent pas
  (`RecurrenceTest.weekday_slots_skip_holidays_like_weekday_tasks`,
  `weekday_slots_keep_every_workday_of_a_week_without_holiday`, `daily_and_weekly_slots_ignore_holidays`) ; la vue Jour d'un férié, la vue Semaine et la charge de « moi » l'appliquent
  (`DayViewTest.a_weekday_slot_is_not_on_a_public_holiday`, `WeekViewTest.a_weekday_slot_skips_the_holidays_of_the_week`,
  `TeamWorkTest.aWeekdayMeetingTakesNoCapacityOnAHoliday`).
- Créneaux mensuels : le 31 borné et reculé (février, avril, mai, année bissextile), férié du 11 novembre, recul dans le
  mois d'avant, 3e et dernier jour de semaine
  (`RecurrenceTest.monthly_day_slots_clamp_to_the_end_of_month_and_move_back_to_a_workday`,
  `monthly_day_slots_skip_holidays_like_monthly_tasks`, `a_monthly_day_slot_moved_back_can_land_in_the_previous_month`,
  `monthly_weekday_slots_keep_the_rank_of_their_origin`, `unknown_block_codes_read_as_one_off_and_the_monthly_codes_are_stable`) ;
  une fois dans sa semaine (`WeekViewTest.a_monthly_slot_appears_once_in_its_week`) ; codes et export
  (`KairosRepositoryTest.deletingASeriesDeletesItsExceptionsAndExportsRoundTripThem`).
- Une seule occurrence : sautée ou remplacée, sans toucher aux autres jours
  (`RecurrenceTest.an_exception_skips_or_changes_one_occurrence_only`), dans la vue Jour et sa liste
  (`DayViewTest.one_occurrence_skipped_or_changed_shows_in_the_day_and_stays_editable`), la vue Semaine
  (`WeekViewTest.a_skipped_or_changed_occurrence_is_reflected_in_the_week`) et la charge de « moi »
  (`TeamWorkTest.aSkippedMeetingFreesMyCapacityThatDayOnly`) ; dépôt (refus, une exception par jour, annulation, série
  modifiée ou rendue ponctuelle, `KairosRepositoryTest.oneOccurrenceIsSkippedChangedOrRestoredWithoutTouchingTheSeries`),
  suppression et aller-retour d'export, export inchangé sans exception
  (`KairosRepositoryTest.deletingASeriesDeletesItsExceptionsAndExportsRoundTripThem`), migration sans perte
  (`BlockExceptionMigrationTest`), dialogue (`BlockOccurrenceUiTest.oneOccurrenceIsSkippedUndoneChangedAndRestored`).

### Hors périmètre / différé

- Rattrapage des mois manqués ; créneau annuel ; déplacer une seule occurrence à un **autre jour** (seuls son titre et
  ses heures changent) ; modifier une occurrence depuis la vue Semaine (la vue Jour reste le seul chemin).

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
- `expandRecurringBlocks(modèles, début, fin, fériés = ∅, exceptions = ∅)` : pour chaque jour de
  `[max(début, origine), fin]` où `occursOn(modèle, jour, fériés)`, l'occurrence `occurrence(modèle, jour, exception)`
  (même heure et même durée, identifiant du modèle), sauf si l'exception du jour est « sautée ». `occurrence` remplace
  champ par champ le titre, le début et la fin par ceux de l'exception quand ils sont non nuls (fin absente : début +
  durée du modèle). `occursOn` : `DAILY` toujours ; `WEEKDAYS` les jours `Workdays.isWorkday(jour, fériés)` (le même
  prédicat que `nextDeadline`) ; `WEEKLY` le jour de semaine de l'origine ; `MONTHLY_DAY` si le jour est
  `monthlyDayOccurrence(origine, mois du jour)` **ou** celle du mois suivant (recul d'un 1er vers la fin du mois
  d'avant) ; `MONTHLY_WEEKDAY` le même jour de semaine que l'origine et `hasRank(jour, weekdayRank(origine))`.
  `monthlyDayOccurrence` : l'origine dans son propre mois, sinon `onOrBeforeBusinessDay(jour N borné, fériés)` ;
  `weekdayRank` : `(jour - 1) / 7 + 1`, 5 → `LAST_RANK` ; `hasRank(jour, LAST_RANK)` : `jour + 7 > jours du mois`.
  Les appelants passent les fériés des
  réglages (`Workdays.holidaysFor`) et `snapshot.blockExceptions` : `DayView.build` (créneaux du jour et créneaux modifiables), `WeekView.build`
  (calculés depuis le lundi : `holidaysFor` couvre l'année d'avant et les deux suivantes, une semaine à cheval sur
  le Nouvel An y est), `TeamWork.busyHours` (charge de « moi »). Les fichiers d'or n'en passent pas.
- Modèle (3.8.0) : `BlockRecurrence.MONTHLY_DAY` (code `monthly_day`) et `MONTHLY_WEEKDAY` (`monthly_weekday`), sans
  colonne nouvelle (tout vient de la date d'origine) ; `BlockException(id, blockId, date, skipped, title?, start?,
  end?, createdAt)`, au plus une par `(blockId, date)` (`KairosSnapshot.blockExceptions`, table
  `time_block_exception`, `modele-donnees.md`).
- Vue Jour : `DayView.editableBlocks` est une liste d'`EditableBlock(template, occurrence, exception)` : les ponctuels du
  jour (`occurrence = template`) et les modèles qui ont une occurrence ce jour (`occursOn`), exception appliquée, **même
  sautée** (`skipped`) ; triée par heure de l'occurrence. `dayBlocks` (frise, planning) n'a pas les sautées.
- Dépôt : `skipOccurrence(id, jour)` et `overrideOccurrence(id, jour, titre, début, fin)` écrivent l'exception
  (`INSERT OR REPLACE`, unicité `(block_id, date)`) et rendent `false` sans rien écrire pour un créneau inconnu, ponctuel
  ou sans occurrence ce jour (`hasOccurrence`, fériés des réglages), ou une fin qui ne suit pas le début ; le titre est
  nettoyé, vide ou égal au modèle → `NULL`. `restoreOccurrence` retire l'exception ; `putOccurrenceException(id, jour,
  exception?)` remet celle d'avant (annulation du message). `deleteBlock` supprime aussi les exceptions du créneau ;
  `updateBlock` les garde, sauf si le créneau devient ponctuel.
- Interface (`Blocks.kt`) : `recurrenceText(règle, jour)` (libellés mensuels tirés de `Dates.dayOfMonth`,
  `Dates.weekdayName`, `rankLabel`), `DayBlockList(List<EditableBlock>)`, `BlockDialog(bloc, jour, …,
  OccurrenceActions)` : `KairosSegmentedRow` « Seulement celle-ci » / « Toutes les occurrences » pour un récurrent,
  `OccurrenceFields` (titre, début, fin ; « Rétablir » si exception, « Sauter ce jour » si non sautée). `DayScreen`
  câble les actions ; le saut passe par `LocalUndoMessages` (`block_skipped_message`, `action_revert`).
- Dépôt : `toggleDone` insère `nextOccurrence` dans la transaction qui
  termine la tâche ; `ensureCalendarOccurrences` recalcule dans sa
  transaction et n'écrit que s'il y a lieu ; `snooze` et les fériés viennent
  des réglages (`Workdays.holidaysFor`). `updateTask` pose l'ancre
  hebdomadaire (jour de l'échéance) et ne garde le jour du mois que pour
  « le … du mois », dans 1-31.
- La vue Jour appelle `ensureCalendarOccurrences` quand le jour ou les
  tâches changent.

### Décisions et pièges tracés

- **Les créneaux « jours ouvrés » sautent aussi les fériés** (2026-10-22, issue #109, décision du propriétaire) :
  **par défaut**, sans réglage. Les deux règles portent le même libellé « jours ouvrés » et se comportaient
  différemment. Les créneaux existants changent (un stand-up disparaît le 1er mai) : c'est le comportement attendu.
  Écarté : un interrupteur désactivé par défaut (un sous-objet de réglages nul de plus pour garder une incohérence).
  Paramètre `fériés = ∅` par défaut, comme `nextDeadline` : les fichiers d'or (`calendar.json`) restent identiques.
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
  départ. Pour un créneau mensuel, elle donne aussi le jour N ou le rang : changer la date change la règle, ce que le
  libellé du menu montre aussitôt.
- **Mensuels sans colonne** (2026-10-24, issue #108) : jour du mois et rang se lisent sur l'origine ; écarté, une colonne
  « jour N » comme pour les tâches (une migration de plus pour une donnée redondante, et deux sources à garder d'accord).
- **Le mois de l'origine garde l'origine** : appliquer la règle au mois de l'origine faisait disparaître un créneau posé
  un samedi le jour même où on le crée. Les mois suivants suivent la règle des tâches « le N du mois ».
- **5e jour de semaine → « dernier »** : un 5e mardi n'existe que certains mois ; « dernier » est ce qu'on veut dire
  quand on pose une réunion le 30. Une origine 4e et dernière (le 28 d'un mois de 4 mardis) reste « 4e ». Écarté :
  sauter les mois sans 5e (des trous inattendus).
- **« Le Ne jour de semaine » ignore les fériés** : la réunion « premier lundi » tombe un lundi de Pâques ou non selon
  l'équipe ; on la saute alors avec « Seulement celle-ci ». « Le N du mois » recule, lui, comme les tâches de même règle
  (l'issue le demande).
- **Exceptions stockées, occurrences toujours calculées** (2026-10-24, issue #110) : une table d'exceptions attachée au
  modèle, plutôt que matérialiser les occurrences (une ligne par jour à tenir à jour à chaque modification de la série).
  Une exception est le jour **et** l'identifiant du modèle : un jour sans occurrence (règle changée depuis) la rend
  inerte, sans effet ni erreur ; elle redevient active si la règle y revient. Écarté : la purger à chaque modification
  de la série (une exception perdue pour un aller-retour de règle).
- **Pas de déplacement à un autre jour** : « Seulement celle-ci » change titre et heures du jour, pas la date ;
  déplacer une occurrence, c'est la sauter et poser un ponctuel. Ainsi une exception n'est jamais « ailleurs » que son
  jour, et la vue Semaine reste juste sans recherche.
- **« Seulement celle-ci » par défaut** pour un récurrent : c'est le geste le plus courant (l'issue part de lui), et le
  plus sûr (il ne touche qu'un jour).
- **Annuler un saut par le message, rétablir par la liste** : l'occurrence sautée reste listée (barrée, badge), sinon
  rien ne permettrait de la retrouver une fois le message parti.
