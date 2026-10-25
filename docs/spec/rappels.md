# Rappels des tâches à heure fixe et de la tâche suivante

_Rôle : prévenir, hors d'un chrono en cours, qu'une tâche à heure fixe va commencer et, sur demande, que le plan passe à la
tâche suivante. Fichiers couverts : `kmp/core/.../core/reminders/Reminders.kt` (calcul pur), `core/model/ReminderSettings.kt`,
`core/model/Settings.kt` (`reminders`), `core/settings/SettingsForm.kt` (champs `reminders.*`),
`kmp/ui/.../reminders/` (`ReminderWatcher.kt`, `ReminderTexts.kt`), `ui/settings/` (section « Rappels » de la catégorie
« Chrono et statistiques »), `ui/navigation/AppShell.kt` (veille), et sur Android `androidApp/.../ReminderSync.kt`,
`ChronoReceiver.kt`, `KairosProcess.kt`. Tests : `core/.../reminders/RemindersTest.kt`, `SettingsFormTest`,
`desktopApp/.../ReminderUiTest.kt`._

État : 3.8.0 (issue #103), spécifiée et implémentée le 2026-10-24.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos ne notifie aujourd'hui que pendant un chrono (dépassement, oubli, pause, `temps-reel-chrono.md`). Une tâche épinglée
à 14 h, ou placée par le plan à 10 h 30, ne dit rien quand son heure arrive. Qui se sert de Kairos comme agenda du jour veut
un rappel discret avant une tâche à heure fixe et, s'il le souhaite, quand le plan passe à la tâche suivante.

### Comportement attendu (utilisateur)

- **Désactivés par défaut** : rien ne change pour qui ne les active pas. Une section « Rappels » dans Réglages → « Chrono
  et statistiques », sous « Alertes du chrono » :
  - « Rappeler les tâches à heure fixe » (interrupteur, désactivé) ;
  - « Combien de minutes avant » (10 par défaut, de 0 à 120 ; 0 = à l'heure même) ;
  - « Signaler aussi la tâche suivante du plan » (interrupteur, désactivé ; ne sert que si les rappels sont activés).
  L'aide de la section dit où et comment ils arrivent (« Une notification du système. Sur Android, même Kairos fermé, avec
  quelques minutes de retard possibles téléphone en veille ; sur le bureau et le web, tant que Kairos est ouvert. »).
- **Tâche à heure fixe** : une tâche à faire épinglée (« Heure fixe ») aujourd'hui, placée par le plan du jour (donc ni
  faite, ni bloquée, ni décalée ou programmée un autre jour), reçoit un rappel *N* minutes avant son début :
  titre = la tâche, texte « Commence à 14:00 (dans 10 min) » (« À 14:00 » quand *N* = 0).
- **Tâche suivante** (si activé) : chaque tâche non épinglée que le plan du jour place **après maintenant** reçoit, à son
  début prévu, « C'est l'heure : prévue jusqu'à 11:30 ». La tâche en cours (déjà commencée selon le plan) n'en a pas.
- Le rappel suit le plan : terminer, décaler, bloquer, déplacer ou désépingler une tâche annule ou déplace son rappel ;
  changer les réglages aussi. Un rappel dont l'heure est passée n'est jamais rattrapé.
- Où ils arrivent :
  - **Android** : notification du système (canal « Rappels »), même application fermée, sans permission d'alarme exacte
    (alarmes inexactes, quelques minutes de retard possibles en veille profonde), sans permission réseau. Les rappels du
    jour et du lendemain sont programmés à chaque changement ; au-delà, ils le sont à la prochaine ouverture de Kairos
    (ou au redémarrage du téléphone).
  - **Bureau et web** : tant que Kairos est ouvert, la notification du système (plateau, navigateur) si elle est
    autorisée ; dans tous les cas, dans l'application, le bandeau des alertes du chrono (« Appeler Marc · Commence à
    14h00 (dans 10 min) »), qui reste jusqu'à ce qu'on le ferme. Les messages existants
    « bloquées » / « indisponibles » des alertes du chrono valent aussi pour les rappels.
- Espace Perso seulement : les tâches d'équipe d'un collègue n'ont jamais de rappel (vue Perso).

### Critères de succès

- Désactivés ou absents des réglages : aucun rappel ; activés : la tâche épinglée à 14 h a un rappel à 13 h 50 ; avance 0
  = 14 h ; faite, bloquée, programmée demain ou à une heure déjà passée : aucun ; la tâche suivante non épinglée a son
  rappel à son début prévu si l'option est cochée, et la tâche en cours n'en a pas (`RemindersTest`).
- Réglages : bornes 0-120, `reminders` nul tant qu'on n'y touche pas (export inchangé), revient nul aux valeurs par défaut
  (`SettingsFormTest`).
- La veille de l'application affiche le rappel à l'heure dite (horloge simulée) et ne l'affiche qu'une fois
  (`ReminderUiTest.theFixedTimeTaskIsRemindedOnceAtItsTime`).
- APK sans nouvelle permission (le manifeste garde `POST_NOTIFICATIONS` et `RECEIVE_BOOT_COMPLETED` ; `READ_CALENDAR`,
  facultative, arrive en 3.10.0 pour les calendriers du téléphone, `connexions-externes.md`).

### Hors périmètre / différé

- Rappels de date (« échéance demain »), rappels d'une tâche d'équipe chez un collègue, rappels répétés ou reportables
  (« dans 5 min »), son propre aux rappels, alarmes exactes.
- Sur le bureau et le web, rappels Kairos fermé (il faudrait un service en arrière-plan).

## 2. Solution technique

### Modèle et réglages (`core`)

- `ReminderSettings(enabled = false, leadMinutes = 10, nextTask = false)` (`@Serializable`), `Settings.reminders:
  ReminderSettings?` nul par défaut : même règle que `learning` (`modele-donnees.md`), un réglage laissé aux défauts
  n'écrit rien et l'export d'une base qui ne s'en sert pas ne change pas.
- `SettingsForm` : `reminders.enabled` (booléen), `reminders.leadMinutes` (entier 0-120), `reminders.nextTask` (booléen),
  lus sur les défauts quand `reminders` est nul, écrits en le gardant nul s'il revient aux défauts.

### Calcul (`Reminders`, pur)

- `Reminders.plan(snapshot, jour, maintenant, fuseau) : List<Reminder>` : vide si `reminders` est nul ou désactivé ;
  sinon `DayView.build(snapshot, jour, maintenant, fuseau)` et ses `schedule.scheduled` :
  - épinglée (`pinned`) : `Reminder(FIXED, at = début − avance)` ;
  - non épinglée, si `nextTask` : `Reminder(NEXT, at = début)` ;
  - gardés seulement si `at > maintenant` (rien de rattrapé) ; triés par `at`. `Reminder` porte la tâche, son titre, son
    début et sa fin (`début + durée`), et une `key` stable (`nature-id-début`).
- Le plan du jour fait foi : `DayView` exclut déjà les tâches faites, bloquées, programmées un autre jour, et place les
  épinglées à leur heure.

### Veille de l'application (`ReminderWatcher`, `ui`)

- Dans la coquille, à côté de `ChronoWatcher` ; lit `personalSnapshot`. Recalcule le plan à chaque changement de base ou
  de minute, attend le prochain `at`, puis, pour chaque rappel franchi depuis le dernier passage (jamais un rappel déjà
  montré : clés retenues pour la session) : bandeau (`AlertBanners`, la liste `alerts` de la coquille) et, si le système ne s'en charge pas
  déjà (`ChronoNotifier.systemHandlesAlerts` faux), `ChronoNotifier.notify(titre, texte, "reminder-<clé>")`.
- `ReminderTexts` : textes hors composition (titre, « Commence à … (dans … min) », « À … », « C'est l'heure : prévue
  jusqu'à … », nom du canal Android).

### Android (`ReminderSync`)

- Démarré par `KairosProcess` comme `ChronoSync` ; suit `personalSnapshot` (réglages compris). À chaque changement :
  annule les alarmes de rappel (un réservoir fixe de `MAX_ALARMS` = 24 codes de requête), calcule les rappels du jour
  (`maintenant`) et du lendemain (début de journée), et pose une alarme `setAndAllowWhileIdle` par rappel, titre et texte
  rédigés dans ses extras (les 24 premiers). `ChronoReceiver` (`ACTION_REMINDER`) poste la notification sur le canal
  `reminders` (importance par défaut). `BootReceiver` : ouvrir la base suffit, comme pour le chrono.
- Aucune permission ajoutée.

### Décisions et pièges tracés

- **Désactivés par défaut, un sous-objet nul** (2026-10-24, issue #103) : rien ne change pour qui ne les active pas, et
  l'export reste identique ; même règle que les durées apprises.
- **Le plan du jour fait foi** : « faite, bloquée, décalée » se déduit de `DayView`, sans règle parallèle à tenir à jour ;
  un rappel « suit » une tâche déplacée parce qu'il est recalculé à chaque changement.
- **Alarmes inexactes** : même décision que le chrono (`temps-reel-chrono.md`) ; quelques minutes de retard valent mieux
  qu'une permission que les magasins (F-Droid compris) regardent de près.
- **Jour et lendemain seulement sur Android** : la tâche suivante dépend du plan, qui dépend de l'heure ; au-delà du
  lendemain il serait faux. Le prochain changement, l'ouverture ou le redémarrage reprogramment.
- **Un réservoir de 24 alarmes** : annuler « toutes les alarmes de rappel » demande de connaître leurs codes ; un
  réservoir fixe les annule sans rien mémoriser. Au-delà de 24 rappels dans deux jours, les suivants le seront au
  prochain changement.
- **Pas de rattrapage** : un rappel passé (téléphone éteint, application fermée sur le bureau) n'a plus de sens.
- **Bandeau dans l'application en plus de la notification** : la notification peut être bloquée ou indisponible ; sur
  Android, l'alarme notifie déjà, l'application n'ajoute que le bandeau. Le bandeau des alertes du chrono, persistant, plutôt
  qu'un message éphémère : un rappel manqué serait perdu.
- **Section du guide** : `GuideSection.SETTINGS_REMINDERS` (catégorie « Chrono et statistiques ») ; la carte `reminders`
  du catalogue y mène.
