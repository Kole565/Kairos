# Accompagnement de la première tâche

_Rôle : guider pas à pas la création d'une vraie première tâche, en expliquant
chaque champ, la priorité, les points (Fibonacci), l'estimation et le placement,
puis féliciter. Fichiers couverts (à venir) : `kmp/ui/.../ui/day/EditTaskDialog.kt`,
`ui/day/PointsGuide.kt`, `ui/day/Why.kt`, `ui/day/TaskRow.kt`, nouveau
`ui/guide/FirstTaskCoach.kt`, `ui/app/Welcome.kt`, `ui/screens/HomeScreen.kt`.
Tests : `FirstTaskCoachUiTest`, `M6GuideUiTest`, `M5ScreensUiTest`._

État : **issue #64 (retour du propriétaire)**, **spécifiée le 2026-10-13, non
implémentée**. Livraison visée : 3.4.0. Complète `accueil.md` (accueil du premier
lancement) et s'appuie sur le catalogue de `visite-guidee.md`.

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
  (`FirstTaskCoachUiTest`).
- **Couverture** : chaque champ de la fiche a une étape (la liste de champs du
  coach est comparée à celle de la fiche : `FirstTaskCoachUiTest.everyFieldHasAStep`).
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

`FirstTaskCoach` n'est pas un formulaire : il enveloppe `EditTaskDialog` en mode
création. L'état du coach (étape courante) est `rememberSaveable`. Chaque étape
est une `FeatureCard` du catalogue (`visite-guidee.md`), liée à une **clé de
champ** de la fiche ; la fiche expose une liste ordonnée `EditField` pour que le
test de couverture compare les deux. Le coach fait défiler la fiche jusqu'au
champ et le met en évidence par un contour primaire (jamais la couleur seule).
Le panneau est une carte MD3 sans ombre, non bloquante : le focus et le clavier
restent sur la fiche.

### Placement et félicitations

L'enregistrement passe par `KairosRepository.createTask`, qui retourne l'`id` ;
c'est **cet événement réel** qui déclenche l'étape « placement » (défilement et
mise en évidence de la ligne dans `DayScreen`, texte tiré de `Why.kt`) puis le
dialogue de félicitations. Le dialogue ne dépend pas du dernier clic du coach.

### Déclenchement

- Bouton dans `WelcomeDialog` (à côté de `welcome_tour`).
- Carte d'invitation dans `DayScreen` / `HomeScreen` tant qu'aucune tâche n'est
  hors exemples. L'état est **dérivé des données**, sans persistance ;
  l'identification des exemples passe par `ExampleData` (à vérifier à
  l'implémentation : marqueur « [Exemple] » traduit ou projet « Exemple »).
- Entrée du hub pour rejouer : même fiche, l'enregistrement reste un geste
  volontaire.

### Décisions et pièges tracés

- **Coach sur la vraie fiche** (écarté : une fiche factice de démonstration, qui
  ne crée rien et diverge de la vraie) : l'utilisateur ressort avec une tâche à lui.
- **Félicitations sur la création réelle** (écarté : au dernier pas du coach) :
  on ne félicite pas d'une tâche qui n'existe pas.
- **Pas de persistance** : l'invitation disparaît d'elle-même dès la première
  tâche ; pas de réglage de plus.
- **Textes partagés** avec le catalogue (une seule source, voir
  `visite-guidee.md`).
- **Aucune animation décorative** : sobriété du design system.

### Impacts sur les specs existantes (à reporter à l'implémentation)

`accueil.md` (bouton « Créer ma première tâche », carte d'invitation),
`vue-jour.md` (mise en évidence d'une ligne, `EditField`), `accessibilite.md`
(annonce d'étape), `i18n.md`, `visite-guidee.md` (chapitre « Premiers pas »).
