# Accompagnement de la première tâche

_Rôle : guider pas à pas la création d'une vraie première tâche, en expliquant
chaque champ, la priorité, les points (Fibonacci), l'estimation et le placement,
puis féliciter. Fichiers couverts : `kmp/ui/.../ui/guide/FirstTaskCoach.kt`,
`ui/day/EditField.kt`, `ui/day/EditTaskDialog.kt`, `ui/day/DayScreen.kt`,
`ui/day/FirstTaskCards.kt`, `ui/day/TaskRow.kt`, `ui/app/Welcome.kt`,
`ui/navigation/AppShell.kt`, `ui/navigation/NavState.kt`, `core/ExampleData.kt`
(`isExample`). Tests : `FirstTaskCoachUiTest`, `FirstTaskCoachTest`,
`ExampleDataTest`, `M6GuideUiTest`, `M5ScreensUiTest`._

État : **issue #64 (retour du propriétaire)**, spécifiée le 2026-10-13,
**implémentée en 3.4.0**. Complète `accueil.md` (accueil du premier lancement) et
s'appuie sur le catalogue de `visite-guidee.md`.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Au premier lancement, l'accueil présente Kairos et des **exemples**
(« [Exemple] »), et la visite décrit les écrans. Mais personne n'accompagne la
**création d'une vraie tâche**, alors que la fiche cumule titre, description,
priorité, points, minutes, échéance, type puis des options avancées (projet,
temps manuel, jour du mois, heure fixe, sous-tâches, bloqueurs, récurrence). Le
vocabulaire (priorité P0 à P3, points de Fibonacci, score, placement) est
déroutant sans explication.

### Comportement attendu (utilisateur)

1. **Entrée** : l'accueil du premier lancement propose « Créer ma première
   tâche » à côté de « Visite guidée ». Une carte d'invitation reste sur la vue
   Jour tant que l'utilisateur n'a créé aucune tâche autre que les exemples, et
   le hub « Découvrir Kairos » permet de rejouer l'accompagnement.
2. **Création guidée dans la vraie fiche** : l'utilisateur remplit la vraie
   fiche d'une nouvelle tâche. Un panneau d'explication, en bas de la fiche, met
   en évidence le champ expliqué et dit à quoi il sert, avec « Précédent »,
   « Suivant » et « Passer ». Rien n'est imposé, sauf le titre à
   l'enregistrement ; les champs restent modifiables à tout moment.
3. **Étapes** : (1) titre : un verbe et un résultat ; (2) description ; (3)
   priorité : P0 à P3, le rouge est réservé à P0 ; (4) points : taille relative et
   pourquoi la suite de Fibonacci, avec les repères du guide des points ; (5)
   estimation en minutes : durée suggérée et durée apprise ; (6) échéance et date
   programmée ; (7) type, et ce qu'il change pour les horaires ; (8) aperçu des
   options avancées (projet, récurrence, bloqueurs, heure fixe, sous-tâches) ;
   (9) enregistrer.
4. **Placement** : après l'enregistrement, la vue Jour s'ouvre sur la tâche,
   mise en évidence, et le coach explique sa **position** : le score, son rang, son
   créneau dans la frise, « Pourquoi à cette place ? », puis les éléments de la
   ligne (priorité, points, durée, badges, échéance, boutons Faire, Chrono,
   Décaler).
5. **Félicitations** : un dialogue chaleureux, une fois la tâche **réellement
   créée**, propose la suite (« Lancer le chrono », « Découvrir Kairos »). Pas
   d'animation décorative.
6. **Interruption** : fermer la fiche ou le coach n'écrit rien ; rouvrir
   recommence au titre. La rotation de l'écran conserve l'étape.

### Critères de succès

- Parcours complet : la tâche existe avec les valeurs saisies, les félicitations
  n'apparaissent qu'après création, rien n'est écrit si l'on abandonne
  (`FirstTaskCoachUiTest`, `givingUpWritesNothing…`).
- **Couverture** : chaque champ de la fiche a une étape (`FirstTaskCoachTest.everyFieldOfTheSheetHasExactlyOneStep` compare la liste du coach à `EditField`, et
  `FirstTaskCoachUiTest.theWholeWalkCreatesTheTaskThenCongratulatesAndExplainsItsPlace` vérifie, étape par étape, que
  la vraie fiche porte la balise de chaque champ expliqué).
- Le nouveau bouton de l'accueil ne casse pas l'accueil existant
  (`M5ScreensUiTest`, `M6GuideUiTest`).
- Accessibilité : cibles ≥ 48 dp, 360 dp sans débordement, clavier ouvert sans
  masquer le champ, annonce de l'étape pour TalkBack, aucun raccourci clavier
  annoncé sur Android ; textes FR/EN (`StringsParityTest`) ; captures
  `--self-test` et `--store-screenshots` regardées.

### Hors périmètre / différé

- La fiche de l'espace Équipe (chapitre ultérieur de `visite-guidee.md`).
- Une récurrente pas à pas, l'accompagnement des réglages.
- Mémoriser que l'accompagnement a été fait.

## 2. Solution technique

### Un coach par-dessus la vraie fiche

`EditTaskDialog` sert aussi à la création : une tâche brouillon d'identifiant 0 (`creating`), sans « Supprimer », dont
« Enregistrer » exige un titre non vide (titre de la fiche « Première tâche »). Le paramètre optionnel
`coach: CoachSession?` ajoute le panneau d'explication en bas de la fiche ; sans coach, la fiche est inchangée.

- `EditField` (`ui/day/EditField.kt`) : les quatorze champs de la fiche, dont `advanced` pour ceux des options avancées ;
  chacun porte la balise de test `edit-field-<NOM>` (`Modifier.coachField`).
- `FirstTaskCoach.STEPS` (`ui/guide/FirstTaskCoach.kt`) : neuf `CoachStep`, chacune avec les champs qu'elle explique :
  titre ; description ; priorité ; points ; minutes ; échéance et programmée ; type ; options avancées (projet, temps
  manuel, récurrence, heure fixe, sous-tâches, bloqueurs) ; enregistrer (met « Enregistrer » en évidence).
  Une étape qui explique un champ avancé ouvre les options avancées.
- Mise en évidence : contour primaire de 2 dp et défilement jusqu'au champ (`BringIntoViewRequester`) ; une marge constante
  est posée dès qu'un coach est là pour que la fiche ne saute pas d'étape en étape ; jamais la couleur seule : le panneau
  nomme l'étape.
- Panneau (`CoachPanel`) : `OutlinedCard` sans ombre, non bloquant (le focus et le clavier restent sur la fiche), région
  vivante polie (TalkBack annonce l'étape), « Étape n sur N », « Passer le guide » (la fiche reste utilisable, « Terminer »
  à la dernière étape), « Précédent » dès la deuxième, « Suivant ».
- État : `DayScreen` garde en `rememberSaveable` `creating`, `coachStep` (-1 : coach passé), `placedId`, `congrats`,
  `placedStep` ; l'étape survit donc à une rotation. Fermer la fiche n'écrit rien ; rouvrir recommence au titre, fiche vide.

### Création, placement et félicitations

« Enregistrer » appelle `KairosRepository.createTask(titre)` puis `updateTask(id, édition)` : c'est la création **réelle**
(l'`id` rendu) qui pose `placedId` et affiche `FirstTaskCongratsDialog`, jamais le dernier clic du coach (« Passer le
guide » puis enregistrer félicite aussi). Le dialogue propose « Lancer le chrono » (`startTimer` sur la tâche), « Me montrer
où elle est placée » et « Découvrir Kairos » (hub), ou « Fermer ».

« Me montrer où elle est placée » lance trois étapes sur la vue Jour (`FirstTaskCoach.placement(placed)`), dans un panneau
en bas d'écran : son rang (le score, « Pourquoi à cette place ? ») ; son créneau (la frise) ; la ligne (coche, chrono,
« Décaler », crayon, badges). Une tâche absente de l'agenda du jour (« À traiter » faute de priorité ou de points, ou « Plus
tard ») remplace la première étape par « Pas encore placée » qui dit pourquoi. Pendant ces étapes, `RowContext.highlight`
cerne la ligne de la tâche (2 dp primaire) et la liste défile jusqu'à elle (`ListBuilder` retient la place de chaque ligne,
`rowPositions`).

### Déclenchement

- `WelcomeDialog(onFirstTask)` : bouton « Créer ma première tâche » sous le texte des exemples (visite guidée et
  « Commencer » inchangés) ; il ferme l'accueil et lance l'accompagnement.
- `AppShell.startFirstTask` : revient à l'espace Perso, ouvre la vue Jour et pose `NavState.firstTaskRequest` ; `DayScreen`
  l'acquitte (`onFirstTaskHandled`) et ouvre la fiche. Mêmes chemins : `GuideTarget.FirstTask` (cartes du catalogue, hub
  « Découvrir Kairos » : on rejoue l'accompagnement ; l'enregistrement reste un geste volontaire).
- Carte d'invitation `FirstTaskInvite` en tête de la vue Jour tant que `snapshot.tasks.none { !ExampleData.isExample(it) }`
  (état **dérivé des données**, sans persistance), masquable pour la session (« Pas maintenant »). `ExampleData.isExample`
  reconnaît le préfixe de titre « [Exemple] » / « [Example] » dans les deux langues.

### Décisions et pièges tracés

- **Coach sur la vraie fiche** (écarté : une fiche factice de démonstration, qui
  ne crée rien et diverge de la vraie) : l'utilisateur ressort avec une tâche à lui.
- **Félicitations sur la création réelle** (écarté : au dernier pas du coach) :
  on ne félicite pas d'une tâche qui n'existe pas.
- **Ordre : félicitations d'abord, puis la place** (choix à l'implémentation) : le dialogue vient dès la création et propose
  d'expliquer la place ; il ne dépend d'aucune étape du coach.
- **Pas de persistance** : l'invitation disparaît d'elle-même dès la première
  tâche ; pas de réglage de plus.
- **Textes partagés** avec le catalogue : la carte « Créer ta première tâche » du chapitre « Premiers pas » lance ce même
  accompagnement.
- **Aucune animation décorative** : sobriété du design system.
- **Titres des étapes distincts des libellés de la fiche** (« Enregistrer la tâche », « Pour aller plus loin ») : un titre
  de panneau identique à un libellé de la fiche rendrait les tests et les lecteurs d'écran ambigus.
- **Empreinte sur les bases vides** : l'invitation s'affiche aussi sur une base sans aucune tâche ; les tests de la vue Jour
  qui y cherchent la carte « Recherche » défilent jusqu'à elle (liste paresseuse).
